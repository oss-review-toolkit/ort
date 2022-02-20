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

package org.ossreviewtoolkit.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude

import java.lang.IllegalStateException
import java.util.SortedSet

/**
 * Type alias for a [Map] that associates a [DependencyGraphNode] with the nodes representing its dependencies.
 */
typealias NodeDependencies = Map<DependencyGraphNode, List<DependencyGraphNode>>

/**
 * A data class that represents the graph of dependencies of a project.
 *
 * This class holds information about a project's scopes and their dependencies in a format that minimizes the
 * consumption of memory. In projects with many scopes there is often a high degree of duplication in the
 * dependencies of the scopes. To avoid this, this class aims to share as many parts of the dependency graph as
 * possible between the different scopes. Ideally, there is only a single dependency graph containing the dependencies
 * used by all scopes. This is not always possibles due to inconsistencies in dependency relations, like a package
 * using different dependencies in different scopes. Then the dependency graph is split into multiple fragments, and
 * each fragment has a consistent view on the dependencies it contains.
 *
 * When constructing a dependency graph the dependencies are organized as a connected structure of
 * [DependencyReference] objects in memory. Originally, the serialization format of a graph was based on this
 * structure, but that turned out to be not ideal: During serialization, sub graphs referenced from multiple nodes
 * (e.g. libraries with transitive dependencies referenced from multiple projects) get duplicated, which can cause a
 * significant amount of redundancy. Therefore, the data representation has been changed again to a form, which can be
 * serialized without introducing redundancy. It consists of the following elements:
 *
 * - *packages*: A list with the coordinates of all the packages (free of duplication) that are referenced by the
 *   graph. This allows extracting the packages directly, but also has the advantage that the package coordinates do
 *   not have to be repeated over and over: All the references to packages are expressed by indices into this list.
 * - *nodes*: An ordered list with the nodes of the dependency graph. A single node represents a package, and
 *   therefore has a reference into the list with package coordinates. It can, however, happen that packages occur
 *   multiple times in the graph if they are in different sub trees with different sets of transitive dependencies.
 *   Then there are multiple nodes for the packages affected, and a *fragmentIndex* is used to identify them uniquely.
 *   Nodes also store information about issues of a package and their linkage.
 * - *edges*: Here the structure of the graph comes in. Each edge connects two nodes and represents a directed
 *   *depends-on* relationship. The nodes are referenced by numeric indices into the list of *nodes*.
 * - *scopes*: This is a map that associates the scopes used by projects with their direct dependencies. A single
 *   dependency graph contains the dependencies of all the projects processed by a specific package manager.
 *   Therefore, the keys of this map are scope names qualified by the coordinates of a project; which makes them
 *   unique. The values are references to the nodes in the graph that correspond to the packages the scopes depend on
 *   directly.
 *
 * So to navigate this structure, start with a *scope* and gather the references to its direct dependency *nodes*.
 * Then, by following the *edges* starting from these *nodes*, the set of transitive dependencies can be determined.
 * The numeric indices can be resolved via the *packages* list.
 */
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
data class DependencyGraph(
    /**
     * A list with the identifiers of the packages that appear in the dependency graph. This list is used to resolve
     * the numeric indices contained in the [DependencyGraphNode] objects.
     */
    val packages: List<Identifier> = emptyList(),

    /**
     * Stores the dependency graph as a list of root nodes for the direct dependencies referenced by scopes. Starting
     * with these nodes, the whole graph can be traversed. The nodes are constructed from the direct dependencies
     * declared by scopes that cannot be reached via other paths in the dependency graph. Note that this property
     * exists for backwards compatibility only; it is replaced by the lists of nodes and edges.
     */
    val scopeRoots: SortedSet<DependencyReference> = sortedSetOf(),

    /**
     * A mapping from scope names to the direct dependencies of the scopes. Based on this information, the set of
     * [Scope]s of a project can be constructed from the serialized form.
     */
    val scopes: Map<String, List<RootDependencyIndex>> = emptyMap(),

    /**
     * A list with the nodes of this dependency graph. Nodes correspond to packages, but in contrast to the [packages]
     * list, there can be multiple nodes for a single package. The order of nodes in this list is relevant; the
     * edges of the graph reference their nodes by numeric indices.
     */
    val nodes: List<DependencyGraphNode>? = null,

    /**
     * A list with the edges of this dependency graph. By traversing the edges, the dependencies of packages can be
     * determined.
     */
    val edges: List<DependencyGraphEdge>? = null
) {
    companion object {
        /**
         * A comparator for [DependencyReference] objects. Note that the concrete order does not really matter, it
         * just has to be well-defined.
         */
        val DEPENDENCY_REFERENCE_COMPARATOR = compareBy<DependencyReference>({ it.pkg }, { it.fragment })

        /**
         * Return a name for the given [scope][scopeName] that is qualified with parts of the identifier of the given
         * [project]. This is used to ensure that the scope names are unique when constructing a dependency graph from
         * multiple projects.
         */
        fun qualifyScope(project: Project, scopeName: String): String =
            qualifyScope(project.id, scopeName)

        /**
         * Return a name for the given [scope][scopeName] that is qualified with parts of the given [projectId]. This
         * is used to ensure that the scope names are unique when constructing a dependency graph from multiple
         * projects.
         */
        fun qualifyScope(projectId: Identifier, scopeName: String): String =
            "${projectId.namespace}:${projectId.name}:${projectId.version}:$scopeName"

        /**
         * Extract the plain (un-qualified) scope name from the given qualified [scopeName]. If the passed in
         * [scopeName] is not qualified, return it unchanged.
         */
        fun unqualifyScope(scopeName: String): String =
            // To handle the case that the scope contains the separator character, cut off the parts for the
            // namespace, the name, and the version.
            scopeName.split(':', limit = 4).getOrElse(3) { scopeName }
    }

    /**
     * A mapping that allows fast access to the dependencies of a node in this graph.
     */
    @get:JsonIgnore
    val dependencies: NodeDependencies by lazy { constructNodeDependencies() }

    /**
     * Stores a mapping from dependency indices to [PackageReference] objects. This is needed when converting the
     * data of this object to the classical layout of dependency information. The structure is created once and then
     * used to convert parts of this graph.
     */
    private val referenceMapping: Map<String, PackageReference> by lazy { constructReferenceMapping() }

    /**
     * Transform the data stored in this object to the classical layout of dependency information, which is a set of
     * [Scope]s referencing the packages they depend on.
     */
    fun createScopes(): SortedSet<Scope> = createScopesFor(scopes, unqualify = false)

    /**
     * Transform a subset of the data stored in this object to the classical layout of dependency information. This is
     * analogous to [createScopes], but only the provided [scopeNames] are taken into account. If [unqualify] is
     * *true*, remove qualifiers from scope names before constructing the [Scope]s.
     */
    fun createScopes(scopeNames: Set<String>, unqualify: Boolean = true): SortedSet<Scope> =
        createScopesFor(scopes.filterKeys { it in scopeNames }, unqualify)

    /**
     * Convert the given [map] with scope information to a set of [Scope]s. [Optionally][unqualify] remove qualifiers
     * from scope names.
     */
    private fun createScopesFor(map: Map<String, List<RootDependencyIndex>>, unqualify: Boolean): SortedSet<Scope> =
        map.mapTo(sortedSetOf()) { entry ->
            val dependencies = entry.value.mapTo(sortedSetOf()) { index ->
                referenceMapping[index.toKey()]
                    ?: throw IllegalStateException("Could not resolve dependency index $index.")
            }

            val scopeName = if (unqualify) unqualifyScope(entry.key) else entry.key
            Scope(scopeName, dependencies)
        }

    /**
     * Construct a mapping from dependency indices to [PackageReference] objects. Based on this mapping, the
     * structure with [Scope]s can be generated.
     */
    private fun constructReferenceMapping(): Map<String, PackageReference> {
        val refMapping = mutableMapOf<String, PackageReference>()
        val allNodes = nodes ?: scopeRoots.map(DependencyReference::toGraphNode)

        allNodes.forEach { constructReferenceTree(it, refMapping) }

        return refMapping
    }

    /**
     * Construct the tree with [PackageReference]s by navigating the dependency graph starting with [node] and
     * populate the given [refMapping].
     */
    private fun constructReferenceTree(
        node: DependencyGraphNode,
        refMapping: MutableMap<String, PackageReference>
    ): PackageReference {
        val indexKey = RootDependencyIndex.generateKey(node.pkg, node.fragment)
        return refMapping.getOrPut(indexKey) {
            val refDependencies = dependencies[node].orEmpty().mapTo(sortedSetOf()) {
                constructReferenceTree(it, refMapping)
            }

            PackageReference(
                id = packages[node.pkg],
                dependencies = refDependencies,
                linkage = node.linkage,
                issues = node.issues
            )
        }
    }

    /**
     * Construct a mapping that allows fast navigation from a graph node to its dependencies.
     */
    private fun constructNodeDependencies(): NodeDependencies =
        when {
            nodes != null && edges != null -> constructNodeDependenciesFromGraph(nodes, edges)
            else -> constructNodeDependenciesFromScopeRoots(scopeRoots)
        }

    /**
     * Return a map of all de-duplicated [OrtIssue]s associated by [Identifier].
     */
    fun collectIssues(): Map<Identifier, Set<OrtIssue>> {
        val collectedIssues = mutableMapOf<Identifier, MutableSet<OrtIssue>>()

        fun addIssues(pkg: Int, issues: Collection<OrtIssue>) {
            if (issues.isNotEmpty()) {
                collectedIssues.getOrPut(packages[pkg]) { mutableSetOf() } += issues
            }
        }

        fun addIssues(ref: DependencyReference) {
            addIssues(ref.pkg, ref.issues)
            ref.dependencies.forEach { addIssues(it) }
        }

        for (ref in scopeRoots) {
            addIssues(ref)
        }

        nodes?.forEach { node ->
            addIssues(node.pkg, node.issues)
        }

        return collectedIssues
    }
}

/**
 * A data class representing the index of a root dependency of a scope.
 *
 * Instances of this class are used to reference the direct dependencies of scopes in the shared dependency graph.
 * These dependencies form the roots of the dependency trees spawned by scopes.
 */
data class RootDependencyIndex(
    /**
     * The index of the root dependency referenced by this object. Each package acting as a dependency is assigned a
     * unique index. Storing an index rather than an identifier reduces the amount of memory consumed by this
     * representation.
     */
    val root: Int,

    /**
     * The index of the fragment of the dependency graph this reference points to. This is used to distinguish between
     * packages that occur multiple times in the dependency graph with different dependencies.
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    val fragment: Int = 0
) {
    companion object {
        /**
         * Generate a string representation for the given [root] and [fragment] that is used internally as key
         * in maps.
         */
        fun generateKey(root: Int, fragment: Int): String = "$root.$fragment"
    }

    /**
     * Generate a string key to represent this index.
     */
    fun toKey(): String = generateKey(root, fragment)
}

/**
 * A class to model a tree-like structure to represent the dependencies of a project.
 *
 * Instances of this class are used to store the relations between dependencies in fragments of dependency trees in an
 * Analyzer result. The main purpose of this class is to define an efficient serialization format, which avoids
 * redundancy as far as possible. Therefore, dependencies are represented by numeric indices into an external table.
 * As a dependency can occur multiple times in the dependency graph with different transitive dependencies, the class
 * defines another index to distinguish these cases.
 *
 * Note: This is by intention no data class. Equality is tested via references and not via the values contained.
 */
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
class DependencyReference(
    /**
     * Stores the numeric index of the package dependency referenced by this object. The package behind this index can
     * be resolved by evaluating the list of identifiers stored in [DependencyGraph] at this index.
     */
    val pkg: Int,

    /**
     * Stores the index of the fragment in the dependency graph where the referenced dependency is contained. This is
     * needed to uniquely identify the target if the dependency occurs multiple times in the graph.
     */
    val fragment: Int = 0,

    /**
     * A set with the references to the dependencies of this dependency. That way a tree-like structure is established.
     */
    val dependencies: SortedSet<DependencyReference> = sortedSetOf(),

    /**
     * The type of linkage used for the referred package from its dependent package. As most of our supported
     * package managers / languages only support dynamic linking or at least default to it, also use that as the
     * default value here to not blow up our result files.
     */
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = PackageLinkageValueFilter::class)
    val linkage: PackageLinkage = PackageLinkage.DYNAMIC,

    /**
     * A list of [OrtIssue]s that occurred handling this dependency.
     */
    val issues: List<OrtIssue> = emptyList()
) : Comparable<DependencyReference> {
    /**
     * Define an order on [DependencyReference] instances. Instances are ordered by their indices and fragment indices.
     */
    override fun compareTo(other: DependencyReference): Int =
        if (pkg != other.pkg) {
            pkg - other.pkg
        } else {
            fragment - other.fragment
        }
}

/**
 * A data class representing a node in the dependency graph.
 *
 * A node corresponds to a package, which is referenced by a numeric index. A package may, however, occur multiple
 * times in the dependency graph with different transitive dependencies. In this case, different fragment indices are
 * used to distinguish between these occurrences.
 */
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
data class DependencyGraphNode(
    /**
     * Stores the numeric index of the package dependency referenced by this object. The package behind this index can
     * be resolved by evaluating the list of identifiers stored in [DependencyGraph] at this index.
     */
    val pkg: Int,

    /**
     * Stores the index of the fragment in the dependency graph where the referenced dependency is contained. This is
     * needed to uniquely identify the target if the dependency occurs multiple times in the graph.
     */
    val fragment: Int = 0,

    /**
     * The type of linkage used for the referred package from its dependent package. As most of our supported
     * package managers / languages only support dynamic linking or at least default to it, also use that as the
     * default value here to not blow up our result files.
     */
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = PackageLinkageValueFilter::class)
    val linkage: PackageLinkage = PackageLinkage.DYNAMIC,

    /**
     * A list of [OrtIssue]s that occurred handling this dependency.
     */
    val issues: List<OrtIssue> = emptyList()
)

/**
 * A data class representing an edge in the dependency graph.
 *
 * An edge corresponds to a directed depends-on relationship between two packages. The packages are identified by the
 * numeric indices into the list of nodes.
 */
data class DependencyGraphEdge(
    /** The index of the source node of this edge. */
    val from: Int,

    /** The index of the destination node of this edge. */
    val to: Int
)

/**
 * Convert this [DependencyReference] to a [DependencyGraphNode].
 */
private fun DependencyReference.toGraphNode() = DependencyGraphNode(pkg, fragment, linkage, issues)

/**
 * Construct a mapping of dependencies based on the given [roots].
 */
private fun constructNodeDependenciesFromScopeRoots(roots: SortedSet<DependencyReference>): NodeDependencies {
    val mapping = mutableMapOf<DependencyGraphNode, List<DependencyGraphNode>>()

    fun construct(refs: SortedSet<DependencyReference>) {
        refs.forEach { ref ->
            val node = ref.toGraphNode()
            if (node !in mapping) {
                mapping[node] = ref.dependencies.map(DependencyReference::toGraphNode)
                construct(ref.dependencies)
            }
        }
    }

    construct(roots)
    return mapping
}

/**
 * Construct a mapping of dependencies based on the lists of graph [nodes] and [edges].
 */
private fun constructNodeDependenciesFromGraph(
    nodes: List<DependencyGraphNode>,
    edges: List<DependencyGraphEdge>
): NodeDependencies {
    val mapping = mutableMapOf<DependencyGraphNode, MutableList<DependencyGraphNode>>()

    edges.forEach { edge ->
        val srcNode = nodes[edge.from]
        val dstNode = nodes[edge.to]
        val dependencies = mapping.getOrPut(srcNode) { mutableListOf() }
        dependencies += dstNode
    }

    // Add entries for nodes without dependencies.
    nodes.filter { it !in mapping }.forEach { mapping[it] = mutableListOf() }

    return mapping
}
