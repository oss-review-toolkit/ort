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

package org.ossreviewtoolkit.model.licenses

import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseSource
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.model.config.LicenseFindingCuration
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.spdx.SpdxSingleLicenseExpression
import org.ossreviewtoolkit.utils.CopyrightStatementsProcessor
import org.ossreviewtoolkit.utils.DeclaredLicenseProcessor

/**
 * Resolved license information about a package (or project).
 */
data class ResolvedLicenseInfo(
    /**
     * The identifier of the package (or project).
     */
    val id: Identifier,

    /**
     * The list of [ResolvedLicense]s for this package (or project).
     */
    val licenses: List<ResolvedLicense>,

    val unmatchedCopyrights: Map<Provenance, Set<CopyrightFinding>>
) : Iterable<ResolvedLicense> by licenses {
    operator fun get(license: SpdxSingleLicenseExpression): ResolvedLicense? = find { it.license == license }
}

/**
 * Resolved information for a single license.
 */
data class ResolvedLicense(
    /**
     * The license.
     */
    val license: SpdxSingleLicenseExpression,

    /**
     * The sources where this license was found.
     */
    val sources: Set<LicenseSource>,

    /**
     * The list of original declared license that were [processed][DeclaredLicenseProcessor] to this [license], or an
     * empty list, if this [license] was not modified during processing.
     */
    val originalDeclaredLicenses: Set<String>,

    /**
     * All text locations where this license was found.
     */
    val locations: Set<ResolvedLicenseLocation>
)

/**
 * A resolved text location.
 */
data class ResolvedLicenseLocation(
    /**
     * The provenance of the file.
     */
    val provenance: Provenance,

    /**
     * The path of the file.
     */
    val path: String,

    /**
     * The start line of the text location.
     */
    val startLine: Int,

    /**
     * The end line of the text location.
     */
    val endLine: Int,

    /**
     * The applied [LicenseFindingCuration], or null if none were applied.
     */
    val appliedCuration: LicenseFindingCuration?,

    /**
     * All matching [PathExclude]s matching this [path].
     */
    val matchingPathExcludes: List<PathExclude>,

    /**
     * All copyright findings associated to this license location.
     */
    val copyrights: Set<ResolvedCopyright>
)

/**
 * A resolved copyright.
 */
data class ResolvedCopyright(
    /**
     * The resolved copyright statement.
     */
    val statement: String,

    /**
     * The resolved findings for this copyright. The statements in the findings can be different to [statement] if they
     * were processed by the [CopyrightStatementsProcessor].
     */
    val findings: Set<ResolvedCopyrightFinding>,

    /**
     * True, if this [statement] is contained in the [CopyrightGarbage] used during resolution.
     */
    val isGarbage: Boolean
)

/**
 * A resolved copyright finding.
 */
data class ResolvedCopyrightFinding(
    /**
     * The copyright statement.
     */
    val statement: String,

    /**
     * The location where this copyright was found.
     */
    val location: TextLocation,

    /**
     * True, if this [statement] is contained in the [CopyrightGarbage] used during resolution.
     */
    val isGarbage: Boolean
)
