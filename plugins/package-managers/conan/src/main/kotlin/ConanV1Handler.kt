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
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.ort.createOrtTempDir

/**
 * A Conan version handler for Conan 1. [conan] is a back-reference to the Conan package manager, to be able to run
 * commands or call common functions.
 */
internal class ConanV1Handler(private val conan: Conan) : ConanVersionHandler {
    override fun getConanHome(): File = Os.userHomeDirectory.resolve(".conan")

    override fun getConanStoragePath(): File = getConanHome().resolve("data")

    override fun process(definitionFile: File, lockfileName: String?): HandlerResults {
        val workingDir = definitionFile.parentFile
        val jsonFile = createOrtTempDir().resolve("info.json")
        if (lockfileName != null) {
            conan.verifyLockfileBelongsToProject(workingDir, lockfileName)
            conan.command.run(
                workingDir,
                "info", definitionFile.name,
                "-l", lockfileName,
                "--json", jsonFile.absolutePath
            ).requireSuccess()
        } else {
            conan.command.run(
                workingDir,
                "info",
                definitionFile.name,
                "--json",
                jsonFile.absolutePath,
                *DUMMY_COMPILER_SETTINGS
            ).requireSuccess()
        }

        val pkgInfos = parsePackageInfosV1(jsonFile).also { jsonFile.parentFile.safeDeleteRecursively() }

        val packageList = removeProjectPackage(pkgInfos, definitionFile.name)
        val packages = parsePackages(packageList, workingDir)
        val projectInfo = findProjectPackageInfo(pkgInfos, definitionFile.name)
        val projectPackage = generateProjectPackage(projectInfo, definitionFile, workingDir)

        val dependenciesScope = Scope(
            name = SCOPE_NAME_DEPENDENCIES,
            dependencies = parseDependencyTree(pkgInfos, projectInfo.requires, workingDir)
        )
        val devDependenciesScope = Scope(
            name = SCOPE_NAME_DEV_DEPENDENCIES,
            dependencies = parseDependencyTree(pkgInfos, projectInfo.buildRequires, workingDir)
        )

        return HandlerResults(packages, projectPackage, dependenciesScope, devDependenciesScope)
    }

    override fun getConanDataFile(name: String, version: String, conanStorageDir: File, recipeFolder: String?) =
        conanStorageDir.resolve("$name/$version/_/_/export/conandata.yml")

    override fun listRemotes(): List<Pair<String, String>> {
        val remoteList = runCatching {
            conan.command.run("remote", "list", "--raw").requireSuccess()
        }.getOrElse {
            logger.warn { "Failed to list remotes." }
            return emptyList()
        }

        return remoteList.stdout.lines().mapNotNull { line ->
            // Extract the remote URL.
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty() || trimmedLine.startsWith('#')) return@mapNotNull null

            val wordIterator = trimmedLine.splitToSequence(' ').iterator()

            if (!wordIterator.hasNext()) return@mapNotNull null
            val remoteName = wordIterator.next()

            if (!wordIterator.hasNext()) return@mapNotNull null
            val remoteUrl = wordIterator.next()

            remoteName to remoteUrl
        }
    }

    override fun runInspectCommand(workingDir: File, pkgName: String, jsonFile: File) {
        conan.command.run(workingDir, "inspect", pkgName, "--json", jsonFile.absolutePath).requireSuccess()
    }

    /**
     * Return the map of packages and their identifiers which are contained in [pkgInfos].
     */
    private fun parsePackages(pkgInfos: List<PackageInfoV1>, workingDir: File): Map<String, Package> =
        pkgInfos.associate { pkgInfo ->
            val pkg = parsePackage(pkgInfo, workingDir)
            "${pkg.id.name}:${pkg.id.version}" to pkg
        }

    /**
     * Return the [Package] parsed from the given [pkgInfo].
     */
    private fun parsePackage(pkgInfo: PackageInfoV1, workingDir: File): Package {
        val homepageUrl = pkgInfo.homepage.orEmpty()

        val id = parsePackageId(pkgInfo, workingDir)
        val conanData = conan.readConanData(id, conan.conanStoragePath)

        return Package(
            id = id,
            authors = conan.parseAuthors(pkgInfo),
            declaredLicenses = pkgInfo.license.toSet(),
            description = conan.inspectField(pkgInfo.displayName, workingDir, "description").orEmpty(),
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
    private fun parsePackageId(pkgInfo: PackageInfoV1, workingDir: File) =
        Identifier(
            type = "Conan",
            namespace = "",
            name = conan.inspectField(pkgInfo.displayName, workingDir, "name").orEmpty(),
            version = conan.inspectField(pkgInfo.displayName, workingDir, "version").orEmpty()
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
    private fun generateProjectPackage(pkgInfo: PackageInfoV1, definitionFile: File, workingDir: File): Package {
        fun inspectPyFile(field: String) =
            definitionFile.name.takeIf { it == "conanfile.py" }?.let { conan.inspectField(it, workingDir, field) }

        return Package(
            id = Identifier(
                type = conan.projectType,
                namespace = "",
                name = inspectPyFile("name") ?: pkgInfo.reference.orEmpty(),
                version = inspectPyFile("version").orEmpty()
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

    /**
     * Return the dependency tree for the given [direct scope dependencies][requires].
     */
    private fun parseDependencyTree(
        pkgInfos: List<PackageInfoV1>,
        requires: List<String>,
        workingDir: File
    ): Set<PackageReference> =
        buildSet {
            requires.forEach { childRef ->
                pkgInfos.find { it.reference.orEmpty() == childRef }?.let { pkgInfo ->
                    logger.debug { "Found child '$childRef'." }

                    val id = parsePackageId(pkgInfo, workingDir)
                    val dependencies = parseDependencyTree(pkgInfos, pkgInfo.requires, workingDir) +
                        parseDependencyTree(pkgInfos, pkgInfo.buildRequires, workingDir)

                    add(PackageReference(id, dependencies = dependencies))
                }
            }
        }
}

/**
 * Return the full list of packages, excluding the project level information.
 */
private fun removeProjectPackage(pkgInfos: List<PackageInfoV1>, definitionFileName: String): List<PackageInfoV1> =
    pkgInfos.minusElement(findProjectPackageInfo(pkgInfos, definitionFileName))

/**
 * Find the [PackageInfo] that represents the project defined in the definition file.
 */
private fun findProjectPackageInfo(pkgInfos: List<PackageInfoV1>, definitionFileName: String): PackageInfoV1 =
    pkgInfos.first {
        // Use "in" because conanfile.py's reference string often includes other data.
        definitionFileName in it.reference.orEmpty()
    }
