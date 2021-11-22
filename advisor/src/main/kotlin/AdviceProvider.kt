/*
 * Copyright (C) 2020-2021 Bosch.IO GmbH
 * Copyright (C) 2021 HERE Europe B.V.
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

import java.time.Instant

import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorSummary
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.utils.common.collectMessagesAsString
import org.ossreviewtoolkit.utils.core.showStackTrace

/**
 * An abstract class that represents a service that can retrieve any kind of advice information
 * for a list of given [Package]s. Examples of such information can be security vulnerabilities, known defects,
 * or code analysis results.
 */
abstract class AdviceProvider(val providerName: String) {
    /**
     * For a given list of [Package]s, retrieve  findings and return a map that associates each package with a list
     * of [AdvisorResult]s. Needs to be implemented by child classes.
     */
    abstract suspend fun retrievePackageFindings(
        packages: List<Package>
    ): Map<Package, List<AdvisorResult>>

    /**
     * An object with detail information about this [AdviceProvider].
     */
    abstract val details: AdvisorDetails

    /**
     * A generic method that creates a failed [AdvisorResult] for [Package]s if there was an issue
     * constructing the provider-specific information.
     */
    protected fun createFailedResults(
        startTime: Instant,
        packages: List<Package>,
        t: Throwable
    ): Map<Package, List<AdvisorResult>> {
        val endTime = Instant.now()

        t.showStackTrace()

        val failedResults = listOf(
            AdvisorResult(
                vulnerabilities = emptyList(),
                advisor = details,
                summary = AdvisorSummary(
                    startTime = startTime,
                    endTime = endTime,
                    issues = listOf(
                        createAndLogIssue(
                            source = providerName,
                            message = "Failed to retrieve findings from $providerName: " +
                                    t.collectMessagesAsString()
                        )
                    )
                )
            )
        )

        return packages.associateWith { failedResults }
    }
}
