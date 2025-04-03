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
import org.ossreviewtoolkit.plugins.packagemanagers.conan.Conan.Companion.SCOPE_NAME_TEST_DEPENDENCIES
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.alsoIfNull
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.ort.createOrtTempDir

/**
 * A Conan version handler for Conan 2. [conan] is a back-reference to the Conan package manager, to be able to run
 * commands or call common functions.
 */
internal class ConanV2Handler(private val conan: Conan) : ConanVersionHandler {
    override fun getConanHome(): File = Os.userHomeDirectory.resolve(".conan2")

    override fun getConanStoragePath(): File = getConanHome().resolve("p")

    override fun process(definitionFile: File, lockfileName: String?): HandlerResults {
        val workingDir = definitionFile.parentFile

        // Create a default build profile.
        if (!getConanHome().resolve("profiles/default").isFile) {
            conan.command.run(workingDir, "profile", "detect")
        }

        val jsonFile = createOrtTempDir().resolve("info.json")
        if (lockfileName != null) {
            conan.verifyLockfileBelongsToProject(workingDir, lockfileName)
            conan.command.run(
                workingDir,
                "graph",
                "info",
                "-f",
                "json",
                "-l",
                lockfileName,
                "--out-file",
                jsonFile.absolutePath,
                *DUMMY_COMPILER_SETTINGS,
                definitionFile.name
            ).requireSuccess()
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
            ).requireSuccess()
        }

        val pkgInfosV2 = parsePackageInfosV2(jsonFile).also { jsonFile.parentFile.safeDeleteRecursively() }

        val packageList = removeProjectPackage(pkgInfosV2, definitionFile.name)
        val packages = parsePackages(packageList)
        val projectInfo = findProjectPackageInfo(pkgInfosV2, definitionFile.name)
        val projectPackage = generateProjectPackage(projectInfo, definitionFile, workingDir)

        val dependenciesScope = Scope(
            name = SCOPE_NAME_DEPENDENCIES,
            dependencies = parseDependencyTree(pkgInfosV2, projectInfo.requires, workingDir)
        )
        val devDependenciesScope = Scope(
            name = SCOPE_NAME_DEV_DEPENDENCIES,
            dependencies = parseDependencyTree(pkgInfosV2, projectInfo.buildRequires, workingDir)
        )
        val testDependencies = parseDependencyTree(pkgInfosV2, projectInfo.testRequires, workingDir)
        val testDependenciesScope = testDependencies.takeUnless { it.isEmpty() }?.let {
            Scope(
                name = SCOPE_NAME_TEST_DEPENDENCIES,
                dependencies = parseDependencyTree(pkgInfosV2, projectInfo.testRequires, workingDir)
            )
        }

        return HandlerResults(packages, projectPackage, dependenciesScope, devDependenciesScope, testDependenciesScope)
    }

    override fun getConanDataFile(name: String, version: String, conanStorageDir: File, recipeFolder: String?): File? =
        recipeFolder?.let {
            File(it).resolve("conandata.yml")
        }.alsoIfNull {
            logger.error { "This function cannot be called on the first package info, i.e. the conanfile itself." }
        }

    override fun listRemotes(): List<Pair<String, String>> {
        val remoteList = runCatching {
            // List configured remotes in JSON format.
            conan.command.run("remote", "list", "-f", "json").requireSuccess()
        }.getOrElse {
            logger.warn { "Failed to list remotes." }
            return emptyList()
        }

        @Serializable
        data class Remote(
            val name: String,
            val url: String,
            val verifySsl: Boolean,
            val enabled: Boolean
        )

        val remotes = JSON.decodeFromString<List<Remote>>(remoteList.stdout)
        return remotes.filter { it.enabled }.map { it.name to it.url }
    }

    override fun runInspectCommand(workingDir: File, pkgName: String, jsonFile: File) {
        val path = if ("conanfile" in pkgName) {
            pkgName
        } else {
            // For Conan 2, "conan inspect" need the path of the reference. See https://github.com/conan-io/conan/issues/12532.
            conan.command.run(
                workingDir,
                "cache",
                "path",
                pkgName
            ).requireSuccess().stdout.trim()
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
     * Return the map of packages and their identifiers which are contained in [pkgInfos].
     */
    private fun parsePackages(pkgInfos: List<PackageInfoV2>): Map<String, Package> =
        // Package types are filtered because "conan graph info" return too many packages.
        pkgInfos.filter { it.packageType == PackageType.STATIC_LIBRARY }.associate { pkgInfo ->
            val pkg = parsePackage(pkgInfo)
            "${pkg.id.name}:${pkg.id.version}" to pkg
        }

    /**
     * Return the [Package] parsed from the given [pkgInfo].
     */
    private fun parsePackage(pkgInfo: PackageInfoV2): Package {
        val homepageUrl = pkgInfo.homepage.orEmpty()

        val id = parsePackageId(pkgInfo)
        val conanData = conan.readConanData(id, conan.conanStoragePath, pkgInfo.recipeFolder)

        return Package(
            id = id,
            authors = conan.parseAuthors(pkgInfo),
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
     * Return a [Package] containing project-level information from [pkgInfo] and [definitionFile] using the
     * `conan inspect` command if possible:
     * - conanfile.txt: `conan inspect conanfile.txt` is not supported.
     * - conanfile.py: `conan inspect conanfile.py` is supported and more useful project metadata is parsed.
     *
     * TODO: The format of `conan info` output for a conanfile.txt file may be such that we can get project metadata
     *       from the `requires` field. Need to investigate whether this is a sure thing before implementing.
     */
    private fun generateProjectPackage(pkgInfo: PackageInfoV2, definitionFile: File, workingDir: File): Package {
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
            authors = conan.parseAuthors(pkgInfo),
            declaredLicenses = pkgInfo.license.toSet(),
            description = inspectPyFile("description").orEmpty(),
            homepageUrl = pkgInfo.homepage.orEmpty(),
            binaryArtifact = RemoteArtifact.EMPTY, // TODO: implement me!
            sourceArtifact = RemoteArtifact.EMPTY, // TODO: implement me!
            vcs = conan.parseVcsInfo(pkgInfo)
        )
    }
}

/**
 * Return the full list of packages, excluding the project level information.
 */
private fun removeProjectPackage(pkgInfos: List<PackageInfoV2>, definitionFileName: String): List<PackageInfoV2> =
    pkgInfos - findProjectPackageInfo(pkgInfos, definitionFileName)

/**
 * Find the [PackageInfo] that represents the project defined in the definition file.
 */
private fun findProjectPackageInfo(pkgInfos: List<PackageInfoV2>, definitionFileName: String): PackageInfoV2 =
    pkgInfos.first {
        // Use "in" because conanfile.py's reference string often includes other data.
        definitionFileName in it.label
    }

/**
 * Return the [Identifier] for the package contained in [pkgInfo].
 */
private fun parsePackageId(pkgInfo: PackageInfoV2) =
    Identifier(
        type = "Conan",
        namespace = "",
        name = pkgInfo.name,
        version = pkgInfo.version
    )

/**
 * Return the dependency tree for the given [direct scope dependencies][requires].
 */
private fun parseDependencyTree(
    pkgInfos: List<PackageInfoV2>,
    requires: List<String>,
    workingDir: File
): Set<PackageReference> =
    buildSet {
        requires.forEach { childRef ->
            pkgInfos.find { it.label == childRef }?.let { pkgInfo ->
                logger.debug { "Found child '$childRef'." }

                val id = parsePackageId(pkgInfo)
                val dependencies = parseDependencyTree(pkgInfos, pkgInfo.requires, workingDir)
                // Here the build dependencies of the direct dependencies are ignored. This is not the case for
                // Conan v1, but with Conan V2 this leads to additional packages in the dependency tree, such as
                // "pkgconf" and "libtool".
                add(PackageReference(id, dependencies = dependencies))
            }
        }
    }
