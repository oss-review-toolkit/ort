/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.reporter.reporters.evaluatedmodel

import com.fasterxml.jackson.annotation.JsonIdentityReference
import com.fasterxml.jackson.annotation.JsonInclude

import java.util.SortedSet

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageCurationResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.ScopeExclude
import org.ossreviewtoolkit.utils.spdx.SpdxExpression

/**
 * The evaluated form of a [Package] used by the [EvaluatedModel].
 */
data class EvaluatedPackage(
    val id: Identifier,
    val isProject: Boolean,
    val definitionFilePath: String,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val purl: String? = null,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val declaredLicenses: List<LicenseId>,
    val declaredLicensesProcessed: EvaluatedProcessedDeclaredLicense,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val detectedLicenses: Set<LicenseId>,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val detectedExcludedLicenses: Set<LicenseId>,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val concludedLicense: SpdxExpression? = null,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val description: String,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val homepageUrl: String,
    val binaryArtifact: RemoteArtifact,
    val sourceArtifact: RemoteArtifact,
    val vcs: VcsInfo,
    val vcsProcessed: VcsInfo = vcs.normalize(),
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val curations: List<PackageCurationResult>,
    @JsonIdentityReference(alwaysAsId = true)
    val paths: MutableList<EvaluatedPackagePath>,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val levels: SortedSet<Int>,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val scopes: MutableSet<EvaluatedScope>,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val scanResults: List<EvaluatedScanResult>,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val findings: List<EvaluatedFinding>,
    val isExcluded: Boolean,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val pathExcludes: List<PathExclude>,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val scopeExcludes: List<ScopeExclude>,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val issues: List<EvaluatedOrtIssue>
)
