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

package org.ossreviewtoolkit.model

import com.fasterxml.jackson.annotation.JsonInclude

import java.lang.IllegalStateException
import java.util.SortedSet

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
 * To further reduce memory consumption, package identifiers are not repeated, but are listed once and then referenced
 * by indices. The storage format defined by this class is not really human-readable, but it causes a significant
 * reduction of memory usage. On deserialization, the information stored can be manually converted again to a structure
 * with [Scope] and [PackageReference] objects (with shared references, so keeping memory consumption low, too), so
 * that it can be processed in the usual ways.
 */
data class DependencyGraph(
    /**
     * A list with the identifiers of the packages that appear in the dependency graph. This list is used to resolve
     * the numeric indices contained in the [DependencyReference] objects.
     */
    val packages: List<Identifier>,

    /**
     * Stores the dependency graph as a list of root nodes for the direct dependencies referenced by scopes. Starting
     * with these nodes, the whole graph can be traversed. The nodes are constructed from the direct dependencies
     * declared by scopes that cannot be reached via other paths in the dependency graph.
     */
    val scopeRoots: SortedSet<DependencyReference>,

    /**
     * A mapping from scope names to the direct dependencies of the scopes. Based on this information, the set of
     * [Scope]s of a project can be constructed from the serialized form.
     */
    val scopes: Map<String, List<RootDependencyIndex>>
) {
    companion object {
        /**
         * A comparator for [DependencyReference] objects. Note that the concrete order does not really matter, it
         * just has to be well-defined.
         */
        val DEPENDENCY_REFERENCE_COMPARATOR = compareBy<DependencyReference> { it.pkg }.thenBy { it.fragment }

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

        scopeRoots.forEach { tree ->
            constructReferenceTree(tree, refMapping)
        }

        return refMapping
    }

    /**
     * Construct the tree with [PackageReference]s by navigating the dependency tree starting with [ref] and
     * populate the given [refMapping].
     */
    private fun constructReferenceTree(
        ref: DependencyReference,
        refMapping: MutableMap<String, PackageReference>
    ): PackageReference {
        val indexKey = RootDependencyIndex.generateKey(ref.pkg, ref.fragment)
        return refMapping.getOrPut(indexKey) {
            val dependencies = ref.dependencies.mapTo(sortedSetOf()) {
                constructReferenceTree(it, refMapping)
            }

            PackageReference(
                id = packages[ref.pkg],
                dependencies = dependencies,
                linkage = ref.linkage,
                issues = ref.issues
            )
        }
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
