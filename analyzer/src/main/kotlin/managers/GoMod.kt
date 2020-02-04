/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.analyzer.managers

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.module.kotlin.readValues

import com.here.ort.analyzer.AbstractPackageManagerFactory
import com.here.ort.analyzer.PackageManager
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.Hash
import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.PackageLinkage
import com.here.ort.model.PackageReference
import com.here.ort.model.Project
import com.here.ort.model.ProjectAnalyzerResult
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.Scope
import com.here.ort.model.VcsInfo
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.model.jsonMapper
import com.here.ort.utils.CommandLineTool
import com.here.ort.utils.stashDirectories

import java.io.File
import java.util.SortedSet

/**
 * The [Go Modules](https://github.com/golang/go/wiki/Modules) package manager for Go. The implementation is
 * experimental since it lacks resolving VCS locations and also the way source artifact URLs are crafted needs to proof
 * being useful. It seems favorable to adjust the implementation to only set VCS but not source artifact URLs.
 *
 * Note: The file `go.sum` is not a lockfile as go modules already allows for reproducible builds without that file.
 * Thus no logic for handling the [AnalyzerConfiguration.allowDynamicVersions] is needed.
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

    override fun mapDefinitionFiles(definitionFiles: List<File>): List<File> =
        definitionFiles.filterNot { definitionFile ->
            definitionFile
                .parentFile
                .relativeTo(analysisRoot)
                .invariantSeparatorsPath
                .split('/')
                .contains("vendor")
        }

    override fun resolveDependencies(definitionFile: File): ProjectAnalyzerResult? {
        val projectDir = definitionFile.parentFile

        stashDirectories(File(projectDir, "vendor")).use {
            // Vendor the dependencies in order to link them to a separate `vendor` scope.
            run("mod", "vendor", workingDir = projectDir).requireSuccess()

            val edges = getDependencyGraph(projectDir)
            val vendorModules = getVendorModules(projectDir)

            val projectName = edges.getNodes().filter { it.version.isBlank() }.distinct().let { idsWithoutVersion ->
                require(idsWithoutVersion.size == 1) {
                    "Expected exactly one unique package without version but got ${idsWithoutVersion.joinToString()}."
                }
                idsWithoutVersion.first()
            }.name

            val projectVcs = processProjectVcs(projectDir)

            val packages = edges.getNodes().filter { it.version.isNotBlank() }.map { createPackage(it) }

            val scopes = sortedSetOf(
                Scope(
                    name = "all",
                    dependencies = edges.toPackageReferenceForest { it.version.isBlank() }
                ),
                Scope(
                    name = "vendor",
                    dependencies = edges.toPackageReferenceForest { it.version.isBlank() || it !in vendorModules }
                )
            )

            return ProjectAnalyzerResult(
                project = Project(
                    id = Identifier(
                        type = managerName,
                        namespace = "",
                        name = projectName,
                        version = projectVcs.revision
                    ),
                    definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                    declaredLicenses = sortedSetOf(), // Go mod doesn't support declared licenses.
                    vcs = projectVcs,
                    vcsProcessed = projectVcs,
                    homepageUrl = "",
                    scopes = scopes
                ),
                packages = packages.mapTo(sortedSetOf()) { it.toCuratedPackage() }
            )
        }
    }

    private fun getVendorModules(projectDir: File): Set<Identifier> {
        val vendorModulesJson = run("list", "-json", "-m", "-mod=vendor", "all", workingDir = projectDir)
            .requireSuccess()
            .stdout

        return jsonMapper
            .readValues<ModuleDependency>(JsonFactory().createParser(vendorModulesJson))
            .asSequence()
            .mapTo(mutableSetOf()) { Identifier(managerName, "", it.name, it.version.orEmpty()) }
    }

    private fun getDependencyGraph(projectDir: File): Set<Edge> {
        val graph = run("mod", "graph", workingDir = projectDir).requireSuccess().stdout

        fun parsePackageEntry(entry: String) =
            Identifier(
                type = managerName,
                namespace = "",
                name = entry.substringBefore('@'),
                version = entry.substringAfter('@', "")
            )

        val result = mutableSetOf<Edge>()
        for (line in graph.lines()) {
            if (line.isBlank()) continue

            val columns = line.split(' ')
            require(columns.size == 2) { "Expected exactly one occurrence of ' ' on any non-blank line." }

            val parent = parsePackageEntry(columns[0])
            val child = parsePackageEntry(columns[1])

            result.add(Edge(parent, child))
        }

        return result
    }

    private fun createPackage(id: Identifier) =
        Package(
            id = Identifier(managerName, "", id.name, id.version.orEmpty()),
            declaredLicenses = sortedSetOf(), // Go mod doesn't support declared licenses.
            description = "",
            homepageUrl = "",
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = getSourceArtifactForPackage(id),
            vcs = VcsInfo.EMPTY
        )

    private fun getSourceArtifactForPackage(id: Identifier): RemoteArtifact {
        /**
         * The below construction of the remote artifact URL makes several simplifying assumptions and it is
         * still questionable whether those assumptions are ok:
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

        return RemoteArtifact(url = "$goProxy/${id.name}/@v/${id.version}.zip", hash = Hash.NONE)
    }

    private fun getGoProxy(): String {
        var firstProxy = System.getenv("GOPROXY").orEmpty()
            .split(",")
            .filterNot { it == "direct" || it == "off" }
            .firstOrNull()
            .orEmpty()

        return if (firstProxy.isNotBlank()) firstProxy else DEFAULT_GO_PROXY
    }
}

@Suppress("UnusedPrivateClass") // Used by jsonMapper.readValues() and passed as type parameter.
@JsonIgnoreProperties(ignoreUnknown = true)
private data class ModuleDependency(
    @JsonProperty("Path") val name: String,
    @JsonProperty("Dir") val dir: String,
    @JsonProperty("Version") val version: String?,
    @JsonProperty("Main") val isMain: Boolean = false
)

private data class Edge(
    val source: Identifier,
    val target: Identifier
)

private fun Collection<Edge>.getNodes(): Set<Identifier> = flatMap { listOf(it.source, it.target) }.toSet()

private fun Collection<Edge>.toPackageReferenceForest(
    ignoreNode: (Identifier) -> Boolean = { false }
): SortedSet<PackageReference> {
    data class Node(
        val id: Identifier,
        val outgoingEdges: MutableSet<Identifier> = mutableSetOf(),
        val incomingEdges: MutableSet<Identifier> = mutableSetOf()
    )

    val nodes = mutableMapOf<Identifier, Node>()
    fun addNode(id: Identifier): Node? = if (!ignoreNode(id)) nodes.getOrPut(id, { Node(id) }) else null

    forEach { edge ->
        val source = addNode(edge.source)
        val target = addNode(edge.target)

        if (source != null && target != null) {
            source.outgoingEdges.add(target.id)
            target.incomingEdges.add(source.id)
        }
    }

    fun getPackageReference(id: Identifier, predecessorNodes: Set<Identifier> = mutableSetOf()): PackageReference {
        val node = nodes[id]!!

        val dependencies = node.outgoingEdges.mapNotNull {
            if (predecessorNodes.contains(it)) {
                null
            } else {
                getPackageReference(it, predecessorNodes + id)
            }
        }.toSortedSet()

        return PackageReference(
            id = id,
            linkage = PackageLinkage.PROJECT_STATIC,
            dependencies = dependencies
        )
    }

    return nodes.values.filter { it.incomingEdges.isEmpty() }.map { getPackageReference(it.id) }.toSortedSet()
}
