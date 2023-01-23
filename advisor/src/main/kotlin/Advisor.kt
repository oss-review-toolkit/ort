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

import java.time.Instant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.model.AdvisorRecord
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorRun
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.utils.common.Plugin
import org.ossreviewtoolkit.utils.ort.Environment

/**
 * The class to manage [AdviceProvider]s. It invokes the configured providers and adds their findings to the current
 * [OrtResult].
 */
class Advisor(
    private val providerFactories: List<AdviceProviderFactory>,
    private val config: AdvisorConfiguration
) {
    companion object : Logging {
        /**
         * All [advice provider factories][AdviceProviderFactory] available in the classpath, associated by their names.
         */
        val ALL by lazy { Plugin.getAll<AdviceProviderFactory>() }
    }

    /**
     * Query the [advice providers][providerFactories] and add the result to the provided [ortResult]. Excluded packages
     * can optionally be [skipped][skipExcluded].
     */
    suspend fun advise(ortResult: OrtResult, skipExcluded: Boolean = false): OrtResult {
        if (ortResult.analyzer == null) {
            logger.warn {
                "Cannot run the advisor as the provided ORT result does not contain an analyzer result. " +
                        "No result will be added."
            }

            return ortResult
        }

        val packages = ortResult.getPackages(skipExcluded).mapTo(mutableSetOf()) { it.metadata }
        val advisorRun = advise(packages)
        return ortResult.copy(advisor = advisorRun)
    }

    /**
     * Query the [advice providers][providerFactories] for the provided [packages].
     */
    suspend fun advise(packages: Set<Package>): AdvisorRun =
        withContext(Dispatchers.IO) {
            val startTime = Instant.now()

            val results = sortedMapOf<Identifier, List<AdvisorResult>>()

            if (packages.isEmpty()) {
                logger.info { "There are no packages to give advice for." }
            } else {
                val providers = providerFactories.map { it.create(config) }

                providers.map { provider ->
                    async {
                        val providerResults = provider.retrievePackageFindings(packages)

                        logger.info {
                            "Found ${providerResults.values.flatten().distinct().size} distinct vulnerabilities via " +
                                    "${provider.providerName}. "
                        }

                        providerResults.filter { it.value.isNotEmpty() }.keys.takeIf { it.isNotEmpty() }?.let { pkgs ->
                            logger.debug {
                                "Affected packages:\n\n${pkgs.joinToString("\n") { it.id.toCoordinates() }}\n"
                            }
                        }

                        providerResults
                    }
                }.forEach { providerResults ->
                    providerResults.await().forEach { (pkg, advisorResults) ->
                        results.merge(pkg.id, advisorResults) { oldResults, newResults ->
                            oldResults + newResults
                        }
                    }
                }
            }

            val advisorRecord = AdvisorRecord(results)

            val endTime = Instant.now()

            AdvisorRun(startTime, endTime, Environment(), config, advisorRecord)
        }
}
