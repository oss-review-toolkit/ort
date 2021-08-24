/*
 * Copyright (C) 2019 HERE Europe B.V.
 * Copyright (C) 2019 Verifa Oy.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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
import java.util.Stack

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.parseAuthorString
import org.ossreviewtoolkit.downloader.VersionControlSystem
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
import org.ossreviewtoolkit.utils.CommandLineTool
import org.ossreviewtoolkit.utils.Os
import org.ossreviewtoolkit.utils.ProcessCapture
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.stashDirectories
import org.ossreviewtoolkit.utils.textValueOrEmpty
import org.ossreviewtoolkit.utils.toUri

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
    override fun resolveDependencies(definitionFile: File): List<ProjectAnalyzerResult> {
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
            val rootNode = jsonMapper.readTree(dependenciesJson)
            val packageList = removeProjectPackage(rootNode, definitionFile)
            val packages = extractPackages(packageList, workingDir)

            val dependenciesScope = Scope(
                name = SCOPE_NAME_DEPENDENCIES,
                dependencies = extractDependencies(rootNode, SCOPE_NAME_DEPENDENCIES, workingDir)
            )
            val devDependenciesScope = Scope(
                name = SCOPE_NAME_DEV_DEPENDENCIES,
                dependencies = extractDependencies(rootNode, SCOPE_NAME_DEV_DEPENDENCIES, workingDir)
            )

            val projectPackage = extractProjectPackage(rootNode, definitionFile, workingDir)

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
     * Return the dependency tree starting from a [rootNode] for given [scopeName].
     */
    private fun extractDependencyTree(
        rootNode: JsonNode,
        workingDir: File,
        pkg: JsonNode,
        scopeName: String
    ): SortedSet<PackageReference> {
        val result = mutableSetOf<PackageReference>()

        pkg[scopeName]?.forEach {
            val childRef = it.textValueOrEmpty()
            rootNode.iterator().forEach { child ->
                if (child["reference"].textValueOrEmpty() == childRef) {
                    log.debug { "Found child '$childRef'." }

                    val packageReference = PackageReference(
                        id = extractPackageId(child, workingDir),
                        dependencies = extractDependencyTree(rootNode, workingDir, child, SCOPE_NAME_DEPENDENCIES)
                    )
                    result += packageReference

                    val packageDevReference = PackageReference(
                        id = extractPackageId(child, workingDir),
                        dependencies = extractDependencyTree(rootNode, workingDir, child, SCOPE_NAME_DEV_DEPENDENCIES)
                    )

                    result += packageDevReference
                }
            }
        }
        return result.toSortedSet()
    }

    /**
     * Run through each package and extract list of its dependencies (also transitive ones).
     */
    private fun extractDependencies(
        rootNode: JsonNode,
        scopeName: String,
        workingDir: File
    ): SortedSet<PackageReference> {
        val stack = Stack<JsonNode>().apply { addAll(rootNode) }
        val dependencies = mutableSetOf<PackageReference>()

        while (!stack.empty()) {
            val pkg = stack.pop()
            extractDependencyTree(rootNode, workingDir, pkg, scopeName).forEach {
                dependencies += it
            }
        }
        return dependencies.toSortedSet()
    }

    /**
     * Return the map of packages and their identifiers which are contained in [node].
     */
    private fun extractPackages(node: List<JsonNode>, workingDir: File): Map<String, Package> {
        val result = mutableMapOf<String, Package>()
        val stack = Stack<JsonNode>().apply { addAll(node) }

        while (!stack.empty()) {
            val currentNode = stack.pop()
            val pkg = extractPackage(currentNode, workingDir)
            result["${pkg.id.name}:${pkg.id.version}"] = pkg
        }

        return result
    }

    /**
     * Return the [Package] extracted from given [node].
     */
    private fun extractPackage(node: JsonNode, workingDir: File) =
        Package(
            id = extractPackageId(node, workingDir),
            authors = parseAuthors(node),
            declaredLicenses = extractDeclaredLicenses(node),
            description = extractPackageField(node, workingDir, "description"),
            homepageUrl = node["homepage"].textValueOrEmpty(),
            binaryArtifact = RemoteArtifact.EMPTY, // TODO: implement me!
            sourceArtifact = RemoteArtifact.EMPTY, // TODO: implement me!
            vcs = extractVcsInfo(node)
        )

    /**
     * Return the value `conan inspect` reports for the given [field].
     */
    private fun runInspectRawField(pkgName: String, workingDir: File, field: String): String =
        run(workingDir, "inspect", pkgName, "--raw", field).stdout

    /**
     * Return the full list of packages, excluding the project level information.
     */
    private fun removeProjectPackage(rootNode: JsonNode, definitionFile: File): List<JsonNode> =
        rootNode.find {
            // Contains because conanfile.py's reference string often includes other data.
            it["reference"].textValueOrEmpty().contains(definitionFile.name)
        }?.let { projectPackage ->
            rootNode.minusElement(projectPackage)
        } ?: rootNode.toList<JsonNode>()

    /**
     * Return the set of declared licenses contained in [node].
     */
    private fun extractDeclaredLicenses(node: JsonNode): SortedSet<String> =
        sortedSetOf<String>().also { licenses ->
            node["license"]?.mapNotNullTo(licenses) { it.textValue() }
        }

    /**
     * Return the [Identifier] for the package contained in [node].
     */
    private fun extractPackageId(node: JsonNode, workingDir: File) =
        Identifier(
            type = "Conan",
            namespace = "",
            name = extractPackageField(node, workingDir, "name"),
            version = extractPackageField(node, workingDir, "version")
        )

    /**
     * Return the [VcsInfo] contained in [node].
     */
    private fun extractVcsInfo(node: JsonNode) =
        VcsInfo(
            type = VcsType.GIT,
            url = node["url"].textValueOrEmpty(),
            revision = node["revision"].textValueOrEmpty().takeUnless { it == "0" }.orEmpty()
        )

    /**
     * Return the value of [field] from the output of `conan inspect --raw` for the package in [node].
     */
    private fun extractPackageField(node: JsonNode, workingDir: File, field: String): String =
        runInspectRawField(node["display_name"].textValue(), workingDir, field)

    /**
     * Return a [Package] containing project-level information depending on which [definitionFile] was found:
     * - conanfile.txt: `conan inspect conanfile.txt` is not supported.
     * - conanfile.py: `conan inspect conanfile.py` is supported and more useful project metadata is extracted.
     *
     * TODO: The format of `conan info` output for a conanfile.txt file may be such that we can get project metadata
     *       from the `requires` field. Need to investigate whether this is a sure thing before implementing.
     */
    private fun extractProjectPackage(rootNode: JsonNode, definitionFile: File, workingDir: File): Package {
        val projectPackageJson = requireNotNull(rootNode.find {
            it["reference"].textValue().contains(definitionFile.name)
        })

        return if (definitionFile.name == "conanfile.py") {
            generateProjectPackageFromConanfilePy(projectPackageJson, definitionFile, workingDir)
        } else {
            generateProjectPackageFromConanfileTxt(projectPackageJson)
        }
    }

    /**
     * Return a [Package] containing project-level information extracted from [node] and [definitionFile] using the
     * `conan inspect` command.
     */
    private fun generateProjectPackageFromConanfilePy(node: JsonNode, definitionFile: File, workingDir: File): Package =
        Package(
            id = Identifier(
                type = managerName,
                namespace = "",
                name = runInspectRawField(definitionFile.name, workingDir, "name"),
                version = runInspectRawField(definitionFile.name, workingDir, "version")
            ),
            authors = parseAuthors(node),
            declaredLicenses = extractDeclaredLicenses(node),
            description = runInspectRawField(definitionFile.name, workingDir, "description"),
            homepageUrl = node["homepage"].textValueOrEmpty(),
            binaryArtifact = RemoteArtifact.EMPTY, // TODO: implement me!
            sourceArtifact = RemoteArtifact.EMPTY, // TODO: implement me!
            vcs = extractVcsInfo(node)
        )

    /**
     * Return a [Package] containing project-level information extracted from [node].
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
            declaredLicenses = extractDeclaredLicenses(node),
            description = "",
            homepageUrl = node["homepage"].textValueOrEmpty(),
            binaryArtifact = RemoteArtifact.EMPTY, // TODO: implement me!
            sourceArtifact = RemoteArtifact.EMPTY, // TODO: implement me!
            vcs = extractVcsInfo(node)
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
