/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package org.ossreviewtoolkit.plugins.packagemanagers.conan

import java.io.File

import kotlinx.serialization.Serializable

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.PackageManager.Companion.processPackageVcs
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.plugins.packagemanagers.conan.Conan.Companion.DUMMY_COMPILER_SETTINGS
import org.ossreviewtoolkit.plugins.packagemanagers.conan.Conan.Companion.SCOPE_NAME_DEPENDENCIES
import org.ossreviewtoolkit.plugins.packagemanagers.conan.Conan.Companion.SCOPE_NAME_DEV_DEPENDENCIES
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively

/**
 * A Conan version handler Conan 2. [conan] is a back-reference to the Conan package manager, to be able to run commands
 * or call common functions.
 */
internal class ConanV2Handler(private val conan: Conan) : ConanVersionHandler {
    override fun getConanStoragePath(): File = conan.conanHome.resolve("data").resolve("p")

    override fun process(
        jsonFile: File,
        definitionFile: File,
        workingDir: File,
        lockFileName: String?
    ): HandlerResults {
        // Create a default build profile.
        if (!conan.conanHome.resolve("profiles/default").exists()) {
            conan.command.run(workingDir, "profile", "detect")
        }

        if (lockFileName != null) {
            conan.verifyLockfileBelongsToProject(workingDir, lockFileName)
            conan.command.run(
                workingDir,
                "graph",
                "info",
                "-f",
                "json",
                "-l",
                lockFileName,
                "--out-file",
                jsonFile.absolutePath,
                definitionFile.name
            )
        } else {
            conan.command.run(
                workingDir,
                "graph",
                "info",
                "-f",
                "json",
                "--out-file",
                jsonFile.absolutePath,
                *DUMMY_COMPILER_SETTINGS,
                definitionFile.name
            )
        }

        val pkgInfosV2 = parsePackageInfosV2(jsonFile).also { jsonFile.parentFile.safeDeleteRecursively() }

        val packageList = removeProjectPackageV2(pkgInfosV2, definitionFile.name)
        val packages = parsePackagesV2(packageList)
        val projectInfo = findProjectPackageInfoV2(pkgInfosV2, definitionFile.name)
        val projectPackage = generateProjectPackageV2(projectInfo, definitionFile, workingDir)

        val dependenciesScope = Scope(
            name = SCOPE_NAME_DEPENDENCIES,
            dependencies = parseDependencyTreeV2(pkgInfosV2, projectInfo.requires, workingDir)
        )
        val devDependenciesScope = Scope(
            name = SCOPE_NAME_DEV_DEPENDENCIES,
            dependencies = parseDependencyTreeV2(pkgInfosV2, projectInfo.buildRequires, workingDir)
        )

        return HandlerResults(packages, projectPackage, dependenciesScope, devDependenciesScope)
    }

    override fun getConanDataFile(name: String, version: String, conanStorageDir: File, recipeFolder: String?): File? =
        File(recipeFolder.orEmpty()).resolve("conandata.yml").takeIf {
            recipeFolder != null
        }

    override fun listRemotes(): ProcessCapture {
        // List configured remotes in JSON format.
        return conan.command.run("remote", "list", "-f", "json").requireSuccess()
    }

    override fun parseRemoteList(remoteList: String): List<Pair<String, String>> {
        @Serializable
        data class Remote(
            val name: String,
            val url: String,
            val verifySsl: Boolean,
            val enabled: Boolean
        )

        val remotes = JSON.decodeFromString<List<Remote>>(remoteList)
        return remotes.filter { it.enabled }.map { it.name to it.url }
    }

    override fun runInspectFieldCommand(workingDir: File, pkgName: String, jsonFile: File) {
        val path = if ("conanfile" in pkgName) {
            pkgName
        } else {
            // For Conan 2, "conan inspect" need the path of the reference. See https://github.com/conan-io/conan/issues/12532.
            val cacheProcess = conan.command.run(
                workingDir,
                "cache",
                "path",
                pkgName
            ).requireSuccess()
            cacheProcess.stdout.trim()
        }

        conan.command.run(
            workingDir,
            "inspect",
            path,
            "-f",
            "json",
            "--out-file",
            jsonFile.absolutePath
        ).requireSuccess()
    }

    /**
     * Return the full list of packages, excluding the project level information.
     */
    private fun removeProjectPackageV2(pkgInfos: List<PackageInfoV2>, definitionFileName: String): List<PackageInfoV2> =
        pkgInfos.minusElement(findProjectPackageInfoV2(pkgInfos, definitionFileName))

    /**
     * Find the [PackageInfo] that represents the project defined in the definition file.
     */
    private fun findProjectPackageInfoV2(pkgInfos: List<PackageInfoV2>, definitionFileName: String): PackageInfoV2 =
        pkgInfos.first {
            // Use "in" because conanfile.py's reference string often includes other data.
            definitionFileName in it.label
        }

    /**
     * Return the map of packages and their identifiers which are contained in [pkgInfos].
     */
    private fun parsePackagesV2(pkgInfos: List<PackageInfoV2>): Map<String, Package> =
        // Package types are filtered because "conan graph info" return too many packages.
        pkgInfos.filter { it.packageType == PackageType.STATIC_LIBRARY }.associate { pkgInfo ->
            val pkg = parsePackageV2(pkgInfo)
            "${pkg.id.name}:${pkg.id.version}" to pkg
        }

    /**
     * Return the [Package] parsed from the given [pkgInfo].
     */
    private fun parsePackageV2(pkgInfo: PackageInfoV2): Package {
        val homepageUrl = pkgInfo.homepage.orEmpty()

        val id = parsePackageIdV2(pkgInfo)
        val conanData = conan.readConanData(id.name, id.version, conan.conanStoragePath, pkgInfo.recipeFolder)

        return Package(
            id = id,
            authors = conan.parseAuthors(pkgInfo.info),
            declaredLicenses = pkgInfo.license.toSet(),
            description = pkgInfo.description.orEmpty(),
            homepageUrl = homepageUrl,
            binaryArtifact = RemoteArtifact.EMPTY, // TODO: implement me!
            sourceArtifact = conan.parseSourceArtifact(conanData),
            vcs = processPackageVcs(VcsInfo.EMPTY, homepageUrl),
            isModified = conanData.hasPatches
        )
    }

    /**
     * Return the [Identifier] for the package contained in [pkgInfo].
     */
    private fun parsePackageIdV2(pkgInfo: PackageInfoV2) =
        Identifier(
            type = "Conan",
            namespace = "",
            name = pkgInfo.name,
            version = pkgInfo.version
        )

    /**
     * Return a [Package] containing project-level information from [pkgInfo] and [definitionFile] using the
     * `conan inspect` command if possible:
     * - conanfile.txt: `conan inspect conanfile.txt` is not supported.
     * - conanfile.py: `conan inspect conanfile.py` is supported and more useful project metadata is parsed.
     *
     * TODO: The format of `conan info` output for a conanfile.txt file may be such that we can get project metadata
     *       from the `requires` field. Need to investigate whether this is a sure thing before implementing.
     */
    private fun generateProjectPackageV2(pkgInfo: PackageInfoV2, definitionFile: File, workingDir: File): Package {
        fun inspectPyFile(field: String) =
            definitionFile.name.takeIf { it == "conanfile.py" }?.let { conan.inspectField(it, workingDir, field) }

        return Package(
            id = Identifier(
                type = conan.projectType,
                namespace = "",
                // With text conan files, the "name" property is null, whereas the "displayName" property contains the
                // definition file name with extension. With python conan files, "name" contains the right project name.
                name = pkgInfo.name.ifEmpty { pkgInfo.label },
                version = pkgInfo.version
            ),
            authors = conan.parseAuthors(pkgInfo.info),
            declaredLicenses = pkgInfo.license.toSet(),
            description = inspectPyFile("description").orEmpty(),
            homepageUrl = pkgInfo.homepage.orEmpty(),
            binaryArtifact = RemoteArtifact.EMPTY, // TODO: implement me!
            sourceArtifact = RemoteArtifact.EMPTY, // TODO: implement me!
            vcs = conan.parseVcsInfo(pkgInfo.info)
        )
    }

    /**
     * Return the dependency tree for the given [direct scope dependencies][requires].
     */
    private fun parseDependencyTreeV2(
        pkgInfos: List<PackageInfoV2>,
        requires: List<String>,
        workingDir: File
    ): Set<PackageReference> =
        buildSet {
            requires.forEach { childRef ->
                pkgInfos.find { it.label == childRef }?.let { pkgInfo ->
                    logger.debug { "Found child '$childRef'." }

                    val id = parsePackageIdV2(pkgInfo)
                    val dependencies = parseDependencyTreeV2(pkgInfos, pkgInfo.requires, workingDir)
                    // Here the build dependencies of the direct dependencies are ignored. This is not the case for
                    // Conan v1, but with Conan V2 this leads to additional packages in the dependency tree, such as
                    // "pkgconf" and "libtool".
                    add(PackageReference(id, dependencies = dependencies))
                }
            }
        }
}
