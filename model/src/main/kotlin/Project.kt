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

package org.ossreviewtoolkit.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

import java.util.SortedSet

import org.ossreviewtoolkit.utils.ort.DeclaredLicenseProcessor
import org.ossreviewtoolkit.utils.ort.ProcessedDeclaredLicense
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.SpdxOperator

/**
 * A class describing a software project. A [Project] is very similar to a [Package] but contains some additional
 * metadata like e.g. the [homepageUrl]. Most importantly, it defines the dependency scopes that refer to the actual
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
     * An optional additional identifier in [CPE syntax](https://cpe.mitre.org/specification/).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val cpe: String? = null,

    /**
     * The path to the definition file of this project, relative to the root of the repository described in [vcs]
     * and [vcsProcessed].
     */
    val definitionFilePath: String,

    /**
     * The list of authors declared for this project.
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    val authors: SortedSet<String> = sortedSetOf(),

    /**
     * The list of licenses the authors have declared for this project. This does not necessarily correspond to the
     * licenses as detected by a scanner. Both need to be taken into account for any conclusions.
     */
    val declaredLicenses: SortedSet<String>,

    /**
     * The declared licenses as [SpdxExpression]. If [declaredLicenses] contains multiple licenses they are
     * concatenated with [SpdxOperator.AND].
     */
    val declaredLicensesProcessed: ProcessedDeclaredLicense = DeclaredLicenseProcessor.process(declaredLicenses),

    /**
     * Original VCS-related information as defined in the [Project]'s metadata.
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
     * Contains dependency information as a set of scope names in case a shared [DependencyGraph] is used. The scopes
     * of this project and their dependencies can then be constructed as the corresponding sub graph of the shared
     * graph.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val scopeNames: SortedSet<String>? = null
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

    init {
        require(scopeDependencies == null || scopeNames == null) {
            "Not both 'scopeDependencies' and 'scopeNames' may be set, as otherwise it is ambiguous which one " +
                    "to use."
        }
    }

    /**
     * The dependency scopes defined by this project. This property provides access to scope-related information, no
     * matter whether this information has been initialized directly or has been encoded in a [DependencyGraph].
     */
    @get:JsonIgnore
    val scopes by lazy { scopeDependencies ?: sortedSetOf() }

    /**
     * Return a [Project] instance that has its scope information directly available, resolved from the given [graph].
     * This function can be used to create a fully initialized [Project] if dependency information is available in a
     * shared [DependencyGraph]. In this case, the set with [Scope]s is constructed as a subset of the provided shared
     * graph. Otherwise, result is this same object.
     */
    fun withResolvedScopes(graph: DependencyGraph?): Project =
        takeUnless { graph != null && scopeNames != null }
            ?: copy(
                scopeDependencies = graph!!.createScopes(qualifiedScopeNames()),
                scopeNames = null
            )

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

    /**
     * Return a set with scope names that are qualified by this project's identifier. This is necessary when
     * extracting the scopes of this project from a shared dependency graph.
     */
    private fun qualifiedScopeNames(): Set<String> =
        scopeNames.orEmpty().map { DependencyGraph.qualifyScope(this, it) }.toSet()
}
