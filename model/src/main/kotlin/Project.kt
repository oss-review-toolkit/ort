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

package org.ossreviewtoolkit.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

import java.util.SortedSet

import org.ossreviewtoolkit.spdx.SpdxExpression
import org.ossreviewtoolkit.spdx.SpdxOperator
import org.ossreviewtoolkit.utils.DeclaredLicenseProcessor
import org.ossreviewtoolkit.utils.ProcessedDeclaredLicense

/**
 * A class describing a software project. A [Project] is very similar to a [Package] but contains some additional
 * meta-data like e.g. the [homepageUrl]. Most importantly, it defines the dependency scopes that refer to the actual
 * packages.
 */
@JsonIgnoreProperties(value = ["aliases", "purl"])
data class Project(
    /**
     * The unique identifier of this project. The [id]'s type is the name of the package manager that manages this
     * project (e.g. "Gradle" for a Gradle project).
     */
    val id: Identifier,

    /**
     * The path to the definition file of this project, relative to the root of the repository described in [vcs]
     * and [vcsProcessed].
     */
    val definitionFilePath: String,

    /**
     * The list of authors declared for this package.
     *
     * TODO: The annotation can be removed after all package manager implementations have filled the field [authors]
     *       accordingly.
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    val authors: SortedSet<String> = sortedSetOf(),

    /**
     * The list of licenses the authors have declared for this package. This does not necessarily correspond to the
     * licenses as detected by a scanner. Both need to be taken into account for any conclusions.
     */
    val declaredLicenses: SortedSet<String>,

    /**
     * The declared licenses as [SpdxExpression]. If [declaredLicenses] contains multiple licenses they are
     * concatenated with [SpdxOperator.AND].
     */
    val declaredLicensesProcessed: ProcessedDeclaredLicense = DeclaredLicenseProcessor.process(declaredLicenses),

    /**
     * Original VCS-related information as defined in the [Project]'s meta-data.
     */
    val vcs: VcsInfo,

    /**
     * Processed VCS-related information about the [Project] that has e.g. common mistakes corrected.
     */
    val vcsProcessed: VcsInfo = vcs.normalize(),

    /**
     * The URL to the project's homepage.
     */
    val homepageUrl: String,

    /**
     * Holds information about the scopes and their dependencies of this project if no [DependencyGraph] is available.
     * NOTE: Do not use this property to access scope information. Use [scopes] instead, which is correctly initialized
     * in all cases.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("scopes")
    val scopeDependencies: SortedSet<Scope>? = null,

    /**
     * Contains dependency information as a [DependencyGraph]. This is an alternative format to store the dependencies
     * referenced by the various scopes. Use the [scopes] property to access dependency information independent on
     * the concrete representation.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val dependencyGraph: DependencyGraph? = null
) : Comparable<Project> {
    companion object {
        /**
         * A constant for a [Project] where all properties are empty.
         */
        @JvmField
        val EMPTY = Project(
            id = Identifier.EMPTY,
            definitionFilePath = "",
            authors = sortedSetOf(),
            declaredLicenses = sortedSetOf(),
            declaredLicensesProcessed = ProcessedDeclaredLicense.EMPTY,
            vcs = VcsInfo.EMPTY,
            vcsProcessed = VcsInfo.EMPTY,
            homepageUrl = "",
            scopeDependencies = sortedSetOf()
        )
    }

    /**
     * The dependency scopes defined by this project. This property provides access to scope-related information, no
     * matter whether this information has been initialized directly or has been encoded in a [DependencyGraph].
     */
    @get:JsonIgnore
    val scopes by lazy {
        dependencyGraph?.createScopes() ?: scopeDependencies ?: sortedSetOf()
    }

    /**
     * Return a [Project] instance that has its scope information directly available. A project can be constructed
     * either with a set of [Scope] objects or with a [DependencyGraph]. In the latter case, the graph has to be
     * converted first into the scope representation. This function ensures that this step was done: If the project
     * has a [DependencyGraph], it returns a new instance with the converted scope information (and the dependency
     * graph removed to save memory); otherwise, it returns this same object.
     */
    fun withResolvedScopes(): Project =
        takeUnless { dependencyGraph != null } ?: copy(scopeDependencies = scopes, dependencyGraph = null)

    /**
     * Return the set of package [Identifier]s of all transitive dependencies of this [Project], up to and including a
     * depth of [maxDepth] where counting starts at 0 (for the [Project] itself) and 1 are direct dependencies etc. A
     * value below 0 means to not limit the depth. If the given [filterPredicate] is false for a specific
     * [PackageReference] the corresponding [Identifier] is excluded from the result.
     */
    fun collectDependencies(
        maxDepth: Int = -1,
        filterPredicate: (PackageReference) -> Boolean = { true }
    ): Set<Identifier> =
        scopes.fold(mutableSetOf()) { refs, scope ->
            refs.also { it += scope.collectDependencies(maxDepth, filterPredicate) }
        }

    /**
     * Return a map of all de-duplicated [OrtIssue]s associated by [Identifier].
     */
    fun collectIssues(): Map<Identifier, Set<OrtIssue>> {
        val collectedIssues = mutableMapOf<Identifier, MutableSet<OrtIssue>>()

        fun addIssues(pkgRef: PackageReference) {
            if (pkgRef.issues.isNotEmpty()) {
                collectedIssues.getOrPut(pkgRef.id) { mutableSetOf() } += pkgRef.issues
            }

            pkgRef.dependencies.forEach { addIssues(it) }
        }

        for (scope in scopes) {
            for (dependency in scope.dependencies) {
                addIssues(dependency)
            }
        }

        return collectedIssues
    }

    /**
     * Return the set of [Identifier]s that refer to sub-projects of this [Project].
     */
    fun collectSubProjects(): SortedSet<Identifier> =
        scopes.fold(sortedSetOf()) { refs, scope ->
            refs.also {
                it += scope.collectDependencies { ref -> ref.linkage in PackageLinkage.PROJECT_LINKAGE }
            }
        }

    /**
     * A comparison function to sort projects by their identifier.
     */
    override fun compareTo(other: Project) = id.compareTo(other.id)

    /**
     * Return whether the package identified by [id] is contained as a (transitive) dependency in this project.
     */
    operator fun contains(id: Identifier) = scopes.any { id in it }

    /**
     * Return all references to [id] as a dependency in this project.
     */
    fun findReferences(id: Identifier) = scopes.flatMap { it.findReferences(id) }

    /**
     * Return a [Package] representation of this [Project].
     */
    fun toPackage() =
        Package(
            id = id,
            authors = authors,
            declaredLicenses = declaredLicenses,
            description = "",
            homepageUrl = homepageUrl,
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = RemoteArtifact.EMPTY,
            vcs = vcs,
            vcsProcessed = vcsProcessed
        )
}
