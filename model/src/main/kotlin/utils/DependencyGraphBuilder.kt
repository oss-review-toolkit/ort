/*
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

package org.ossreviewtoolkit.model.utils

import java.util.LinkedList
import java.util.SortedSet

import org.ossreviewtoolkit.model.DependencyGraph
import org.ossreviewtoolkit.model.DependencyGraphEdge
import org.ossreviewtoolkit.model.DependencyGraphNode
import org.ossreviewtoolkit.model.DependencyReference
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.RootDependencyIndex
import org.ossreviewtoolkit.utils.core.log

/**
 * Internal class to represent the result of a search in the dependency graph. The outcome of the search
 * determines how to integrate a specific dependency into the dependency graph.
 */
private sealed class DependencyGraphSearchResult {
    /**
     * A specialized [DependencyGraphSearchResult] that indicates that the dependency that was searched for is already
     * present in the dependency graph. This is the easiest case, as the [DependencyReference] that was found
     * can directly be reused.
     */
    data class Found(
        /** The reference to the dependency that was searched in the graph. */
        val ref: DependencyReference
    ) : DependencyGraphSearchResult()

    /**
     * A specialized [DependencyGraphSearchResult] that indicates that the dependency that was searched for was not
     * found in the dependency graph, but a fragment has been detected, to which it can be added.
     */
    data class NotFound(
        /** The index of the fragment to which to add the dependency. */
        val fragmentIndex: Int
    ) : DependencyGraphSearchResult()

    /**
     * A specialized [DependencyGraphSearchResult] that indicates that the dependency that was searched for in its
     * current form cannot be added to any of the existing fragments. This means that either there are no
     * fragments yet or each fragment contains a variant of the dependency with an incompatible dependency
     * tree. In this case, a new fragment has to be added to the graph to host this special variant of this
     * dependency.
     */
    object Incompatible : DependencyGraphSearchResult()
}

/**
 * A class that can construct a [DependencyGraph] from a set of dependencies that provides an efficient storage format
 * for large dependency sets in many scopes.
 *
 * For larger projects the network of transitive dependencies tends to become complex. Single packages can occur many
 * times in this structure if they are referenced by multiple scopes or are fundamental libraries, on which many other
 * packages depend. A naive implementation, which simply duplicates the dependency trees for each scope and package
 * therefore leads to a high consumption of memory. This class addresses this problem by generating an optimized
 * structure that shares the components of the dependency graph between the scopes as far as possible.
 *
 * This builder class provides the _addDependency()_ function, which has to be called for all the direct dependencies
 * of the different scopes. From these dependencies it constructs a single, optimized graph that is referenced by all
 * scopes. To reduce the amount of memory required even further, package identifiers are replaced by numeric indices,
 * so that references in the graph are just numbers.
 *
 * Ideally, the resulting dependency graph contains each dependency exactly once. There are, however, cases, in which
 * packages occur multiple times in the project's dependency graph with different dependencies, for instance if
 * exclusions for transitive dependencies are used or a version resolution mechanism comes into play. In such cases,
 * the corresponding packages need to form different nodes in the graph, so that they can be distinguished, and for
 * packages depending on them, it must be ensured that the correct node is referenced. In the terminology of this class
 * this is referred to as "fragmentation": A fragment is a consistent sub graph, in which each package occurs only
 * once. Packages appearing multiple times with different dependencies need to be placed in separate fragments. It is
 * then possible to uniquely identify a specific package by a combination of its numeric identifier and the index of
 * the fragment it belongs to.
 *
 * This class implements the full logic to construct a [DependencyGraph], independent on the concrete representation of
 * dependencies [D] used by specific package managers. To make this class compatible with such a dependency
 * representation, the package manager implementation has to provide a [DependencyHandler]. Via this handler, all the
 * relevant information about dependencies can be extracted.
 */
class DependencyGraphBuilder<D>(
    /**
     * The [DependencyHandler] used by this builder instance to extract information from the dependency objects when
     * constructing the [DependencyGraph].
     */
    private val dependencyHandler: DependencyHandler<D>
) {
    /**
     * A list storing the identifiers of all dependencies added to this builder. This list is then used to resolve
     * dependencies based on their indices.
     */
    private val dependencyIds = mutableListOf<Identifier>()

    /**
     * A set storing the identifiers of all package dependencies with no issues. This is used to check whether there
     * are references to packages not contained in this builder's list of packages.
     */
    private val validPackageDependencies = mutableSetOf<Identifier>()

    /**
     * A mapping of the identifiers of the dependencies known to this builder to their numeric indices.
     */
    private val dependencyIndexMapping = mutableMapOf<Identifier, Int>()

    /**
     * Stores all the references to dependencies that have been added so far. Each element of the list represents a
     * fragment of the dependency graph. For each fragment, there is a mapping from a dependency index to the
     * reference pointing to the corresponding dependency tree.
     */
    private val referenceMappings = mutableListOf<MutableMap<Int, DependencyReference>>()

    /** The mapping from scopes to dependencies constructed by this builder. */
    private val scopeMapping = mutableMapOf<String, List<RootDependencyIndex>>()

    /** Stores all packages encountered in the dependency tree associated by their ID. */
    private val resolvedPackages = mutableMapOf<Identifier, Package>()

    /**
     * A set storing the packages that are direct dependencies of one of the scopes. These are the entry points into
     * the dependency graph.
     */
    private val directDependencies = mutableSetOf<DependencyReference>()

    /**
     * Add the given [dependency] for the scope with the given [scopeName] to this builder. This function needs to be
     * called all the direct dependencies of all scopes. That way the builder gets sufficient information to construct
     * the [DependencyGraph].
     */
    fun addDependency(scopeName: String, dependency: D): DependencyGraphBuilder<D> =
        apply { addDependencyToGraph(scopeName, dependency, transitive = false) }

    /**
     * Add the given [packages] to this builder. They are stored internally and also returned when querying the set of
     * known packages. This function can be used by package managers that have to deal with packages not part of
     * normal scope dependencies. One example would be Yarn; here packages can be defined in the workspace and are not
     * necessarily referenced by manifest files.
     */
    fun addPackages(packages: Collection<Package>): DependencyGraphBuilder<D> =
        apply { resolvedPackages += packages.associateBy { it.id } }

    /**
     * Construct the [DependencyGraph] from the dependencies passed to this builder so far. If [checkReferences] is
     * *true*, check whether all dependency references used in the graph point to packages managed by this builder.
     * This check is enabled by default and should be done for all package manager implementations. Only for special
     * cases, e.g. the conversion from the dependency tree to the dependency graph format, it needs to be disabled as
     * the conditions do not hold then.
     */
    fun build(checkReferences: Boolean = true): DependencyGraph {
        if (checkReferences) checkReferences()

        val (sortedDependencyIds, indexMapping) = constructSortedDependencyIds(dependencyIds)
        val (nodes, edges) = directDependencies.toGraph(indexMapping)

        return DependencyGraph(
            sortedDependencyIds,
            sortedSetOf(),
            constructSortedScopeMappings(scopeMapping, indexMapping),
            nodes,
            edges.removeCycles()
        )
    }

    private fun Collection<DependencyGraphEdge>.removeCycles(): List<DependencyGraphEdge> {
        val edges = mapTo(mutableSetOf()) { it.from to it.to }
        val edgesToKeep = breakCycles(edges)
        val edgesToRemove = edges - edgesToKeep

        edgesToRemove.forEach {
            this@DependencyGraphBuilder.log.warn { "Removing edge '${it.first} -> ${it.second}' to break a cycle." }
        }

        return filter { it.from to it.to in edgesToKeep }
    }

    private fun checkReferences() {
        require(resolvedPackages.keys.containsAll(validPackageDependencies)) {
            "The following references do not actually refer to packages: " +
                    "${validPackageDependencies - resolvedPackages.keys}."
        }

        val packageReferencesKeysWithMultipleDistinctPackageReferences = directDependencies.groupBy { it.key }.filter {
            it.value.distinct().size > 1
        }.keys

        require(packageReferencesKeysWithMultipleDistinctPackageReferences.isEmpty()) {
            "Found multiple distinct package references for each of the following package / fragment index tuples " +
                "${packageReferencesKeysWithMultipleDistinctPackageReferences.joinToString()}."
        }
    }

    /**
     * Return a set with all the packages that have been encountered for the current project.
     */
    fun packages(): Set<Package> = resolvedPackages.values.toSet()

    /**
     * Return a set of all the scope names known to this builder that start with the given [prefix]. If [unqualify] is
     * *true*, remove this prefix from the returned scope names.
     */
    fun scopesFor(prefix: String, unqualify: Boolean = true): SortedSet<String> {
        val qualifiedScopes = scopeMapping.keys.filter { it.startsWith(prefix) }
        val scopes = qualifiedScopes.takeUnless { unqualify } ?: qualifiedScopes.map { it.substring(prefix.length) }
        return scopes.toSortedSet()
    }

    /**
     * Return a set of all the scope names known to this builder that are qualified with the given [projectId]. If
     * [unqualify] is *true*, remove the project qualifier from the returned scope names. As dependency graphs are
     * shared between multiple projects, scope names are given a project-specific prefix to make them unique. Using
     * this function, the scope names of a specific project can be retrieved.
     */
    fun scopesFor(projectId: Identifier, unqualify: Boolean = true): SortedSet<String> =
        scopesFor(DependencyGraph.qualifyScope(projectId, ""), unqualify)

    /**
     * Update the dependency graph by adding the given [dependency], which may be [transitive], for the scope with name
     * [scopeName]. All the dependencies of this dependency are processed recursively.
     */
    private fun addDependencyToGraph(scopeName: String, dependency: D, transitive: Boolean): DependencyReference {
        val id = dependencyHandler.identifierFor(dependency)
        val issues = dependencyHandler.issuesForDependency(dependency).toMutableList()
        val index = updateDependencyMappingAndPackages(id, dependency, issues)

        val ref = when (val result = findDependencyInGraph(index, dependency)) {
            is DependencyGraphSearchResult.Found -> result.ref

            is DependencyGraphSearchResult.NotFound -> {
                insertIntoGraph(
                    id,
                    RootDependencyIndex(index, result.fragmentIndex),
                    scopeName,
                    dependency,
                    issues,
                    transitive
                )
            }

            is DependencyGraphSearchResult.Incompatible ->
                insertIntoNewFragment(id, index, scopeName, dependency, issues, transitive)
        }

        return updateScopeMapping(scopeName, ref, transitive)
    }

    /**
     * Update internal state for the given dependency [id]. Check whether this ID is already known. If not, add it
     * to the data managed by this instance, resolve the package, and update the [issues] if necessary. Return the
     * numeric index for this dependency.
     */
    private fun updateDependencyMappingAndPackages(id: Identifier, dependency: D, issues: MutableList<OrtIssue>): Int {
        val dependencyIndex = dependencyIndexMapping[id]
        if (dependencyIndex != null) return dependencyIndex

        updateResolvedPackages(id, dependency, issues)

        return dependencyIds.size.also {
            dependencyIds += id
            dependencyIndexMapping[id] = it
        }
    }

    /**
     * Search for the [dependency] with the given [index] in the fragments of the dependency graph. Return a
     * [DependencyGraphSearchResult] that indicates how to proceed with this dependency.
     */
    private fun findDependencyInGraph(index: Int, dependency: D): DependencyGraphSearchResult {
        val mappingForCompatibleFragment = referenceMappings.find { mapping ->
            mapping[index]?.takeIf { dependencyTreeEquals(it, dependency) } != null
        }

        val compatibleReference = mappingForCompatibleFragment?.let { it[index] }

        return compatibleReference?.let { DependencyGraphSearchResult.Found(it) }
            ?: handleNoCompatibleDependencyInGraph(index)
    }

    /**
     * Determine how to deal with the dependency with the given [index], for which no compatible variant was found
     * in the dependency graph. Try to find a fragment, in which the dependency can be inserted. If this fails, a new
     * fragment has to be added.
     */
    private fun handleNoCompatibleDependencyInGraph(index: Int): DependencyGraphSearchResult {
        val mappingToInsert = referenceMappings.withIndex().find { index !in it.value }

        return mappingToInsert?.let { DependencyGraphSearchResult.NotFound(it.index) }
            ?: DependencyGraphSearchResult.Incompatible
    }

    /**
     * Check whether the dependency tree spawned by [dependency] matches the one [ref] points to. Using this function,
     * packages are identified that occur multiple times in the dependency graph with different sets of dependencies;
     * these have to be placed in separate fragments of the dependency graph.
     */
    private fun dependencyTreeEquals(ref: DependencyReference, dependency: D): Boolean {
        val dependencies = dependencyHandler.dependenciesFor(dependency)
        if (ref.dependencies.size != dependencies.size) return false

        val dependencies1 = ref.dependencies.map { dependencyIds[it.pkg] }
        val dependencies2 = dependencies.associateBy { dependencyHandler.identifierFor(it) }
        if (!dependencies2.keys.containsAll(dependencies1)) return false

        return ref.dependencies.all { refDep ->
            dependencies2[dependencyIds[refDep.pkg]]?.let { dependencyTreeEquals(refDep, it) } ?: false
        }
    }

    /**
     * Add a new fragment to the dependency graph for the [dependency] with the given [id] and [index], which may be
     * [transitive] and belongs to the scope with the given [scopeName]. This function is called for dependencies that
     * cannot be added to already existing fragments. Therefore, create a new fragment and add the [dependency] to it,
     * together with its own dependencies. Store the given [issues] for the dependency.
     */
    private fun insertIntoNewFragment(
        id: Identifier,
        index: Int,
        scopeName: String,
        dependency: D,
        issues: List<OrtIssue>,
        transitive: Boolean
    ): DependencyReference {
        val fragmentMapping = mutableMapOf<Int, DependencyReference>()
        val dependencyIndex = RootDependencyIndex(index, referenceMappings.size)
        referenceMappings += fragmentMapping

        return insertIntoGraph(id, dependencyIndex, scopeName, dependency, issues, transitive)
    }

    /**
     * Insert the [dependency] with the given [id] and [RootDependencyIndex][index], which belongs to the scope with
     * the given [scopeName] and may be [transitive] into the dependency graph. Insert the dependencies of this
     * [dependency] recursively. Create a new [DependencyReference] for the dependency and initialize it with the list
     * of [issues].
     */
    private fun insertIntoGraph(
        id: Identifier,
        index: RootDependencyIndex,
        scopeName: String,
        dependency: D,
        issues: List<OrtIssue>,
        transitive: Boolean
    ): DependencyReference {
        val transitiveDependencies = dependencyHandler.dependenciesFor(dependency).map {
            addDependencyToGraph(scopeName, it, transitive = true)
        }

        val fragmentMapping = referenceMappings[index.fragment]
        val ref = DependencyReference(
            pkg = index.root,
            fragment = index.fragment,
            dependencies = transitiveDependencies.toSortedSet(),
            linkage = dependencyHandler.linkageFor(dependency),
            issues = issues
        )
        fragmentMapping[index.root] = ref

        if (ref.issues.isEmpty() && ref.linkage !in PackageLinkage.PROJECT_LINKAGE) {
            validPackageDependencies += id
        }

        return updateDirectDependencies(ref, transitive)
    }

    /**
     * Construct a [Package] for the given [id] that corresponds to the given [dependency]. If the package is already
     * available, nothing has to be done. Otherwise, create a new one and add it to the set managed by this object. If
     * this fails, record a corresponding message in [issues].
     */
    private fun updateResolvedPackages(id: Identifier, dependency: D, issues: MutableList<OrtIssue>) {
        resolvedPackages.compute(id) { _, pkg -> pkg ?: dependencyHandler.createPackage(dependency, issues) }
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
}

/**
 * Convert the direct dependency references of all projects to a list of nodes and edges that represent the final
 * dependency graph. Apply the given [indexMapping] to the indices pointing to dependencies.
 */
private fun Collection<DependencyReference>.toGraph(
    indexMapping: IntArray
): Pair<List<DependencyGraphNode>, List<DependencyGraphEdge>> {
    val nodes = mutableSetOf<DependencyGraphNode>()
    val edges = mutableListOf<DependencyGraphEdge>()
    val nodeIndices = mutableMapOf<NodeKey, Int>()

    fun getOrAddNodeIndex(ref: DependencyReference): Int =
        nodeIndices.getOrPut(ref.key) {
            nodes += DependencyGraphNode(indexMapping[ref.pkg], ref.fragment, ref.linkage, ref.issues)
            nodes.size - 1
        }

    visitEach { fromRef ->
        val fromNodeIndex = getOrAddNodeIndex(fromRef)

        fromRef.dependencies.forEach { toRef ->
            val toNodeIndex = getOrAddNodeIndex(toRef)
            edges += DependencyGraphEdge(fromNodeIndex, toNodeIndex)
        }
    }

    return nodes.toList() to edges
}

private fun Collection<DependencyReference>.visitEach(visit: (ref: DependencyReference) -> Unit) {
    val visited = mutableSetOf<NodeKey>()
    val queue = LinkedList(this)

    while (queue.isNotEmpty()) {
        val ref = queue.removeFirst()

        if (ref.key !in visited) {
            visit(ref)
            visited += ref.key
            queue += ref.dependencies
        }
    }
}

private data class NodeKey(
    val pkg: Int,
    val fragment: Int
)

private val DependencyReference.key: NodeKey
    get() = NodeKey(pkg, fragment)

private enum class NodeColor { WHITE, GRAY, BLACK }

/**
 * A depth-first-search (DFS)-based implementation which breaks all cycles in O(V + E).
 * Finding a minimal solution is NP-complete.
 */
internal fun breakCycles(edges: Collection<Pair<Int, Int>>): Set<Pair<Int, Int>> {
    val outgoingEdgesForNodes = edges.groupBy({ it.first }, { it.second }).mapValues { it.value.toMutableSet() }
    val color = outgoingEdgesForNodes.keys.associateWithTo(mutableMapOf()) { NodeColor.WHITE }

    fun visit(u: Int) {
        color[u] = NodeColor.GRAY

        val nodesClosingCircle = mutableSetOf<Int>()

        outgoingEdgesForNodes[u].orEmpty().forEach { v ->
            if (color[v] == NodeColor.WHITE) {
                visit(v)
            } else if (color[v] == NodeColor.GRAY) {
                nodesClosingCircle += v
            }
        }

        outgoingEdgesForNodes[u]?.removeAll(nodesClosingCircle)

        color[u] = NodeColor.BLACK
    }

    val queue = LinkedList(outgoingEdgesForNodes.keys)

    while (queue.isNotEmpty()) {
        val v = queue.removeFirst()

        if (color.getValue(v) != NodeColor.WHITE) continue

        visit(v)
    }

    return outgoingEdgesForNodes.flatMapTo(mutableSetOf()) { (fromNode, toNodes) ->
        toNodes.map { toNode -> fromNode to toNode }
    }
}

/**
 * Sort the list of [identifiers][ids] for the known dependencies and generate a mapping, so that all index-based
 * references can be adjusted accordingly. The latter is just an array that contains at index i the new index for the
 * identifier that was originally at index i.
 */
private fun constructSortedDependencyIds(ids: Collection<Identifier>): Pair<List<Identifier>, IntArray> {
    val sortedIds = ids.withIndex().sortedBy { it.value.toCoordinates() }
    val indexMapping = IntArray(sortedIds.size)

    var currentIndex = 0
    sortedIds.forEach { indexMapping[it.index] = currentIndex++ }

    return sortedIds.map { it.value } to indexMapping
}

/** A Comparator for ordering [RootDependencyIndex] instances. */
private val rootDependencyIndexComparator = compareBy<RootDependencyIndex>({ it.root }, { it.fragment })

/**
 * Sort the given [scopeMappings] by scope names, and the lists of dependencies per scope by their package indices.
 * Also apply the given [indexMapping] to the dependency indices.
 */
private fun constructSortedScopeMappings(
    scopeMappings: Map<String, List<RootDependencyIndex>>,
    indexMapping: IntArray
): Map<String, List<RootDependencyIndex>> {
    val orderedMappings = mutableMapOf<String, List<RootDependencyIndex>>()

    scopeMappings.keys.toSortedSet().forEach { scope ->
        orderedMappings[scope] = scopeMappings.getValue(scope)
            .map { it.mapIndex(indexMapping) }
            .sortedWith(rootDependencyIndexComparator)
    }

    return orderedMappings
}

/**
 * Apply the given [indexMapping] to this [RootDependencyIndex]. If there is a change, return a new instance with an
 * updated index.
 */
private fun RootDependencyIndex.mapIndex(indexMapping: IntArray): RootDependencyIndex =
    takeIf { indexMapping[root] == root } ?: copy(root = indexMapping[root])
