/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
 * Copyright (C) 2022 Bosch.IO GmbH
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

package org.ossreviewtoolkit.model.utils

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.model.config.LicenseFilenamePatterns
import org.ossreviewtoolkit.model.licenses.DefaultLicenseInfoProvider
import org.ossreviewtoolkit.model.licenses.LicenseInfoResolver

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
 * Create a [LicenseInfoResolver] for [this] [OrtResult]. If the resolver is used multiple times it should be stored
 * instead of calling this function multiple times for better performance.
 */
fun OrtResult.createLicenseInfoResolver(
    packageConfigurationProvider: PackageConfigurationProvider = PackageConfigurationProvider.EMPTY,
    copyrightGarbage: CopyrightGarbage = CopyrightGarbage(),
    addAuthorsToCopyrights: Boolean = false,
    archiver: FileArchiver? = null
) = LicenseInfoResolver(
        DefaultLicenseInfoProvider(this, packageConfigurationProvider),
        copyrightGarbage,
        addAuthorsToCopyrights,
        archiver,
        LicenseFilenamePatterns.getInstance()
    )

/**
 * Copy this [OrtResult] and add all [labels] to the existing labels, overwriting existing labels on conflict.
 */
fun OrtResult.mergeLabels(labels: Map<String, String>) = copy(labels = this.labels + labels)
