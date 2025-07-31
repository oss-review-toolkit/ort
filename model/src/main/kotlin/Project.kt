/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonSerialize

import org.ossreviewtoolkit.model.utils.ScopeSortedSetConverter
import org.ossreviewtoolkit.utils.ort.DeclaredLicenseProcessor
import org.ossreviewtoolkit.utils.ort.ProcessedDeclaredLicense
import org.ossreviewtoolkit.utils.ort.StringSortedSetConverter
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.SpdxOperator

/**
 * A class describing a software project. A [Project] is very similar to a [Package] but contains some additional
 * metadata like e.g. the [homepageUrl]. Most importantly, it defines the dependency scopes that refer to the actual
 * packages.
 */
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
     * The set of authors declared for this project.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonSerialize(converter = StringSortedSetConverter::class)
    val authors: Set<String> = emptySet(),

    /**
     * The set of licenses declared for this project. This does not necessarily correspond to the licenses as detected
     * by a scanner. Both need to be taken into account for any conclusions.
     */
    @JsonSerialize(converter = StringSortedSetConverter::class)
    val declaredLicenses: Set<String>,

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
     * The description of project.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val description: String = "",

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
    @JsonSerialize(converter = ScopeSortedSetConverter::class)
    val scopeDependencies: Set<Scope>? = null,

    /**
     * Contains dependency information as a set of scope names in case a shared [DependencyGraph] is used. The scopes
     * of this project and their dependencies can then be constructed as the corresponding sub graph of the shared
     * graph.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonSerialize(converter = StringSortedSetConverter::class)
    val scopeNames: Set<String>? = null
) {
    companion object {
        /**
         * A constant for a [Project] where all properties are empty.
         */
        @JvmField
        val EMPTY = Project(
            id = Identifier.EMPTY,
            definitionFilePath = "",
            authors = emptySet(),
            declaredLicenses = emptySet(),
            declaredLicensesProcessed = ProcessedDeclaredLicense.EMPTY,
            vcs = VcsInfo.EMPTY,
            vcsProcessed = VcsInfo.EMPTY,
            homepageUrl = "",
            scopeDependencies = emptySet()
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
    val scopes: Set<Scope> by lazy { scopeDependencies.orEmpty() }

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
            description = description,
            homepageUrl = homepageUrl,
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = RemoteArtifact.EMPTY,
            vcs = vcs,
            vcsProcessed = vcsProcessed
        )
}
