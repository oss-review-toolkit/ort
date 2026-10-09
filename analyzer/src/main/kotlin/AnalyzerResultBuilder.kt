/*
 * Copyright (C) 2017 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.analyzer

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.DependencyGraph
import org.ossreviewtoolkit.model.DependencyGraphNavigator
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.Includes
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.model.utils.convertToDependencyGraph
import org.ossreviewtoolkit.utils.common.getDuplicates

class AnalyzerResultBuilder {
    private val projects = mutableSetOf<Project>()
    private val packages = mutableSetOf<Package>()
    private val issues = mutableMapOf<Identifier, List<Issue>>()
    private val dependencyGraphs = mutableMapOf<String, DependencyGraph>()

    fun build(excludes: Excludes = Excludes.EMPTY, includes: Includes = Includes.EMPTY): AnalyzerResult {
        val mergedPackages = mergePackagesByIdentifier(packages)

        val duplicates = (projects.map { it.toPackage() } + mergedPackages).getDuplicates { it.id }
        require(duplicates.isEmpty()) {
            "Unable to create the AnalyzerResult as it contains projects and / or packages with the same ids: " +
                duplicates.values
        }

        return AnalyzerResult(projects, mergedPackages, issues, dependencyGraphs)
            .convertToDependencyGraph(excludes, includes)
            .resolvePackageManagerDependencies()
    }

    fun addResult(projectAnalyzerResult: ProjectAnalyzerResult) =
        apply {
            // TODO: It might be, e.g. in the case of PIP "requirements.txt" projects, that different projects with
            //       the same ID exist. Decide how to handle that case.
            val existingProject = projects.find { it.id == projectAnalyzerResult.project.id }

            if (existingProject != null) {
                val existingDefinitionFileUrl = with(existingProject) {
                    VcsHost.fromUrl(vcsProcessed.url)
                        ?.toPermalink(vcsProcessed.copy(path = definitionFilePath))
                        ?: "${vcsProcessed.url}/$definitionFilePath"
                }

                val incomingDefinitionFileUrl = with(projectAnalyzerResult.project) {
                    VcsHost.fromUrl(vcsProcessed.url)
                        ?.toPermalink(vcsProcessed.copy(path = definitionFilePath))
                        ?: "${vcsProcessed.url}/$definitionFilePath"
                }

                val issue = createAndLogIssue(
                    source = "Analyzer",
                    message = "Multiple projects with the same id '${existingProject.id.toCoordinates()}' " +
                        "found. Not adding the project defined in '$incomingDefinitionFileUrl' to the " +
                        "analyzer results as it duplicates the project defined in " +
                        "'$existingDefinitionFileUrl'."
                )

                val projectIssues = issues.getOrDefault(existingProject.id, emptyList())
                issues[existingProject.id] = projectIssues + issue
            } else {
                addProject(projectAnalyzerResult.project)
                addPackages(projectAnalyzerResult.packages)

                if (projectAnalyzerResult.issues.isNotEmpty()) {
                    issues[projectAnalyzerResult.project.id] = projectAnalyzerResult.issues
                }
            }
        }

    /**
     * Add the given [project] to this builder. This function can be used for projects that have been obtained
     * independently of a [ProjectAnalyzerResult].
     */
    fun addProject(project: Project) = apply { projects += project }

    /**
     * Add the given [packageSet] to this builder. This function can be used for packages that have been obtained
     * independently of a [ProjectAnalyzerResult].
     */
    fun addPackages(packageSet: Set<Package>) = apply { packages += packageSet }

    /**
     * Add a [DependencyGraph][graph] with all dependencies detected by the [PackageManager] with the given
     * [name][packageManagerName] to the result produced by this builder.
     */
    fun addDependencyGraph(packageManagerName: String, graph: DependencyGraph) =
        apply { dependencyGraphs[packageManagerName] = graph }
}

private fun mergePackagesByIdentifier(packages: Set<Package>): Set<Package> =
    packages.groupBy { it.id }.values.mapTo(mutableSetOf()) { group ->
        group.singleOrNull() ?: group.merge()
    }

private fun Collection<Package>.merge(): Package {
    val id = first().id

    return Package(
        id = id,
        purl = map { it.purl }.singleNonEmpty(id, "purl", ""),
        cpe = map { it.cpe }.singleNonEmpty(id, "cpe", null),
        authors = flatMapTo(mutableSetOf()) { it.authors },
        declaredLicenses = map { it.declaredLicenses }.singleValue(id, "declaredLicenses"),
        declaredLicensesProcessed = map { it.declaredLicensesProcessed }.singleValue(id, "declaredLicensesProcessed"),
        concludedLicense = map { it.concludedLicense }.singleNonEmpty(id, "concludedLicense", null),
        description = map { it.description }.singleNonEmpty(id, "description", ""),
        homepageUrl = map { it.homepageUrl }.singleNonEmpty(id, "homepageUrl", ""),
        binaryArtifact = map { it.binaryArtifact }.singleNonEmpty(id, "binaryArtifact", RemoteArtifact.EMPTY),
        sourceArtifact = map { it.sourceArtifact }.singleNonEmpty(id, "sourceArtifact", RemoteArtifact.EMPTY),
        publishedAt = map { it.publishedAt }.singleNonEmpty(id, "publishedAt", null),
        vcs = mergeVcs(id, "vcs") { it.vcs },
        vcsProcessed = mergeVcs(id, "vcsProcessed") { it.vcsProcessed },
        isMetadataOnly = all { it.isMetadataOnly },
        isModified = any { it.isModified },
        sourceCodeOrigins = map { it.sourceCodeOrigins }.singleNonEmpty(id, "sourceCodeOrigins", null),
        labels = mergeLabels(id)
    )
}

private fun <T> Collection<T>.singleNonEmpty(id: Identifier, field: String, empty: T): T {
    val values = filterNot { it == empty }.toSet()

    require(values.size <= 1) {
        "Cannot merge packages with id '${id.toCoordinates()}': conflicting $field values."
    }

    return values.singleOrNull() ?: empty
}

private fun <T> Collection<T>.singleValue(id: Identifier, field: String): T {
    val values = toSet()

    require(values.size == 1) {
        "Cannot merge packages with id '${id.toCoordinates()}': conflicting $field values."
    }

    return values.single()
}

private fun Collection<Package>.mergeVcs(id: Identifier, field: String, selector: (Package) -> VcsInfo): VcsInfo =
    VcsInfo(
        type = map { selector(it).type }.singleNonEmpty(id, "$field.type", VcsType.UNKNOWN),
        url = map { selector(it).url }.singleNonEmpty(id, "$field.url", ""),
        revision = map { selector(it).revision }.singleNonEmpty(id, "$field.revision", ""),
        path = map { selector(it).path }.singleNonEmpty(id, "$field.path", "")
    )

private fun Collection<Package>.mergeLabels(id: Identifier): Map<String, String> =
    flatMap { it.labels.entries }
        .groupBy({ it.key }, { it.value })
        .toSortedMap()
        .mapValues { (key, values) -> values.singleValue(id, "labels['$key']") }

private fun AnalyzerResult.resolvePackageManagerDependencies(): AnalyzerResult {
    if (dependencyGraphs.size < 2) return this

    val handler = PackageManagerDependencyHandler(this)
    val navigator = DependencyGraphNavigator(dependencyGraphs)

    // Exit early if no graph contains placeholder nodes for package manager dependencies to avoid expensive graph
    // reconstruction in those regular cases.
    val hasPackageManagerDependencies = dependencyGraphs.any { (packageManagerName, graph) ->
        graph.scopes.any { (_, rootIndices) ->
            val nodes = navigator.dependenciesAccessor(packageManagerName, graph, rootIndices)
            nodes.any { it.isPackageManagerDependency }
        }
    }

    if (!hasPackageManagerDependencies) return this

    logger.info { "Resolving package manager dependencies across ${dependencyGraphs.size} graphs." }

    // Resolve package manager dependencies by constructing new graphs that have the placeholder nodes replaced with
    // copies of the referenced nodes.
    val graphs = dependencyGraphs.mapValues { (packageManagerName, graph) ->
        val builder = DependencyGraphBuilder(handler)

        logger.info { "Resolving dependencies for $packageManagerName across ${graph.scopes.size} scope(s)." }

        graph.scopes.forEach { (scopeName, rootIndices) ->
            val nodes = navigator.dependenciesAccessor(packageManagerName, graph, rootIndices)
            val resolvableNodes = nodes.flatMap { node ->
                handler.resolvePackageManagerDependency(node)
            }

            resolvableNodes.forEach { node ->
                // TODO: Adding a dependency internally calls `areDependenciesEqual()`, but the implementation from
                //       `PackageManagerDependencyHandler` and not any package-manager-specific override, meaning that
                //       optimizations done there are lost here. Ideally, this would be solved by changing the whole
                //       algorithm to not reconstruct the graph by copying all nodes, but by only replacing the
                //       placeholder nodes for package manager dependencies with references to the respective graphs.
                builder.addDependency(scopeName, node)
            }
        }

        // Package managers that do not use the dependency graph representation might not have a check implemented to
        // verify that packages exist for all dependencies, so the reference check needs to be disabled here.
        builder.build(checkReferences = false)
    }

    return copy(dependencyGraphs = graphs)
}
