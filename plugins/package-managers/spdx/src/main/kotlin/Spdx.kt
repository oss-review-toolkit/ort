/*
 * Copyright (C) 2025 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.spdx

import java.io.File

import kotlin.jvm.optionals.getOrDefault
import kotlin.jvm.optionals.getOrNull

import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream
import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.Includes
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor

import org.spdx.library.SpdxModelFactory
import org.spdx.library.model.v3_0_1.build.Build
import org.spdx.library.model.v3_0_1.core.LifecycleScopeType
import org.spdx.library.model.v3_0_1.core.Relationship
import org.spdx.library.model.v3_0_1.core.RelationshipType
import org.spdx.library.model.v3_0_1.core.SpdxDocument
import org.spdx.library.model.v3_0_1.software.Sbom
import org.spdx.library.model.v3_0_1.software.SpdxPackage as SpdxPackage
import org.spdx.storage.simple.InMemSpdxStore
import org.spdx.v3jsonldstore.JsonLDStore

private const val PROJECT_TYPE = "SPDX"
internal const val PACKAGE_TYPE_SPDX = "SPDX"

@OrtPlugin(
    displayName = "SPDX",
    summary = "A package manager that uses SPDX version 3 documents as definition files.",
    factory = PackageManagerFactory::class
)
class Spdx(override val descriptor: PluginDescriptor = SpdxFactory.descriptor) : PackageManager(PROJECT_TYPE) {
    init {
        SpdxModelFactory.init()
    }

    private val dependencyHandler = SpdxDependencyHandler()
    private val graphBuilder = DependencyGraphBuilder(dependencyHandler)

    override val globsForDefinitionFiles = listOf(
        "*.spdx.json", "*.spdx.jsonld",
        "*.spdx.json.zst", "*.spdx.jsonld.zst" // Zstandard-compressed variants.
    )

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        includes: Includes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        val spdxDocument = parseSpdx3File(definitionFile)
        val relationships = mutableListOf<Relationship>()
        val packages = mutableListOf<SpdxPackage>()

        // Only iterate over elements once to get all needed instance types.
        val counts = sortedMapOf<String, Int>()
        spdxDocument.elements.forEach { element ->
            counts.compute(element.type) { _, v -> v?.let { it + 1 } ?: 1 }

            when (element) {
                is Relationship -> relationships += element
                is SpdxPackage -> packages += element
            }
        }

        logger.debug {
            counts.entries.joinToString("\n", prefix = "Found ${spdxDocument.elements.size} SPDX element(s):\n") {
                "\t${it.key}: ${it.value}"
            }
        }

        dependencyHandler.setDocument(relationships, packages)

        val documentDescribes = spdxDocument.rootElements +
            dependencyHandler.getElementsReferredFrom(spdxDocument, RelationshipType.DESCRIBES)

        val fallbackProjectName = getFallbackProjectName(analysisRoot, definitionFile)

        val projects = documentDescribes.mapNotNull { describedElement ->
            when (describedElement) {
                is Sbom -> {
                    val describedRootPackages = describedElement.rootElements
                        .filterIsInstanceTo<SpdxPackage, MutableSet<SpdxPackage>>(mutableSetOf())

                    val diff = describedElement.rootElements - describedRootPackages
                    if (diff.isNotEmpty()) {
                        logger.info {
                            "The following root elements are not packages and will be discarded: " +
                                diff.joinToString { "${it.name.getOrDefault("unnamed")} (${it.type})" }
                        }
                    }

                    describedRootPackages.map { pkg ->
                        val projectName = pkg.name.getOrNull()
                            ?: describedElement.name.getOrNull()
                            ?: spdxDocument.name.getOrNull()
                            ?: fallbackProjectName

                        pkg to Identifier(
                            type = projectType,
                            namespace = "",
                            name = projectName,
                            version = ""
                        )
                    }
                }

                else -> {
                    logger.warn { "Unhandled root element type '${describedElement.type}'. " }
                    null
                }
            }
        }.flatten()

        val (projectPackage, projectId) = requireNotNull(projects.singleOrNull()) {
            "Currently only a single project per SPDX document is supported."
        }

        // The project package may be the output of a build whose inputs are all the packages that get installed. For
        // Yocto, this is the build that creates the image's root filesystem.
        val installedPackages = dependencyHandler.getElementsReferringTo(projectPackage, RelationshipType.HAS_OUTPUT)
            .flatMap { dependencyHandler.getElementsReferredFrom(it, RelationshipType.HAS_INPUT) }
            .filterIsInstance<SpdxPackage>()

        val runtimeDependencies = installedPackages.map { SpdxDependency(it, LifecycleScopeType.RUNTIME) } +
            // The project package may also directly depend on the packages that get installed.
            dependencyHandler.dependenciesFor(SpdxDependency(projectPackage, LifecycleScopeType.RUNTIME))

        addDirectDependencies(projectId, LifecycleScopeType.RUNTIME, runtimeDependencies)

        // The installed packages are outputs of builds whose input packages are the sources the installed packages are
        // built from. For Yocto, these are the builds of the recipes.
        val buildDependencies = installedPackages
            .flatMap { dependencyHandler.getElementsReferringTo(it, RelationshipType.HAS_OUTPUT) }
            .filterIsInstance<Build>()
            .flatMapTo(mutableSetOf()) { dependencyHandler.getInputPackages(it) }
            .map { SpdxDependency(it, LifecycleScopeType.BUILD) }
        addDirectDependencies(projectId, LifecycleScopeType.BUILD, buildDependencies)

        val project = Project(
            id = projectId,
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
            authors = emptySet(),
            declaredLicenses = emptySet(),
            // Do not derive the (processed) VCS information from the working directory as done for several other
            // package managers, including SpdxDocumentFile, because the location of the SPDX file usually does not
            // match the location of the project it describes.
            vcs = VcsInfo.EMPTY,
            homepageUrl = "",
            scopeNames = graphBuilder.scopesFor(projectId)
        )

        return listOf(ProjectAnalyzerResult(project, packages = emptySet()))
    }

    /**
     * Add the entry points among the given [dependencies] as direct dependencies to the [scope] of the project with the
     * given [projectId], see [getEntryPoints]. This is necessary as SPDX documents do not explicitly distinguish direct
     * from transitive dependencies.
     */
    private fun addDirectDependencies(
        projectId: Identifier,
        scope: LifecycleScopeType,
        dependencies: Collection<SpdxDependency>
    ) {
        val directDependencies = getEntryPoints(dependencies) { dependencyHandler.dependenciesFor(it) }
        graphBuilder.addDependencies(projectId, scope.longName, directDependencies)
    }

    override fun createPackageManagerResult(projectResults: Map<File, List<ProjectAnalyzerResult>>) =
        PackageManagerResult(projectResults, graphBuilder.build(), graphBuilder.packages())
}

/**
 * Return those of the given [nodes] from which all [nodes] can be reached via [dependenciesFor]. These are the nodes
 * that are not a dependency of any other of the [nodes], plus one node per dependency cycle that is not reachable from
 * any of these nodes, as all members of such a cycle are a dependency of another node. Nodes are returned in the order
 * they are given.
 */
internal fun <T> getEntryPoints(nodes: Collection<T>, dependenciesFor: (T) -> Collection<T>): List<T> {
    val candidates = nodes.toCollection(LinkedHashSet())
    val dependedOn = candidates.flatMapTo(mutableSetOf()) { dependenciesFor(it) }
    val entryPoints = candidates.filter { it !in dependedOn }

    // Everything that is reachable from a node that is reachable from an entry point is reachable from that entry
    // point, too, so stop the traversal at such nodes.
    fun getReachableNodes(start: T, reachable: Set<T>): Set<T> {
        val visited = mutableSetOf(start)
        val queue = ArrayDeque(listOf(start))

        while (queue.isNotEmpty()) {
            dependenciesFor(queue.removeFirst()).forEach { node ->
                if (node !in reachable && visited.add(node)) queue += node
            }
        }

        return visited
    }

    val reachable = mutableSetOf<T>()
    entryPoints.forEach { reachable += getReachableNodes(it, reachable) }

    val cycleEntryPoints = mutableListOf<T>()
    val reachableFromCycles = mutableSetOf<T>()

    candidates.forEach { node ->
        if (node in reachable || node in reachableFromCycles) return@forEach

        // Previously chosen entry points that are reachable from this node are redundant.
        val reachableFromNode = getReachableNodes(node, reachable)
        cycleEntryPoints.removeAll { it in reachableFromNode }
        cycleEntryPoints += node
        reachableFromCycles += reachableFromNode
    }

    return entryPoints + cycleEntryPoints
}

private fun parseSpdx3File(spdxFile: File): SpdxDocument {
    val baseStore = InMemSpdxStore()
    val store = JsonLDStore(baseStore)
    return spdxFile.inputStream().use { rawStream ->
        when (spdxFile.extension) {
            "zst" -> ZstdCompressorInputStream(rawStream).use { uncompressedStream ->
                store.deSerialize(uncompressedStream, /* overwrite = */ false)
            }

            else -> store.deSerialize(rawStream, /* overwrite = */ false)
        }
    }
}
