/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.scanner

import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageType
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.Includes
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.config.SnippetChoices
import org.ossreviewtoolkit.utils.spdx.SpdxExpression

/**
 * Additional context information that can be used by a [ScannerWrapper] to alter its behavior.
 */
data class ScanContext(
    /**
     * A map of key-value pairs, usually from [OrtResult.labels].
     */
    val labels: Map<String, String>,

    /**
     * The [type][PackageType] of the packages to scan.
     */
    val packageType: PackageType,

    /**
     * The [Excludes] of the project to scan.
     */
    val excludes: Excludes? = null,

    /**
     * The [Includes] of the project to scan.
     */
    val includes: Includes? = null,

    /**
     * The detected license mappings configured in the
     * [scanner configuration][ScannerConfiguration.detectedLicenseMapping]. Can be used by [ScannerWrapper]
     * implementations where the scanner can return arbitrary license strings which cannot be parsed as
     * [SpdxExpression]s and can therefore not be returned as a [LicenseFinding] without being mapped first. Should not
     * be used by scanners where scan results are stored, because then changes in the mapping would not be applied to
     * stored results.
     */
    val detectedLicenseMapping: Map<String, String> = emptyMap(),

    /**
     * The packages known to be covered in the context of this scan. For package scanners, this is the list of packages
     * that have the same provenance as the reference package.
     */
    val coveredPackages: List<Package> = emptyList(),

    /**
     * The [SnippetChoices] of the project to scan.
     */
    val snippetChoices: List<SnippetChoices> = emptyList()
)
