/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

package com.here.ort.reporter.model

import com.fasterxml.jackson.annotation.JsonIdentityInfo
import com.fasterxml.jackson.annotation.JsonIdentityReference
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.databind.introspect.ObjectIdInfo
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

import com.here.ort.model.CustomData
import com.here.ort.model.Identifier
import com.here.ort.model.LicenseSource
import com.here.ort.model.OrtIssue
import com.here.ort.model.OrtResult
import com.here.ort.model.PROPERTY_NAMING_STRATEGY
import com.here.ort.model.PackageCurationResult
import com.here.ort.model.PackageLinkage
import com.here.ort.model.Provenance
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.RuleViolation
import com.here.ort.model.ScannerDetails
import com.here.ort.model.Severity
import com.here.ort.model.VcsInfo
import com.here.ort.model.config.IssueResolution
import com.here.ort.model.config.LicenseFindingCuration
import com.here.ort.model.config.PathExclude
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.model.config.RuleViolationResolution
import com.here.ort.model.config.ScopeExclude
import com.here.ort.reporter.Reporter
import com.here.ort.reporter.ReporterInput
import com.here.ort.reporter.reporters.WebAppReporter
import com.here.ort.reporter.utils.IntIdModule
import com.here.ort.reporter.utils.ZeroBasedIntSequenceGenerator
import com.here.ort.spdx.SpdxExpression

import java.io.Writer
import java.time.Instant
import java.util.SortedMap
import java.util.SortedSet

/**
 * The [EvaluatedModel] represents the outcome of the evaluation of a [ReporterInput]. This means that all additional
 * information contained in the [ReporterInput] is applied to the [OrtResult]:
 *
 * * [PathExclude]s and [ScopeExclude]s from the [RepositoryConfiguration] are applied.
 * * [IssueResolution]s from the [ReporterInput.resolutionProvider] are matched against all [OrtIssue]s contained in the
 *   result.
 * * [RuleViolationResolution]s from the [ReporterInput.resolutionProvider] are matched against all [RuleViolation]s.
 *
 * The current implementation is missing these features:
 *
 * * [LicenseFindingCuration]s are not yet applied to the model.
 *
 * The model also contains useful containers to easily access some content of the [OrtResult], for example a list of
 * all [OrtIssue]s in their [evaluated form][EvaluatedOrtIssue] which contains back-references to its source.
 *
 * The model can be serialized to with the helper functions [toJson] and [toYaml]. It uses a special [JsonMapper] that
 * de-duplicates objects in the result. For this it uses Jackson's [JsonIdentityInfo] to automatically generate [Int]
 * IDs for the objects. All objects for which the model contains containers, like [issues] or [packages], are serialized
 * only once in those containers. All other references to those objects are replaced by the [Int] IDs. This is required
 * because the model contains cyclic dependencies between objects which would otherwise cause stack overflows during
 * serialization, and it also reduces the size of the result file.
 *
 * Use cases for the [EvaluatedModel] are:
 *
 * * Input for the [WebAppReporter], so that it does not have to evaluate the model at runtime. Currently the model is
 *   optimized for this use case.
 * * Input for [Reporter] implementations, so that they do not have to repeatedly implement the application of excludes,
 *   resolutions, and so on.
 * * Input for external tools, so that they do not have to re-implement the logic for evaluating the model.
 *
 * Important notes for working with this model:
 *
 * * The model uses Kotlin data classes with cyclic dependencies, therefore the [hashCode] and [toString] of affected
 *   classes cannot be used, because they would create stack overflows.
 * * When modifying the model make sure that the objects are serialized at the right place. By default Jackson
 *   serializes an Object with [ObjectIdInfo] the first time the serializer sees the object. If this is not desired
 *   because the object shall be serialized as the generated ID, the [JsonIdentityReference] annotation can be used to
 *   enforce this. For example, the list of [EvaluatedOrtIssue]s is serialized before the list of [EvaluatedPackage]s.
 *   Therefore [EvaluatedOrtIssue.pkg] is annotated with [JsonIdentityReference].
 */
data class EvaluatedModel(
    val pathExcludes: List<PathExclude>,
    val scopeExcludes: List<ScopeExclude>,
    val copyrights: List<CopyrightStatement>,
    val licenses: List<LicenseId>,
    val scopes: List<ScopeName>,
    val issueResolutions: List<IssueResolution>,
    val issues: List<EvaluatedOrtIssue>,
    val scanResults: List<EvaluatedScanResult>,
    val packages: List<EvaluatedPackage>,
    val paths: List<EvaluatedPackagePath>,
    val dependencyTrees: List<DependencyTreeNode>,
    val ruleViolationResolutions: List<RuleViolationResolution>,
    val ruleViolations: List<EvaluatedRuleViolation>,
    val declaredLicenseStats: SortedMap<String, Int>,
    val detectedLicenseStats: SortedMap<String, Int>,
    val statistics: Statistics,
    // TODO: Ideally this would be an instance of RepositoryConfiguration, but for now it has to be a string to not be
    //       converted to JSON when using it as input for the web app reporter.
    val repositoryConfiguration: String,
    val customData: CustomData
) {
    companion object {
        private val INT_ID_TYPES = listOf(
            CopyrightStatement::class.java,
            EvaluatedOrtIssue::class.java,
            EvaluatedPackage::class.java,
            EvaluatedPackagePath::class.java,
            EvaluatedRuleViolation::class.java,
            EvaluatedScanResult::class.java,
            IssueResolution::class.java,
            LicenseId::class.java,
            PathExclude::class.java,
            RuleViolationResolution::class.java,
            ScopeName::class.java,
            ScopeExclude::class.java
        )

        private val MAPPER_CONFIG: ObjectMapper.() -> Unit = {
            registerKotlinModule()

            registerModule(JavaTimeModule())
            registerModule(IntIdModule(INT_ID_TYPES))

            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

            propertyNamingStrategy = PROPERTY_NAMING_STRATEGY
        }

        private val JSON_MAPPER = JsonMapper().apply(MAPPER_CONFIG)
        private val YAML_MAPPER = YAMLMapper().apply(MAPPER_CONFIG)

        fun create(input: ReporterInput): EvaluatedModel = EvaluatedModelMapper(input).build()
    }

    fun toJson(writer: Writer): Unit = JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValue(writer, toSortedTree())

    fun toYaml(writer: Writer): Unit = YAML_MAPPER.writeValue(writer, toSortedTree())

    /**
     * Sort all collections by the generated IDs. This ensures that the IDs match the array index of the elements.
     * The web-app-reporter relies on this for fast lookup of object references.
     */
    private fun toSortedTree(): JsonNode {
        val tree = JSON_MAPPER.valueToTree<ObjectNode>(this)
        tree.forEach { node ->
            if (node is ArrayNode) {
                if (!node.isEmpty && node[0].has("_id")) {
                    val sortedChildren =
                        node.elements().asSequence().toList().sortedBy { it["_id"].intValue() }.toList()
                    node.removeAll()
                    node.addAll(sortedChildren)
                }
            }
        }
        return tree
    }
}

data class EvaluatedPackage(
    val id: Identifier,
    val isProject: Boolean,
    val definitionFilePath: String,
    val purl: String = id.toPurl(),
    val declaredLicenses: List<LicenseId>,
    val declaredLicensesProcessed: EvaluatedProcessedDeclaredLicense,
    val detectedLicenses: Set<LicenseId>,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val concludedLicense: SpdxExpression? = null,
    val description: String,
    val homepageUrl: String,
    val binaryArtifact: RemoteArtifact,
    val sourceArtifact: RemoteArtifact,
    val vcs: VcsInfo,
    val vcsProcessed: VcsInfo = vcs.normalize(),
    val curations: List<PackageCurationResult>,
    @JsonIdentityReference(alwaysAsId = true)
    val paths: MutableList<EvaluatedPackagePath>,
    val levels: SortedSet<Int>,
    val scopes: MutableSet<ScopeName>,
    val scanResults: List<EvaluatedScanResult>,
    val findings: List<EvaluatedFinding>,
    val isExcluded: Boolean,
    val pathExcludes: List<PathExclude>,
    val scopeExcludes: List<ScopeExclude>,
    val issues: List<EvaluatedOrtIssue>
)

data class EvaluatedPackagePath(
    val pkg: EvaluatedPackage,
    val project: EvaluatedPackage,
    val scope: ScopeName,
    val path: List<EvaluatedPackage>
)

data class EvaluatedScanResult(
    val provenance: Provenance,
    val scanner: ScannerDetails,
    val startTime: Instant,
    val endTime: Instant,
    val fileCount: Int,
    val packageVerificationCode: String,
    val issues: List<EvaluatedOrtIssue>
)

enum class EvaluatedFindingType {
    COPYRIGHT, LICENSE
}

data class EvaluatedFinding(
    val type: EvaluatedFindingType,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val license: LicenseId?,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val copyright: CopyrightStatement?,
    val path: String,
    val startLine: Int,
    val endLine: Int,
    val scanResult: EvaluatedScanResult
)

/**
 * Wrapper class for copyright statements. Allows Jackson to generate IDs for them when storing them in a separate list
 * for de-duplication.
 */
data class CopyrightStatement(
    val statement: String
)

/**
 * Wrapper class for license identifiers. Allows Jackson to generate IDs for them when storing them in a separate list
 * for de-duplication.
 */
data class LicenseId(
    val id: String
)

/**
 * Wrapper class for scope names. Allows Jackson to generate IDs for them when storing them in a separate list for
 * de-duplication.
 */
data class ScopeName(
    val name: String
)

data class EvaluatedProcessedDeclaredLicense(
    val spdxExpression: SpdxExpression?,
    val mappedLicenses: List<LicenseId>,
    val unmappedLicenses: List<LicenseId>
)

enum class EvaluatedOrtIssueType {
    ANALYZER, SCANNER
}

@JsonIgnoreProperties(value = ["pkg", "scanResult", "path"], allowGetters = true)
data class EvaluatedOrtIssue(
    val timestamp: Instant,
    val type: EvaluatedOrtIssueType,
    val source: String,
    val message: String,
    val severity: Severity = Severity.ERROR,
    val resolutions: List<IssueResolution>,
    @JsonIdentityReference(alwaysAsId = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val pkg: EvaluatedPackage?,
    @JsonIdentityReference(alwaysAsId = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val scanResult: EvaluatedScanResult?,
    @JsonIdentityReference(alwaysAsId = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val path: EvaluatedPackagePath?
)

data class EvaluatedRuleViolation(
    val rule: String,
    val pkg: EvaluatedPackage,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val license: LicenseId?,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val licenseSource: LicenseSource?,
    val severity: Severity,
    val message: String,
    val howToFix: String,
    val resolutions: List<RuleViolationResolution>
)

@JsonIdentityInfo(property = "key", generator = ZeroBasedIntSequenceGenerator::class, scope = DependencyTreeNode::class)
data class DependencyTreeNode(
    val title: String,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val linkage: PackageLinkage?,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val pkg: EvaluatedPackage?,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val pathExcludes: List<PathExclude> = emptyList(),
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val scopeExcludes: List<ScopeExclude> = emptyList(),
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val issues: List<EvaluatedOrtIssue> = emptyList(),
    val children: List<DependencyTreeNode>
)
