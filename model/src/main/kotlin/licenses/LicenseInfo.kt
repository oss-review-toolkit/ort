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

package org.ossreviewtoolkit.model.licenses

import java.util.SortedSet

import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.PackageCurationResult
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.config.LicenseFindingCuration
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.utils.ort.ProcessedDeclaredLicense
import org.ossreviewtoolkit.utils.spdx.SpdxExpression

/**
 * This class contains all license information about a package or project.
 */
data class LicenseInfo(
    /**
     * The identifier of the package or project.
     */
    val id: Identifier,

    /**
     * Information about the declared license.
     */
    val declaredLicenseInfo: DeclaredLicenseInfo,

    /**
     * Information about the detected license.
     */
    val detectedLicenseInfo: DetectedLicenseInfo,

    /**
     * Information about the concluded license.
     */
    val concludedLicenseInfo: ConcludedLicenseInfo
)

/**
 * Information about the concluded license of a package or project.
 */
data class ConcludedLicenseInfo(
    /**
     * The concluded license, or null if no license was concluded.
     */
    val concludedLicense: SpdxExpression?,

    /**
     * The list of [PackageCurationResult]s that modified the concluded license.
     */
    val appliedCurations: List<PackageCurationResult>
)

/**
 * Information about the declared license of a package or project.
 */
data class DeclaredLicenseInfo(
    /**
     * The set of authors.
     */
    val authors: SortedSet<String>,

    /**
     * The unmodified set of declared licenses.
     */
    val licenses: Set<String>,

    /**
     * The processed declared license.
     */
    val processed: ProcessedDeclaredLicense,

    /**
     * The list of [PackageCurationResult]s that modified the declared license.
     */
    val appliedCurations: List<PackageCurationResult>
)

/**
 * Information about the detected licenses of a package or project.
 */
data class DetectedLicenseInfo(
    /**
     * The list of all [Findings].
     */
    val findings: List<Findings>
)

/**
 * A collection of [license][licenses] and [copyright][copyrights] findings detected in the source code located at
 * [provenance].
 */
data class Findings(
    /**
     * The [Provenance] of the scanned source code.
     */
    val provenance: Provenance,

    /**
     * The set of all license findings.
     */
    val licenses: Set<LicenseFinding>,

    /**
     * The set of all copyright findings.
     */
    val copyrights: Set<CopyrightFinding>,

    /**
     * The list of all license finding curations that apply to this [provenance].
     */
    val licenseFindingCurations: List<LicenseFindingCuration>,

    /**
     * The list of all path excludes that apply to this [provenance].
     */
    val pathExcludes: List<PathExclude>,

    /**
     * The root path of the locations of the [licenses] and [copyrights] relative to the paths used in the
     * [licenseFindingCurations] and [pathExcludes]. An empty string, if all refer to the same root path.
     *
     * The roots can be different in case of projects inside nested repositories (see [Repository.nestedRepositories]),
     * where the license and copyright finding locations are relative to the nested repository, but the
     * [licenseFindingCurations] and [pathExcludes] are relative to the root repository, because they are configured in
     * the [RepositoryConfiguration] of the root repository.
     */
    val relativeFindingsPath: String
)
