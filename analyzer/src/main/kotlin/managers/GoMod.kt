/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper

import java.io.File
import java.util.SortedSet

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.managers.utils.normalizeModuleVersion
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
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
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.stashDirectories
import org.ossreviewtoolkit.utils.common.withoutSuffix
import org.ossreviewtoolkit.utils.ort.log

/**
 * The [Go Modules](https://github.com/golang/go/wiki/Modules) package manager for Go.
 *
 * Note: The file `go.sum` is not a lockfile as go modules already allows for reproducible builds without that file.
 * Thus, no logic for handling the [AnalyzerConfiguration.allowDynamicVersions] is needed.
 */
class GoMod(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig), CommandLineTool {
    class Factory : AbstractPackageManagerFactory<GoMod>("GoMod") {
        override val globsForDefinitionFiles = listOf("go.mod")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = GoMod(managerName, analysisRoot, analyzerConfig, repoConfig)
    }

    companion object {
        const val DEFAULT_GO_PROXY = "https://proxy.golang.org"
    }

    override fun command(workingDir: File?) = "go"

    override fun getVersionArguments() = "version"

    override fun transformVersion(output: String) = output.removePrefix("go version go").substringBefore(' ')

    override fun mapDefinitionFiles(definitionFiles: List<File>): List<File> =
        definitionFiles.filterNot { definitionFile ->
            "vendor" in definitionFile
                .parentFile
                .relativeTo(analysisRoot)
                .invariantSeparatorsPath
                .split('/')
        }

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        val projectDir = definitionFile.parentFile

        stashDirectories(projectDir.resolve("vendor")).use {
            val moduleInfoForModuleName = getModuleInfos(projectDir).associateBy({ it.path }, { it })
            val graph = getModuleGraph(projectDir, moduleInfoForModuleName)
            val projectId = graph.projectId()
            val packageIds = graph.nodes() - projectId
            val packages = packageIds.mapTo(sortedSetOf()) { moduleInfoForModuleName.getValue(it.name).toPackage() }
            val projectVcs = processProjectVcs(projectDir)

            val dependenciesScopePackageIds = getTransitiveMainModuleDependencies(projectDir).let { moduleNames ->
                graph.nodes().filterTo(mutableSetOf()) { it.name in moduleNames }
            }

            val scopes = sortedSetOf(
                Scope(
                    name = "main",
                    dependencies = graph.subgraph(dependenciesScopePackageIds).toPackageReferenceForest(projectId)
                ),
                Scope(
                    name = "vendor",
                    dependencies = graph.toPackageReferenceForest(projectId)
                )
            )

            return listOf(
                ProjectAnalyzerResult(
                    project = Project(
                        id = Identifier(
                            type = managerName,
                            namespace = "",
                            name = projectId.name,
                            version = projectVcs.revision
                        ),
                        definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                        authors = sortedSetOf(), // Go mod doesn't support author information.
                        declaredLicenses = sortedSetOf(), // Go mod doesn't support declared licenses.
                        vcs = projectVcs,
                        vcsProcessed = projectVcs,
                        homepageUrl = "",
                        scopeDependencies = scopes
                    ),
                    packages = packages
                )
            )
        }
    }

    /**
     * Return the module graph output from `go mod graph` with non-vendor dependencies removed.
     */
    private fun getModuleGraph(projectDir: File, moduleInfoForModuleName: Map<String, ModuleInfo>): Graph {
        fun moduleInfo(moduleName: String): ModuleInfo = moduleInfoForModuleName.getValue(moduleName)

        fun parseModuleEntry(entry: String): Identifier =
            entry.substringBefore('@').let { moduleName ->
                moduleInfo(moduleName).toId()
            }

        var graph = Graph()

        val edges = run("mod", "graph", workingDir = projectDir)
        edges.stdout.lines().forEach { line ->
            if (line.isBlank()) return@forEach

            val columns = line.split(' ')
            require(columns.size == 2) { "Expected exactly one occurrence of ' ' on any non-blank line." }

            val parent = parseModuleEntry(columns[0])
            val child = parseModuleEntry(columns[1])

            if (moduleInfo(parent.name).main && moduleInfo(child.name).indirect) {
                log.debug { "Module '${child.name}' is an indirect dependency of '${parent.name}. Skip adding edge." }
                return@forEach
            }

            graph.addEdge(parent, child)
        }

        val vendorModules = getVendorModules(graph, projectDir)
        if (vendorModules.size < graph.size()) {
            log.debug { "Removing ${graph.size() - vendorModules.size} non-vendor modules from the dependency graph." }

            graph = graph.subgraph(vendorModules)
        }

        return graph
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class ModuleInfo(
        @JsonProperty("Path")
        val path: String,

        @JsonProperty("Version")
        val version: String = "",

        @JsonProperty("Replace")
        val replace: ModuleInfo? = null,

        @JsonProperty("Indirect")
        val indirect: Boolean = false,

        @JsonProperty("Main")
        val main: Boolean = false
    )

    private fun ModuleInfo.toId(): Identifier =
        Identifier(
            type = managerName.takeIf { version.isBlank() } ?: "Go",
            namespace = "",
            name = path,
            version = normalizeModuleVersion(version)
        )

    /**
     * Return the list of all modules contained in the dependency tree with resolved versions and the 'replace'
     * directive applied.
     */
    private fun getModuleInfos(projectDir: File): List<ModuleInfo> {
        val list = run("list", "-m", "-json", "all", workingDir = projectDir)

        list.stdout.byteInputStream().use { inputStream ->
            val result = mutableListOf<ModuleInfo>()

            JsonFactory().createParser(inputStream).apply {
                codec = ObjectMapper()
                nextToken()

                while (hasCurrentToken()) {
                    result += jsonMapper.readValue(this, ModuleInfo::class.java)
                    nextToken()
                }
            }

            return result.map { it.replace ?: it }
        }
    }

    /**
     * Return the subset of the modules in [graph] required for building and testing the main module. So, test
     * dependencies of dependencies are filtered out.
     */
    private fun getVendorModules(graph: Graph, projectDir: File): Set<Identifier> {
        val vendorModuleNames = mutableSetOf(graph.projectId().name)

        graph.nodes().chunked(WHY_CHUNK_SIZE).forEach { ids ->
            val moduleNames = ids.map { it.name }.toTypedArray()
            // Use the ´-m´ switch to use module names because the graph also uses module names, not package names.
            // This fixes the accidental dropping of some modules.
            vendorModuleNames += parseWhyOutput(run(projectDir, "mod", "why", "-m", "-vendor", *moduleNames).stdout)
        }

        return graph.nodes().filterTo(mutableSetOf()) { it.name in vendorModuleNames }
    }

    /**
     * Return the module names of all transitive main module dependencies. This excludes test-only dependencies.
     */
    private fun getTransitiveMainModuleDependencies(projectDir: File): Set<String> {
        val result = mutableSetOf<String>()

        val list = run(
            "list", "-deps", "-f", "{{with .Module}}{{.Path}} {{.Version}}{{end}}", "./...", workingDir = projectDir
        )

        list.stdout.lines().forEach { line ->
            val columns = line.split(' ')
            if (columns.size != 2) return@forEach

            result += columns[0]
        }

        return result
    }

    private fun ModuleInfo.toPackage(): Package {
        val vcsInfo = toVcsInfo().takeUnless { it.type == VcsType.UNKNOWN }.orEmpty()

        return Package(
            id = toId(),
            authors = sortedSetOf(), // Go mod doesn't support author information.
            declaredLicenses = sortedSetOf(), // Go mod doesn't support declared licenses.
            description = "",
            homepageUrl = "",
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = if (vcsInfo == VcsInfo.EMPTY) {
                toSourceArtifact()
            } else {
                RemoteArtifact.EMPTY
            },
            vcs = vcsInfo
        )
    }

    private fun ModuleInfo.toSourceArtifact(): RemoteArtifact {
        /**
         * The below construction of the remote artifact URL makes several simplifying assumptions and it is still
         * questionable whether those assumptions are ok:
         *
         *   1. GOPROXY in general can hold a list of (fallback) proxy URLs.
         *   2. There are special values like 'direct' and 'off'.
         *   3. GOPRIVATE variable can specify glob expression against paths for which the proxy should be bypassed.
         *
         * TODO: Reconsider removing the source artifact URLs in favor of VCS locations. Those could be obtained by
         * 1. Exposing needed go internals analog to https://github.com/kisielk/gotool/
         * 2. Provide a simple CLI written in go which uses those internals to obtain the VCS location and revision.
         * 3. Make use of that CLI from within this class.
         */
        val goProxy = getGoProxy()

        return RemoteArtifact(url = "$goProxy/$path/@v/$version.zip", hash = Hash.NONE)
    }

    private fun getGoProxy(): String {
        val firstProxy = Os.env["GOPROXY"].orEmpty()
            .split(',')
            .filterNot { it == "direct" || it == "off" }
            .firstOrNull()
            .orEmpty()

        return firstProxy.ifBlank { DEFAULT_GO_PROXY }
    }
}

/**
 * A class to represent a graph with dependencies. This representation is basically an adjacency list implemented by a
 * map whose keys are package identifiers and whose values are the identifiers of packages these packages depend on.
 */
private class Graph(private val nodeMap: MutableMap<Identifier, Set<Identifier>> = mutableMapOf()) {
    /**
     * Return a set with all nodes (i.e. package identifiers) contained in this graph.
     */
    fun nodes(): Set<Identifier> = nodeMap.keys

    /**
     * Return the size of this graph. This is the number of nodes it contains.
     */
    fun size() = nodeMap.size

    /**
     * Add an edge (i.e. a dependency relation) from [source] to [target] to this dependency graph. Add missing nodes if
     * necessary.
     */
    fun addEdge(source: Identifier, target: Identifier) {
        nodeMap.merge(source, setOf(target)) { set, _ -> set + target }
        nodeMap.getOrPut(target) { emptySet() }
    }

    /**
     * Return a subgraph of this [Graph] that contains only nodes from the given set of [subNodes]. This can be used to
     * construct graphs for specific scopes.
     */
    fun subgraph(subNodes: Set<Identifier>): Graph =
        Graph(
            nodeMap.filter { it.key in subNodes }.mapValuesTo(mutableMapOf()) { e ->
                e.value.filterTo(mutableSetOf()) { it in subNodes }
            }
        )

    /**
     * Search for the single package that represents the main project. This is the only package without a version. Fail
     * if no single package with this criterion can be found.
     */
    fun projectId(): Identifier =
        nodes().filter { it.version.isBlank() }.let { idsWithoutVersion ->
            require(idsWithoutVersion.size == 1) {
                "Expected exactly one unique package without version but got ${idsWithoutVersion.joinToString()}."
            }

            idsWithoutVersion.first()
        }

    /**
     * Convert this [Graph] to a set of [PackageReference]s that spawn the dependency trees of the direct dependencies
     * of the given [root] package.
     */
    fun toPackageReferenceForest(root: Identifier): SortedSet<PackageReference> {
        fun getPackageReference(id: Identifier, predecessorNodes: Set<Identifier> = emptySet()): PackageReference {
            val dependencies = nodeMap.getValue(id).filter { it !in predecessorNodes }.mapTo(sortedSetOf()) {
                getPackageReference(it, predecessorNodes + id)
            }

            return PackageReference(
                id = id,
                linkage = PackageLinkage.PROJECT_STATIC,
                dependencies = dependencies
            )
        }

        return dependencies(root).mapTo(sortedSetOf()) { getPackageReference(it) }
    }

    /**
     * Return the identifiers of the direct dependencies of the package denoted by [id].
     */
    private fun dependencies(id: Identifier): Set<Identifier> = nodeMap[id].orEmpty()
}

private val GITHUB_NAME_REGEX = "(github\\.com/[^/]+/[^/]+)/v\\d".toRegex()

private const val DATE_REVISION_PATTERN = "[\\d]{14}-(?<sha1>[0-9a-f]+)"

// See https://golang.org/ref/mod#pseudo-versions.
private val PSEUDO_VERSION_REGEXES = listOf(
    // Format for no known base version, e.g. v0.0.0-20191109021931-daa7c04131f5.
    "^v0\\.0\\.0-$DATE_REVISION_PATTERN$".toRegex(),
    // Format for base version is a release version, e.g. v1.2.4-0.20191109021931-daa7c04131f5.
    "^v\\d+\\.\\d+\\.\\d+-0\\.$DATE_REVISION_PATTERN$".toRegex(),
    // base version is a pre-release version, e.g. v1.2.4-pre.0.20191109021931-daa7c04131f5.
    "^v\\d+\\.\\d+\\.\\d+-pre.0\\.$DATE_REVISION_PATTERN$".toRegex(),
)

/** Separator string indicating that data of a new package follows in the output of the go mod why command. */
private const val PACKAGE_SEPARATOR = "# "

/**
 * Constant for the number of modules to pass to the _go mod why_ command in a single step. This value is chosen rather
 * arbitrarily to keep the command's output to a reasonable size.
 */
private const val WHY_CHUNK_SIZE = 32

private fun getRevision(version: String): String {
    version.withoutSuffix("+incompatible")?.let { return getRevision(it) }

    PSEUDO_VERSION_REGEXES.forEach { regex ->
        regex.find(version)?.let { matchResult ->
            return matchResult.groups["sha1"]!!.value
        }
    }

    return version
}

internal fun GoMod.ModuleInfo.toVcsInfo(): VcsInfo {
    val hostname = GITHUB_NAME_REGEX.matchEntire(path)?.let {
        it.groupValues[1]
    } ?: path

    val vcsInfo = VcsHost.parseUrl("https://$hostname")
    return vcsInfo.copy(revision = getRevision(version))
}

/**
 * Parse the given [output] generated by the _go mod why_ command and the names of modules that are reported to be used
 * by the main module. See https://golang.org/ref/mod#go-mod-why.
 */
internal fun parseWhyOutput(output: String): Set<String> {
    val usedModules = mutableSetOf<String>()
    var currentModule: String? = null

    output.lineSequence().forEach { line ->
        if (line.startsWith(PACKAGE_SEPARATOR)) {
            currentModule = line.substring(PACKAGE_SEPARATOR.length)
        } else {
            if (!line.startsWith('(') && line.isNotBlank()) {
                currentModule?.let { usedModules += it }
            }
        }
    }

    return usedModules
}
