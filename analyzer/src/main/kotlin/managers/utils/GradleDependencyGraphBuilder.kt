/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.analyzer.managers.utils

import Dependency

import org.apache.maven.project.ProjectBuildingException

import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.repository.RemoteRepository

import org.ossreviewtoolkit.model.DependencyGraph
import org.ossreviewtoolkit.model.DependencyReference
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.RootDependencyIndex
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.utils.collectMessagesAsString
import org.ossreviewtoolkit.utils.showStackTrace

/**
 * Internal class to represent the result of a search in the dependency graph. The outcome of the search determines
 * how to integrate a specific dependency into the dependency graph.
 */
private sealed class GraphSearchResult

/**
 * A specialized [GraphSearchResult] that indicates that the dependency that was searched for is already present in
 * the dependency graph. This is the easiest case, as the [DependencyReference] that was found can directly be
 * reused.
 */
private data class GraphSearchResultFound(
    /** The reference to the dependency that was searched in the graph. */
    val ref: DependencyReference
) : GraphSearchResult()

/**
 * A specialized [GraphSearchResult] that indicates that the dependency that was searched for was not found in the
 * dependency graph, but a fragment has been detected, to which it can be added.
 */
private data class GraphSearchResultNotFound(
    /** The index of the fragment to which to add the dependency. */
    val fragmentIndex: Int
) : GraphSearchResult()

/**
 * A specialized [GraphSearchResult] that indicates that the dependency that was searched for in its current form
 * cannot be added to any of the existing fragments. This means that either there are no fragments yet or each
 * fragment contains a variant of the dependency with an incompatible dependency tree. In this case, a new fragment
 * has to be added to the graph to host this special variant of this dependency.
 */
private object GraphSearchResultIncompatible : GraphSearchResult()

/**
 * A class that can construct a [DependencyGraph] from a set of Gradle dependencies that provides an efficient storage
 * format for large dependency sets in many scopes.
 *
 * Especially in Android projects, it is common habit to have many scopes that all define their own sets of (often
 * identical) dependencies. Duplicating the dependency trees for the different scopes leads to a high consumption of
 * memory. This class addresses this problem by sharing the components of the dependency graph between the scopes as
 * far as possible.
 *
 * This builder class provides the _addDependency()_ function, which has to be called for all the direct dependencies
 * of the different scopes. From these dependencies it constructs a single, optimized graph that is referenced by all
 * scopes. To reduce the amount of memory required even further, package identifiers are replaced by numeric indices,
 * so that references in the graph are just numbers.
 *
 * Ideally, the resulting dependency graph contains each dependency exactly once. There are, however, cases, in which
 * packages occur multiple times in the project's dependency graph with different dependencies. Such cases are
 * detected, and the corresponding packages form different nodes in the graph, so that they can be distinguished
 * correctly.
 */
class GradleDependencyGraphBuilder(
    /** The name of the dependency manager to use as type of identifiers. */
    private val managerName: String,

    /** The helper object to resolve packages via Maven. */
    private val maven: MavenSupport
) {
    /**
     * A list storing the identifiers of all dependencies added to this builder. This list is then used to resolve
     * dependencies based on their indices.
     */
    private val dependencyIds = mutableListOf<String>()

    /**
     * A map listing the dependencies known to this builder and their numeric indices. This is used for a fast
     * calculation of the index for a specific dependency.
     */
    private val dependencyIndexMapping = mutableMapOf<String, Int>()

    /**
     * Stores all the references to dependencies that have been added so far. Each element of the list represents a
     * fragment of the dependency graph. For each fragment, there is a mapping from a dependency index to the
     * reference pointing to the corresponding dependency tree.
     */
    private val referenceMappings = mutableListOf<MutableMap<Int, DependencyReference>>()

    /** The mapping from scopes to dependencies constructed by this builder. */
    private val scopeMapping = mutableMapOf<String, List<RootDependencyIndex>>()

    /** Stores all packages encountered in the dependency tree. */
    private val resolvedPackages = mutableSetOf<Package>()

    /**
     * A set storing the packages that are direct dependencies of one of the scopes. These are the entry points into
     * the dependency graph.
     */
    private val directDependencies = mutableSetOf<DependencyReference>()

    /**
     * Add the scope with the given [scopeName] to this builder. In most cases, it is not necessary to add scopes
     * explicitly, as they are recorded automatically by _addDependency()_. However, if there are scopes without
     * dependencies, this function can be used to include them into the builder result.
     */
    fun addScope(scopeName: String) {
        scopeMapping.putIfAbsent(scopeName, emptyList())
    }

    /**
     * Add the given [dependency] for the scope with the given [scopeName] to this builder. Use the provided
     * [repositories] to resolve the package if necessary.
     */
    fun addDependency(scopeName: String, dependency: Dependency, repositories: List<RemoteRepository>) {
        addDependencyToGraph(scopeName, dependency, repositories, transitive = false)
    }

    /**
     * Construct the [DependencyGraph] from the dependencies passed to this builder so far.
     */
    fun build(): DependencyGraph = DependencyGraph(dependencyIds, directDependencies, scopeMapping)

    /**
     * Return a set with all the packages that have been encountered for the current project.
     */
    fun packages(): Set<Package> = resolvedPackages

    /**
     * Update the dependency graph by adding the given [dependency], which may be [transitive], for the scope with name
     * [scopeName]. Use the provided [repositories] to resolve the package if necessary. All the dependencies of this
     * dependency are processed recursively.
     */
    private fun addDependencyToGraph(
        scopeName: String,
        dependency: Dependency,
        repositories: List<RemoteRepository>,
        transitive: Boolean
    ): DependencyReference {
        val identifier = identifierFor(dependency)
        val issues = issuesForDependency(dependency)
        val index = updateDependencyMappingAndPackages(identifier, dependency, repositories, issues)

        val ref = when (val result = findDependencyInGraph(index, dependency)) {
            is GraphSearchResultFound -> result.ref
            is GraphSearchResultNotFound ->
                insertIntoGraph(
                    RootDependencyIndex(index, result.fragmentIndex),
                    scopeName,
                    dependency,
                    repositories,
                    issues,
                    transitive
                )
            is GraphSearchResultIncompatible ->
                insertIntoNewFragment(index, scopeName, dependency, repositories, issues, transitive)
        }

        return updateScopeMapping(scopeName, ref, transitive)
    }

    /**
     * Update internal state for the given dependency [id]. Check whether this ID is already known. If not, add it
     * to the data managed by this instance, resolve the package, and update the [issues] if necessary. Return the
     * numeric index for this dependency.
     */
    private fun updateDependencyMappingAndPackages(
        id: String,
        dependency: Dependency,
        repositories: List<RemoteRepository>,
        issues: MutableList<OrtIssue>
    ): Int {
        val dependencyIndex = dependencyIndexMapping[id]
        if (dependencyIndex != null) return dependencyIndex

        updateResolvedPackages(id, dependency, repositories, issues)
        return dependencyIds.size.also {
            dependencyIds += id
            dependencyIndexMapping[id] = it
        }
    }

    /**
     * Search for the [dependency] with the given [index] in the fragments of the dependency graph. Return a
     * [GraphSearchResult] that indicates how to proceed with this dependency.
     */
    private fun findDependencyInGraph(index: Int, dependency: Dependency): GraphSearchResult {
        val mappingForCompatibleFragment = referenceMappings.find { mapping ->
            mapping[index]?.takeIf { dependencyTreeEquals(it, dependency) } != null
        }

        val compatibleReference = mappingForCompatibleFragment?.let { it[index] }
        return compatibleReference?.let { GraphSearchResultFound(it) } ?: handleNoCompatibleDependencyInGraph(index)
    }

    /**
     * Determine how to deal with the dependency with the given [index], for which no compatible variant was found
     * in the dependency graph. Try to find a fragment, in which the dependency can be inserted. If this fails, a new
     * fragment has to be added.
     */
    private fun handleNoCompatibleDependencyInGraph(index: Int): GraphSearchResult {
        val mappingToInsert = referenceMappings.withIndex().find { index !in it.value }
        return mappingToInsert?.let { GraphSearchResultNotFound(it.index) } ?: GraphSearchResultIncompatible
    }

    /**
     * Check whether the dependency tree spawned by [dependency] matches the one [ref] points to. Using this function,
     * packages are identified that occur multiple times in the dependency graph with different sets of dependencies;
     * these have to be placed in separate fragments of the dependency graph.
     */
    private fun dependencyTreeEquals(ref: DependencyReference, dependency: Dependency): Boolean {
        if (ref.dependencies.size != dependency.dependencies.size) return false

        val dependencies1 = ref.dependencies.map { dependencyIds[it.pkg] }
        val dependencies2 = dependency.dependencies.associateBy(::identifierFor)
        if (!dependencies2.keys.containsAll(dependencies1)) return false

        return ref.dependencies.all { refDep ->
            dependencies2[dependencyIds[refDep.pkg]]?.let { dependencyTreeEquals(refDep, it) } ?: false
        }
    }

    /**
     * Add a new fragment to the dependency graph for the [dependency] with the given [index], which may be
     * [transitive] and belongs to the scope with the given [scopeName]. This function is called for dependencies that
     * cannot be added to already existing fragments. Therefore, create a new fragment and add the [dependency] to it,
     * together with its own dependencies. Store the given [issues] for the dependency and use the given
     * [repositories] to resolve packages.
     */
    private fun insertIntoNewFragment(
        index: Int, scopeName: String,
        dependency: Dependency,
        repositories: List<RemoteRepository>,
        issues: List<OrtIssue>,
        transitive: Boolean
    ): DependencyReference {
        val fragmentMapping = mutableMapOf<Int, DependencyReference>()
        val dependencyIndex = RootDependencyIndex(index, referenceMappings.size)
        referenceMappings += fragmentMapping
        return insertIntoGraph(dependencyIndex, scopeName, dependency, repositories, issues, transitive)
    }

    /**
     * Insert the [dependency] with the given [RootDependencyIndex][index], which belongs to the scope with the given
     * [scopeName] and may be [transitive] into the dependency graph. Insert the dependencies of this [dependency]
     * recursively and use the [repositories] to resolve packages. Create a new [DependencyReference] for the
     * dependency and initialize it with the list of [issues].
     */
    private fun insertIntoGraph(
        index: RootDependencyIndex,
        scopeName: String,
        dependency: Dependency,
        repositories: List<RemoteRepository>,
        issues: List<OrtIssue>,
        transitive: Boolean
    ): DependencyReference {
        val transitiveDependencies = dependency.dependencies.map {
            addDependencyToGraph(scopeName, it, repositories, transitive = true)
        }

        val fragmentMapping = referenceMappings[index.fragment]
        val ref = DependencyReference(
            pkg = index.root,
            fragment = index.fragment,
            dependencies = transitiveDependencies.toSortedSet(),
            linkage = dependency.linkage(),
            issues = issues
        )

        fragmentMapping[index.root] = ref
        return updateDirectDependencies(ref, transitive)
    }

    /**
     * Return a list of issues that is initially populated with errors or warnings from the given [dependency].
     */
    private fun issuesForDependency(dependency: Dependency): MutableList<OrtIssue> {
        val issues = mutableListOf<OrtIssue>()

        dependency.error?.let {
            issues += createAndLogIssue(
                source = managerName,
                message = it,
                severity = Severity.ERROR
            )
        }

        dependency.warning?.let {
            issues += createAndLogIssue(
                source = managerName,
                message = it,
                severity = Severity.WARNING
            )
        }

        return issues
    }

    /**
     * Construct a [Package] for the given [dependency] using the [repositories] provided. Add the new package to the
     * set managed by this object. If this fails, record a corresponding message in [issues].
     */
    private fun updateResolvedPackages(
        identifier: String,
        dependency: Dependency,
        repositories: List<RemoteRepository>,
        issues: MutableList<OrtIssue>
    ) {
        // Only look for a package if there was no error resolving the dependency and it is no project dependency.
        if (dependency.error != null || dependency.isProjectDependency()) return

        val pkg = try {
            val artifact = DefaultArtifact(
                dependency.groupId, dependency.artifactId, dependency.classifier,
                dependency.extension, dependency.version
            )

            maven.parsePackage(artifact, repositories)
        } catch (e: ProjectBuildingException) {
            e.showStackTrace()

            issues += createAndLogIssue(
                source = managerName,
                message = "Could not get package information for dependency '$identifier': " +
                        e.collectMessagesAsString()
            )

            Package.EMPTY.copy(
                id = Identifier(
                    type = "Maven",
                    namespace = dependency.groupId,
                    name = dependency.artifactId,
                    version = dependency.version
                )
            )
        }

        resolvedPackages += pkg
    }

    /**
     * Add the given [dependency reference][ref] to the set of direct dependencies if it is not [transitive]. If one of
     * the direct dependencies of this package is in this set, it is removed, as it is obviously no direct dependency.
     * Because this function is called for all dependencies, all transitive dependencies are eventually removed.
     */
    private fun updateDirectDependencies(ref: DependencyReference, transitive: Boolean): DependencyReference {
        directDependencies.removeAll(ref.dependencies)
        if (!transitive) directDependencies += ref
        return ref
    }

    /**
     * Update the scope mapping for the given [scopeName] to depend on [ref], which may be a [transitive] dependency.
     * The scope mapping records all the direct dependencies of scopes.
     */
    private fun updateScopeMapping(
        scopeName: String, ref: DependencyReference, transitive: Boolean
    ): DependencyReference {
        if (!transitive) {
            val index = RootDependencyIndex(ref.pkg, ref.fragment)
            scopeMapping.compute(scopeName) { _, ids ->
                ids?.let { it + index } ?: listOf(index)
            }
        }

        return ref
    }

    /**
     * Generate a string that uniquely identifies this [dependency]. This string is also used to construct an
     * [Identifier] for this package.
     */
    private fun identifierFor(dependency: Dependency): String =
        "${dependencyType(dependency)}:${dependency.groupId}:${dependency.artifactId}:${dependency.version}"

    /**
     * Determine the type of the given [dependency]. This manager implementation uses Maven to resolve packages, so
     * the type of dependencies to packages is typically _Maven_ unless no pom is available. Only for module
     * dependencies, the type of this manager is used.
     */
    private fun dependencyType(dependency: Dependency): String =
        if (dependency.isProjectDependency()) {
            managerName
        } else {
            dependency.pomFile?.let { "Maven" } ?: "Unknown"
        }
}

/**
 * Determine the [PackageLinkage] for this [Dependency].
 */
private fun Dependency.linkage() =
    if (isProjectDependency()) {
        PackageLinkage.PROJECT_DYNAMIC
    } else {
        PackageLinkage.DYNAMIC
    }

/**
 * Return a flag whether this dependency references another project in the current build.
 */
private fun Dependency.isProjectDependency() = localPath != null
