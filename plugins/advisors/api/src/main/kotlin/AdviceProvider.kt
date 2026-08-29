/*
 * Copyright (C) 2020 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.advisors.api

import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.plugins.api.Plugin

/**
 * An abstract class that represents a service that can retrieve security vulnerabilities for a list of given
 * [Package]s.
 */
interface AdviceProvider : Plugin {
    /**
     * For a given set of [packages], retrieve findings and return a map of only those packages that actually have
     * findings associated with an [AdvisorResult].
     */
    suspend fun retrievePackageFindings(packages: Set<Package>): Map<Package, AdvisorResult>

    /**
     * An object with detail information about this [AdviceProvider].
     */
    val details: AdvisorDetails
}
