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

import com.vdurmont.semver4j.Requirement

import java.io.File
import java.net.Authenticator
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
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.core.CommandLineTool
import org.ossreviewtoolkit.utils.core.ProcessCapture
import org.ossreviewtoolkit.utils.core.createOrtTempFile
import org.ossreviewtoolkit.utils.core.log
import org.ossreviewtoolkit.utils.core.safeDeleteRecursively
import org.ossreviewtoolkit.utils.core.stashDirectories
import org.ossreviewtoolkit.utils.core.textValueOrEmpty
import org.ossreviewtoolkit.utils.core.toUri

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

    private val pkgInspectResults = mutableMapOf<String, JsonNode>()

    override fun command(workingDir: File?) = "conan"

    // TODO: Add support for Conan lock files.
    // protected open fun hasLockFile(projectDir: File) = null

    override fun transformVersion(output: String) =
        // Conan could report version strings like:
        // Conan version 1.18.0
        output.removePrefix("Conan version ")

    override fun getVersionRequirement(): Requirement = Requirement.buildIvy("[1.18.0,)")

    override fun beforeResolution(definitionFiles: List<File>) = checkVersion(analyzerConfig.ignoreToolVersions)

    /**
     * Primary method for resolving dependencies from [definitionFile].
     */
    override fun resolveDependencies(definitionFile: File): List<ProjectAnalyzerResult> =
        try {
            resolvedDependenciesInternal(definitionFile)
        } finally {
            // Clear the inspection result cache, because we call "conan config install" for each definition file which
            // could overwrite the remotes and result in different metadata for packages with the same name and version.
            pkgInspectResults.clear()
        }

    private fun resolvedDependenciesInternal(definitionFile: File): List<ProjectAnalyzerResult> {
        val conanHome = Os.userHomeDirectory.resolve(".conan")

        // This is where Conan caches downloaded packages [1]. Note that the package cache is not concurrent, and its
        // layout does not support packages from different remotes that are named (and versioned) the same.
        //
        // TODO: Consider using the experimental (and by default disabled) download cache [2] to lift these limitations.
        //
        // [1]: https://docs.conan.io/en/latest/reference/config_files/conan.conf.html#storage
        // [2]: https://docs.conan.io/en/latest/configuration/download_cache.html#download-cache
        val conanStoragePath = conanHome.resolve("data")

        val workingDir = definitionFile.parentFile
        val conanConfig = listOf(workingDir, analysisRoot).map { it.resolve("conan_config") }
            .find { it.isDirectory }
        val directoryToStash = conanConfig?.let { conanHome } ?: conanStoragePath

        stashDirectories(directoryToStash).use {
            conanConfig?.also {
                run("config", "install", it.absolutePath)

                val remoteList = run("remote", "list", "--raw")
                if (remoteList.isSuccess) {
                    remoteList.stdout.lines().forEach { line ->
                        val trimmedLine = line.trim()
                        if (trimmedLine.isEmpty() || trimmedLine.startsWith('#')) return@forEach

                        val wordIterator = trimmedLine.splitToSequence(' ').iterator()

                        if (!wordIterator.hasNext()) return@forEach
                        val remoteName = wordIterator.next()

                        if (!wordIterator.hasNext()) return@forEach
                        val remoteUrl = wordIterator.next()

                        remoteUrl.toUri().onSuccess { uri ->
                            log.info { "Found remote '$remoteName' pointing to URL $remoteUrl." }

                            val auth = Authenticator.requestPasswordAuthentication(
                                /* host = */ uri.host,
                                /* addr = */ null,
                                /* port = */ uri.port,
                                /* protocol = */ uri.scheme,
                                /* prompt = */ null,
                                /* scheme = */ null
                            )

                            if (auth != null) {
                                val userAuth = run("user", "-r", remoteName, "-p", String(auth.password), auth.userName)
                                if (userAuth.isError) {
                                    log.error { "Failed to configure user authentication for remote '$remoteName'." }
                                }
                            }
                        }.onFailure {
                            log.warn { "The remote '$remoteName' points to invalid URL $remoteUrl." }
                        }
                    }
                } else {
                    log.warn { "Failed to list remotes." }
                }
            }

            installDependencies(workingDir)

            val dependenciesJson = run(workingDir, "info", ".", "-j").stdout
            val pkgInfos = jsonMapper.readTree(dependenciesJson)
            val packageList = removeProjectPackage(pkgInfos, definitionFile.name)
            val packages = parsePackages(packageList, workingDir, conanStoragePath)

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
    private fun parsePackages(nodes: List<JsonNode>, workingDir: File, conanStoragePath: File): Map<String, Package> =
        nodes.associate { node ->
            val pkg = parsePackage(node, workingDir, conanStoragePath)
            "${pkg.id.name}:${pkg.id.version}" to pkg
        }

    /**
     * Return the [Package] parsed from the given [node].
     */
    private fun parsePackage(node: JsonNode, workingDir: File, conanStoragePath: File): Package {
        val id = parsePackageId(node, workingDir)
        val homepageUrl = node["homepage"].textValueOrEmpty()

        return Package(
            id = id,
            authors = parseAuthors(node),
            declaredLicenses = parseDeclaredLicenses(node),
            description = parsePackageField(node, workingDir, "description"),
            homepageUrl = homepageUrl,
            binaryArtifact = RemoteArtifact.EMPTY, // TODO: implement me!
            sourceArtifact = parseSourceArtifact(id, conanStoragePath),
            vcs = processPackageVcs(VcsInfo.EMPTY, homepageUrl)
        )
    }

    /**
     * Return the value `conan inspect` reports for the given [field].
     */
    private fun inspectField(pkgName: String, workingDir: File, field: String): String =
        pkgInspectResults.getOrPut(pkgName) {
            val jsonFile = createOrtTempFile(managerName)
            run(workingDir, "inspect", pkgName, "--json", jsonFile.absolutePath).requireSuccess()
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
     * Try to read the source artifact from the [conanStoragePath], if not possible return [RemoteArtifact.EMPTY].
     */
    private fun parseSourceArtifact(id: Identifier, conanStoragePath: File): RemoteArtifact {
        val conanDataFile = conanStoragePath.resolve("${id.name}/${id.version}/_/_/export/conandata.yml")

        return runCatching {
            val conanData = yamlMapper.readTree(conanDataFile)
            val artifactEntry = conanData["sources"][id.version]

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
     * Run `conan install .` to install packages in [workingDir]. The `conan install .` command looks for the package
     * in the remote repository that is built for the same architecture as the host that runs this command. That package
     * may not exist in the remote and in that case the command will fail. As this is acceptable since package
     * metadata is fetched anyway, ignore the exit code by not using [run] but [ProcessCapture] directly.
     */
    private fun installDependencies(workingDir: File) {
        ProcessCapture(workingDir, "conan", "install", ".")
    }

    /**
     * Parse information about the package author from the given JSON [node]. If present, return a set containing the
     * author name; otherwise, return an empty set.
     */
    private fun parseAuthors(node: JsonNode): SortedSet<String> =
        parseAuthorString(node["author"]?.textValue(), '<', '(')?.let { sortedSetOf(it) } ?: sortedSetOf()
}
