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

package org.ossreviewtoolkit.model.utils

import java.util.SortedSet

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseFindings
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.PathExclude

/**
 * Return a map of concluded licenses for each package [Identifier] that has a concluded license. Note that this
 * function only returns license identifiers, license exceptions associated to licenses using the SPDX `WITH` operator
 * are currently ignored.
 */
fun OrtResult.collectConcludedLicenses(omitExcluded: Boolean = false): Map<Identifier, List<String>> =
    getPackages(omitExcluded)
        .filter { it.pkg.concludedLicense != null }
        .associate {
            Pair(it.pkg.id, it.pkg.concludedLicense?.licenses().orEmpty())
        }

/**
 * Return a map of declared licenses for each project or package [Identifier]. Only licenses contained in the SPDX
 * expression of the processed declared license are included. Note that this function only returns license identifiers,
 * license exceptions associated to licenses using the SPDX `WITH` operator are currently ignored.
 */
fun OrtResult.collectDeclaredLicenses(omitExcluded: Boolean = false): Map<Identifier, List<String>> =
    getProjects(omitExcluded).associate {
        Pair(it.id, it.declaredLicensesProcessed.spdxExpression?.licenses().orEmpty())
    } + getPackages(omitExcluded).associate {
        Pair(it.pkg.id, it.pkg.declaredLicensesProcessed.spdxExpression?.licenses().orEmpty())
    }

/**
 * Return a map of license findings for each project or package [Identifier]. The license findings for projects are
 * mapped to a list of [PathExclude]s matching the locations where a license was found. This list is only populated
 * if all file locations are excluded. The list is empty for all dependency packages, as path excludes are only
 * applied to the projects.
 *
 * If [omitExcluded] is set to true, excluded projects / packages are omitted from the result.
 */
fun OrtResult.collectLicenseFindings(
    packageConfigurationProvider: PackageConfigurationProvider = SimplePackageConfigurationProvider(),
    omitExcluded: Boolean = false
): Map<Identifier, Map<LicenseFindings, List<PathExclude>>> =
    LicenseResolver(this, packageConfigurationProvider).collectLicenseFindings(omitExcluded)

/**
 * Return all detected licenses for the given package [id]. As projects are implicitly converted to packages before
 * scanning, the [id] may either refer to a project or to a package. If [id] is not found an empty set is returned.
 */
@Suppress("UNUSED") // This is intended to be mostly used via scripting.
fun OrtResult.getDetectedLicensesForId(
    id: Identifier,
    packageConfigurationProvider: PackageConfigurationProvider = SimplePackageConfigurationProvider()
): SortedSet<String> = LicenseResolver(this, packageConfigurationProvider).getDetectedLicensesForId(id)

/**
 * Return all detected licenses for the given package[id] along with the copyrights.
 */
@Suppress("UNUSED") // This is intended to be mostly used via scripting.
fun OrtResult.getDetectedLicensesWithCopyrights(
    id: Identifier,
    packageConfigurationProvider: PackageConfigurationProvider = SimplePackageConfigurationProvider(),
    omitExcluded: Boolean = true
): Map<String, Set<String>> =
    LicenseResolver(this, packageConfigurationProvider).getDetectedLicensesWithCopyrights(id, omitExcluded)

/**
 * Copy this [OrtResult] and add all [labels] to the existing labels, overwriting existing labels on conflict.
 */
fun OrtResult.mergeLabels(labels: Map<String, String>) =
    copy(labels = this.labels + labels).apply { data += this@mergeLabels.data }
