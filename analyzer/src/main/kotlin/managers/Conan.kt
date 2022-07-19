/*
 * Copyright (C) 2019 HERE Europe B.V.
 * Copyright (C) 2019 Verifa Oy.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.analyzer.managers

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue

import com.vdurmont.semver4j.Requirement

import java.io.File
import java.util.SortedSet

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.parseAuthorString
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.common.stashDirectories
import org.ossreviewtoolkit.utils.common.textValueOrEmpty
import org.ossreviewtoolkit.utils.common.toUri
import org.ossreviewtoolkit.utils.ort.createOrtTempDir
import org.ossreviewtoolkit.utils.ort.log
import org.ossreviewtoolkit.utils.ort.requestPasswordAuthentication

/**
 * The [Conan](https://conan.io/) package manager for C / C++.
 *
 * TODO: Add support for `python_requires`.
 */
@Suppress("TooManyFunctions")
class Conan(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig), CommandLineTool {
    companion object {
        private val DUMMY_COMPILER_SETTINGS = arrayOf(
            "-s", "compiler=gcc",
            "-s", "compiler.libcxx=libstdc++",
            "-s", "compiler.version=11.1"
        )

        private const val SCOPE_NAME_DEPENDENCIES = "requires"
        private const val SCOPE_NAME_DEV_DEPENDENCIES = "build_requires"
    }

    class Factory : AbstractPackageManagerFactory<Conan>("Conan") {
        override val globsForDefinitionFiles = listOf("conanfile.txt", "conanfile.py")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Conan(managerName, analysisRoot, analyzerConfig, repoConfig)
    }

    private val conanHome = Os.userHomeDirectory.resolve(".conan")

    // This is where Conan caches downloaded packages [1]. Note that the package cache is not concurrent, and its
    // layout does not support packages from different remotes that are named (and versioned) the same.
    //
    // TODO: Consider using the experimental (and by default disabled) download cache [2] to lift these limitations.
    //
    // [1]: https://docs.conan.io/en/latest/reference/config_files/conan.conf.html#storage
    // [2]: https://docs.conan.io/en/latest/configuration/download_cache.html#download-cache
    private val conanStoragePath = conanHome.resolve("data")

    private val pkgInspectResults = mutableMapOf<String, JsonNode>()

    override fun command(workingDir: File?) = "conan"

    // TODO: Add support for Conan lock files.

    // protected open fun hasLockFile(projectDir: File) = null

    override fun transformVersion(output: String) =
        // Conan could report version strings like:
        // Conan version 1.18.0
        output.removePrefix("Conan version ")

    override fun getVersionRequirement(): Requirement = Requirement.buildIvy("[1.18.0,)")

    override fun beforeResolution(definitionFiles: List<File>) = checkVersion()

    /**
     * Primary method for resolving dependencies from [definitionFile].
     */
    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> =
        try {
            resolvedDependenciesInternal(definitionFile)
        } finally {
            // Clear the inspection result cache, because we call "conan config install" for each definition file which
            // could overwrite the remotes and result in different metadata for packages with the same name and version.
            pkgInspectResults.clear()
        }

    private fun resolvedDependenciesInternal(definitionFile: File): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile

        // TODO: Support customizing the "conan_config" directory name, and also support getting the config from a URL.
        //       These options should be retrieved from package manager specific analyzer configuration in ".ort.yml".
        val conanConfig = sequenceOf(workingDir, analysisRoot).map { it.resolve("conan_config") }
            .find { it.isDirectory }

        val directoryToStash = conanConfig?.let { conanHome } ?: conanStoragePath

        stashDirectories(directoryToStash).use {
            conanConfig?.also { configureRemoteAuthentication(it) }

            val jsonFile = createOrtTempDir().resolve("info.json")
            run(workingDir, "info", ".", "--json", jsonFile.absolutePath, *DUMMY_COMPILER_SETTINGS)

            val pkgInfos = jsonMapper.readTree(jsonFile)
            jsonFile.parentFile.safeDeleteRecursively(force = true)

            val packageList = removeProjectPackage(pkgInfos, definitionFile.name)
            val packages = parsePackages(packageList, workingDir)

            val dependenciesScope = Scope(
                name = SCOPE_NAME_DEPENDENCIES,
                dependencies = parseDependencies(pkgInfos, definitionFile.name, SCOPE_NAME_DEPENDENCIES, workingDir)
            )
            val devDependenciesScope = Scope(
                name = SCOPE_NAME_DEV_DEPENDENCIES,
                dependencies = parseDependencies(pkgInfos, definitionFile.name, SCOPE_NAME_DEV_DEPENDENCIES, workingDir)
            )

            val projectPackage = parseProjectPackage(pkgInfos, definitionFile, workingDir)

            return listOf(
                ProjectAnalyzerResult(
                    project = Project(
                        id = projectPackage.id,
                        definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                        authors = projectPackage.authors,
                        declaredLicenses = projectPackage.declaredLicenses,
                        vcs = projectPackage.vcs,
                        vcsProcessed = processProjectVcs(
                            workingDir,
                            projectPackage.vcs,
                            projectPackage.homepageUrl
                        ),
                        homepageUrl = projectPackage.homepageUrl,
                        scopeDependencies = sortedSetOf(dependenciesScope, devDependenciesScope)
                    ),
                    packages = packages.values.toSortedSet()
                )
            )
        }
    }

    private fun configureRemoteAuthentication(conanConfig: File) {
        // Install configuration from a local directory.
        run("config", "install", conanConfig.absolutePath)

        // List configured remotes in "remotes.txt" format.
        val remoteList = run("remote", "list", "--raw")
        if (remoteList.isError) {
            log.warn { "Failed to list remotes." }
            return
        }

        // Iterate over configured remotes.
        remoteList.stdout.lines().forEach { line ->
            // Extract the remote URL.
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty() || trimmedLine.startsWith('#')) return@forEach

            val wordIterator = trimmedLine.splitToSequence(' ').iterator()

            if (!wordIterator.hasNext()) return@forEach
            val remoteName = wordIterator.next()

            if (!wordIterator.hasNext()) return@forEach
            val remoteUrl = wordIterator.next()

            remoteUrl.toUri().onSuccess { uri ->
                log.info { "Found remote '$remoteName' pointing to URL $remoteUrl." }

                // Request authentication for the extracted remote URL.
                val auth = requestPasswordAuthentication(uri)

                if (auth != null) {
                    // Configure Conan's authentication based on ORT's authentication for the remote.
                    val userAuth = run("user", "-r", remoteName, "-p", String(auth.password), auth.userName)
                    if (userAuth.isError) {
                        log.error { "Failed to configure user authentication for remote '$remoteName'." }
                    }
                }
            }.onFailure {
                log.warn { "The remote '$remoteName' points to invalid URL $remoteUrl." }
            }
        }
    }

    /**
     * Return the dependency tree for [pkg] for the given [scopeName].
     */
    private fun parseDependencyTree(
        pkgInfos: JsonNode,
        pkg: JsonNode,
        scopeName: String,
        workingDir: File
    ): SortedSet<PackageReference> {
        val result = mutableSetOf<PackageReference>()

        pkg[scopeName]?.forEach { childNode ->
            val childRef = childNode.textValueOrEmpty()
            pkgInfos.find { it["reference"].textValueOrEmpty() == childRef }?.let { pkgInfo ->
                log.debug { "Found child '$childRef'." }

                val id = parsePackageId(pkgInfo, workingDir)
                val dependencies = parseDependencyTree(pkgInfos, pkgInfo, SCOPE_NAME_DEPENDENCIES, workingDir) +
                        parseDependencyTree(pkgInfos, pkgInfo, SCOPE_NAME_DEV_DEPENDENCIES, workingDir)

                result += PackageReference(id, dependencies = dependencies.toSortedSet())
            }
        }

        return result.toSortedSet()
    }

    /**
     * Run through each package and parse the list of its dependencies (also transitive ones).
     */
    private fun parseDependencies(
        pkgInfos: JsonNode,
        definitionFileName: String,
        scopeName: String,
        workingDir: File
    ): SortedSet<PackageReference> =
        parseDependencyTree(pkgInfos, findProjectNode(pkgInfos, definitionFileName), scopeName, workingDir)

    /**
     * Return the map of packages and their identifiers which are contained in [nodes].
     */
    private fun parsePackages(nodes: List<JsonNode>, workingDir: File): Map<String, Package> =
        nodes.associate { node ->
            val pkg = parsePackage(node, workingDir)
            "${pkg.id.name}:${pkg.id.version}" to pkg
        }

    /**
     * Return the [Package] parsed from the given [node].
     */
    private fun parsePackage(node: JsonNode, workingDir: File): Package {
        val homepageUrl = node["homepage"].textValueOrEmpty()

        val id = parsePackageId(node, workingDir)
        val conanData = readConanData(id)

        return Package(
            id = id,
            authors = parseAuthors(node),
            declaredLicenses = parseDeclaredLicenses(node),
            description = parsePackageField(node, workingDir, "description"),
            homepageUrl = homepageUrl,
            binaryArtifact = RemoteArtifact.EMPTY, // TODO: implement me!
            sourceArtifact = parseSourceArtifact(conanData),
            vcs = processPackageVcs(VcsInfo.EMPTY, homepageUrl),
            isModified = "patches" in conanData
        )
    }

    /**
     * Return the value `conan inspect` reports for the given [field].
     */
    private fun inspectField(pkgName: String, workingDir: File, field: String): String =
        pkgInspectResults.getOrPut(pkgName) {
            val jsonFile = createOrtTempDir().resolve("inspect.json")
            run(workingDir, "inspect", pkgName, "--json", jsonFile.absolutePath)
            jsonMapper.readTree(jsonFile).also { jsonFile.parentFile.safeDeleteRecursively(force = true) }
        }.get(field).textValueOrEmpty()

    /**
     * Find the node that represents the project defined in the definition file.
     */
    private fun findProjectNode(pkgInfos: JsonNode, definitionFileName: String): JsonNode =
        pkgInfos.first {
            // Use "in" because conanfile.py's reference string often includes other data.
            definitionFileName in it["reference"].textValueOrEmpty()
        }

    /**
     * Return the full list of packages, excluding the project level information.
     */
    private fun removeProjectPackage(pkgInfos: JsonNode, definitionFileName: String): List<JsonNode> =
        pkgInfos.minusElement(findProjectNode(pkgInfos, definitionFileName))

    /**
     * Return the set of declared licenses contained in [node].
     */
    private fun parseDeclaredLicenses(node: JsonNode): SortedSet<String> =
        sortedSetOf<String>().also { licenses ->
            node["license"]?.mapNotNullTo(licenses) { it.textValue() }
        }

    /**
     * Return the [Identifier] for the package contained in [node].
     */
    private fun parsePackageId(node: JsonNode, workingDir: File) =
        Identifier(
            type = "Conan",
            namespace = "",
            name = parsePackageField(node, workingDir, "name"),
            version = parsePackageField(node, workingDir, "version")
        )

    /**
     * Return the [VcsInfo] contained in [node].
     */
    private fun parseVcsInfo(node: JsonNode) =
        VcsInfo(
            type = VcsType.GIT,
            url = node["url"].textValueOrEmpty(),
            revision = node["revision"].textValueOrEmpty().takeUnless { it == "0" }.orEmpty()
        )

    /**
     * Return the value of [field] from the output of `conan inspect --raw` for the package in [node].
     */
    private fun parsePackageField(node: JsonNode, workingDir: File, field: String): String =
        inspectField(node["display_name"].textValue(), workingDir, field)

    /**
     * Return the generic map of Conan data for the [id].
     */
    private fun readConanData(id: Identifier): Map<String, JsonNode> {
        val conanDataFile = conanStoragePath.resolve("${id.name}/${id.version}/_/_/export/conandata.yml")

        return runCatching {
            val conanData = yamlMapper.readValue<Map<String, JsonNode>>(conanDataFile)

            // Replace metadata for all version with metadata for this specific version for convenient access.
            conanData.mapValues { (key, value) ->
                when (key) {
                    "patches", "sources" -> value[id.version]
                    else -> value
                }
            }
        }.getOrDefault(emptyMap())
    }

    /**
     * Return the source artifact contained in [conanData], or [RemoteArtifact.EMPTY] if no source artifact is
     * available.
     */
    private fun parseSourceArtifact(conanData: Map<String, JsonNode>): RemoteArtifact {
        return runCatching {
            val artifactEntry = conanData.getValue("sources")

            val url = artifactEntry["url"].let { urlNode ->
                (urlNode.takeIf { it.isTextual } ?: urlNode.first()).textValueOrEmpty()
            }
            val hash = Hash.create(artifactEntry["sha256"].textValueOrEmpty())

            RemoteArtifact(url, hash)
        }.getOrElse {
            RemoteArtifact.EMPTY
        }
    }

    /**
     * Return a [Package] containing project-level information depending on which [definitionFile] was found:
     * - conanfile.txt: `conan inspect conanfile.txt` is not supported.
     * - conanfile.py: `conan inspect conanfile.py` is supported and more useful project metadata is parsed.
     *
     * TODO: The format of `conan info` output for a conanfile.txt file may be such that we can get project metadata
     *       from the `requires` field. Need to investigate whether this is a sure thing before implementing.
     */
    private fun parseProjectPackage(pkgInfos: JsonNode, definitionFile: File, workingDir: File): Package {
        val projectPackageJson = findProjectNode(pkgInfos, definitionFile.name)

        return if (definitionFile.name == "conanfile.py") {
            generateProjectPackageFromConanfilePy(projectPackageJson, definitionFile, workingDir)
        } else {
            generateProjectPackageFromConanfileTxt(projectPackageJson)
        }
    }

    /**
     * Return a [Package] containing project-level information parsed from [node] and [definitionFile] using the
     * `conan inspect` command.
     */
    private fun generateProjectPackageFromConanfilePy(node: JsonNode, definitionFile: File, workingDir: File): Package =
        Package(
            id = Identifier(
                type = managerName,
                namespace = "",
                name = inspectField(definitionFile.name, workingDir, "name"),
                version = inspectField(definitionFile.name, workingDir, "version")
            ),
            authors = parseAuthors(node),
            declaredLicenses = parseDeclaredLicenses(node),
            description = inspectField(definitionFile.name, workingDir, "description"),
            homepageUrl = node["homepage"].textValueOrEmpty(),
            binaryArtifact = RemoteArtifact.EMPTY, // TODO: implement me!
            sourceArtifact = RemoteArtifact.EMPTY, // TODO: implement me!
            vcs = parseVcsInfo(node)
        )

    /**
     * Return a [Package] containing project-level information parsed from [node].
     */
    private fun generateProjectPackageFromConanfileTxt(node: JsonNode): Package =
        Package(
            id = Identifier(
                type = managerName,
                namespace = "",
                name = node["reference"].textValueOrEmpty(),
                version = ""
            ),
            authors = parseAuthors(node),
            declaredLicenses = parseDeclaredLicenses(node),
            description = "",
            homepageUrl = node["homepage"].textValueOrEmpty(),
            binaryArtifact = RemoteArtifact.EMPTY, // TODO: implement me!
            sourceArtifact = RemoteArtifact.EMPTY, // TODO: implement me!
            vcs = parseVcsInfo(node)
        )

    /**
     * Parse information about the package author from the given JSON [node]. If present, return a set containing the
     * author name; otherwise, return an empty set.
     */
    private fun parseAuthors(node: JsonNode): SortedSet<String> =
        parseAuthorString(node["author"]?.textValue(), '<', '(')?.let { sortedSetOf(it) } ?: sortedSetOf()
}
