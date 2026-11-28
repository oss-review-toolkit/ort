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

// The lifecycle scopes other than for runtime and build, which need special handling.
private val OTHER_LIFECYCLE_SCOPES = listOf(
    LifecycleScopeType.DESIGN,
    LifecycleScopeType.DEVELOPMENT,
    LifecycleScopeType.TEST,
    LifecycleScopeType.OTHER
)

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

        val context = SpdxDocumentContext(relationships, packages)

        // Prefer explicit DESCRIBES relationships over root elements, as the JSON-LD store turns all elements into root
        // elements if a document does not specify any.
        val documentDescribes = context.getElementsReferredFrom(spdxDocument, RelationshipType.DESCRIBES)
            .ifEmpty { spdxDocument.rootElements }

        val fallbackProjectName = getFallbackProjectName(analysisRoot, definitionFile)

        fun getProjectId(pkg: SpdxPackage, sbom: Sbom? = null): Identifier {
            val projectName = pkg.name.getOrNull()
                ?: sbom?.name?.getOrNull()
                ?: spdxDocument.name.getOrNull()
                ?: fallbackProjectName

            return Identifier(
                type = projectType,
                namespace = "",
                name = projectName.sanitize(),
                version = pkg.packageVersion.getOrDefault("").sanitize()
            )
        }

        val projects = documentDescribes.flatMap { describedElement ->
            when (describedElement) {
                is Sbom -> {
                    val describedRootPackages = describedElement.rootElements
                        .filterIsInstanceTo(mutableSetOf<SpdxPackage>())

                    val diff = describedElement.rootElements - describedRootPackages
                    if (diff.isNotEmpty()) {
                        logger.info {
                            "The following root elements are not packages and will be discarded: " +
                                diff.joinToString { "${it.name.getOrDefault("unnamed")} (${it.type})" }
                        }
                    }

                    describedRootPackages.map { pkg -> pkg to getProjectId(pkg, describedElement) }
                }

                is SpdxPackage -> listOf(describedElement to getProjectId(describedElement))

                else -> {
                    logger.warn { "Unhandled root element type '${describedElement.type}'." }
                    emptyList()
                }
            }
        }.distinctBy { (pkg, _) ->
            //  Remove duplicates in case the same package shows up more than once, for example as a document root
            //  element and also through DESCRIBES.
            pkg
        }

        val (projectPackage, projectId) = requireNotNull(projects.singleOrNull()) {
            "Currently only a single project per SPDX document is supported."
        }

        // The project package may be the output of a build. Inputs of that build that are the output of other builds
        // are the packages that get installed, like for the Yocto build that creates the image's root filesystem. All
        // other inputs are the sources the project package is built from.
        val projectBuilds = context.getElementsReferringTo(projectPackage, RelationshipType.HAS_OUTPUT)
            .filterIsInstance<Build>()

        val (installedPackages, sourcePackages) = projectBuilds
            .flatMap { context.getElementsReferredFrom(it, RelationshipType.HAS_INPUT) }
            .filterIsInstance<SpdxPackage>()
            .distinct()
            .partition { input ->
                context.getElementsReferringTo(input, RelationshipType.HAS_OUTPUT).any { it is Build }
            }

        val runtimeDependencies = installedPackages.map { SpdxDependency(it, LifecycleScopeType.RUNTIME, context) } +
            // The project package may also directly depend on the packages that get installed.
            dependencyHandler.dependenciesFor(SpdxDependency(projectPackage, LifecycleScopeType.RUNTIME, context))

        addDirectDependencies(projectId, LifecycleScopeType.RUNTIME, runtimeDependencies)

        // Collect all packages that are reachable in the runtime scope, as any of them may be the output of a build
        // whose input packages are the sources the package is built from. For Yocto, these are the builds of the
        // recipes.
        val runtimePackages = mutableSetOf<SpdxPackage>()
        val queue = ArrayDeque(runtimeDependencies)
        while (queue.isNotEmpty()) {
            val dependency = queue.removeFirst()
            if (runtimePackages.add(dependency.element)) queue += dependencyHandler.dependenciesFor(dependency)
        }

        val buildDependencies = runtimePackages
            .flatMap { context.getElementsReferringTo(it, RelationshipType.HAS_OUTPUT) }
            .filterIsInstance<Build>()
            .flatMapTo(mutableSetOf()) { context.getBuildInputs(it) }

        // The sources of the project package are needed at build time, and so are the build dependencies of the builds
        // of the project package, like tools. If there are sources, the latter usually also are dependencies of the
        // sources.
        val projectBuildDependencies = sourcePackages.map { SpdxDependency(it, LifecycleScopeType.BUILD, context) } +
            projectBuilds.flatMap { context.getBuildDependencies(it) }

        // The project package and the packages it needs at runtime may also directly depend on packages at build time.
        // Their own dependencies are what they need at runtime, so follow these instead of their build dependencies.
        val directBuildDependencies = (runtimePackages + projectPackage)
            .flatMap { context.getDependencies(it, LifecycleScopeType.BUILD) }
            .filterIsInstance<SpdxPackage>()
            .map { SpdxDependency(it, LifecycleScopeType.RUNTIME, context) }

        addDirectDependencies(
            projectId,
            LifecycleScopeType.BUILD,
            projectBuildDependencies + buildDependencies + directBuildDependencies
        )

        // Like for direct build dependencies, follow the runtime dependencies of packages the project package and the
        // packages it needs at runtime depend on in any other lifecycle scope.
        OTHER_LIFECYCLE_SCOPES.forEach { scope ->
            val dependencies = (runtimePackages + projectPackage)
                .flatMap { context.getDependencies(it, scope) }
                .filterIsInstance<SpdxPackage>()
                .map { SpdxDependency(it, LifecycleScopeType.RUNTIME, context) }

            addDirectDependencies(projectId, scope, dependencies)
        }

        // As the project package is not returned as a package, take over its metadata to the project.
        val projectMetadata = context.createPackage(projectPackage)

        val project = Project(
            id = projectId,
            cpe = projectMetadata.cpe,
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
            authors = emptySet(),
            declaredLicenses = projectMetadata.declaredLicenses,
            // Do not derive the (processed) VCS information from the working directory as done for several other
            // package managers, including SpdxDocumentFile, because the location of the SPDX file usually does not
            // match the location of the project it describes. Only use the VCS information from the project package.
            vcs = projectMetadata.vcs,
            description = projectMetadata.description,
            homepageUrl = projectMetadata.homepageUrl,
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
 * that are not a direct or transitive dependency of any other of the [nodes], plus one node per dependency cycle that
 * is not reachable from any of these nodes, as all members of such a cycle are a dependency of another node. Nodes are
 * returned in the order they are given.
 */
internal fun <T> getEntryPoints(nodes: Collection<T>, dependenciesFor: (T) -> Collection<T>): List<T> {
    val candidates = nodes.toCollection(LinkedHashSet())

    // Also follow dependencies that are not among the candidates, as these may lead to other candidates.
    val dependedOn = mutableSetOf<T>()
    val queue = ArrayDeque(candidates.flatMap { dependenciesFor(it) })
    while (queue.isNotEmpty()) {
        val node = queue.removeFirst()
        if (dependedOn.add(node)) queue += dependenciesFor(node)
    }

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
