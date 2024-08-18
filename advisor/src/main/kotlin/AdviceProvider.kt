/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.advisor

import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.plugins.api.Plugin

/**
 * An abstract class that represents a service that can retrieve any kind of advice information
 * for a list of given [Package]s. Examples of such information can be security vulnerabilities, known defects,
 * or code analysis results.
 */
interface AdviceProvider : Plugin {
    /**
     * For a given set of [Package]s, retrieve findings and return a map that associates packages with [AdvisorResult]s.
     */
    suspend fun retrievePackageFindings(packages: Set<Package>): Map<Package, AdvisorResult>

    /**
     * An object with detail information about this [AdviceProvider].
     */
    val details: AdvisorDetails
}
