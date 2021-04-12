/*
 * Copyright (C) 2020 Bosch.IO GmbH
 * Copyright (C) 2021 HERE Europe B.V.
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

package org.ossreviewtoolkit.advisor

import java.io.File
import java.time.Instant
import java.util.ServiceLoader

import kotlin.time.measureTimedValue

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

import org.ossreviewtoolkit.model.AdvisorRecord
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorRun
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.utils.Environment
import org.ossreviewtoolkit.utils.formatSizeInMib
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.perf

/**
 * The class to manage [VulnerabilityProvider]s that retrieve security advisories.
 */
class Advisor(
    private val providerFactories: List<VulnerabilityProviderFactory>,
    private val config: AdvisorConfiguration
) {
    companion object {
        private val LOADER = ServiceLoader.load(VulnerabilityProviderFactory::class.java)!!

        /**
         * The list of all available [VulnerabilityProvider]s in the classpath.
         */
        val ALL by lazy { LOADER.iterator().asSequence().toList() }
    }

    fun retrieveVulnerabilityInformation(ortFile: File, skipExcluded: Boolean = false): OrtResult {
        val startTime = Instant.now()

        val (ortResult, duration) = measureTimedValue { ortFile.readValue<OrtResult>() }

        log.perf {
            "Read ORT result from '${ortFile.name}' (${ortFile.formatSizeInMib}) in ${duration.inMilliseconds}ms."
        }

        if (ortResult.analyzer == null) {
            log.warn {
                "Cannot run the advisor as the provided ORT result file '${ortFile.canonicalPath}' does not contain " +
                        "an analyzer result. No result will be added."
            }

            return ortResult
        }

        val providers = providerFactories.map { it.create(config) }

        val results = sortedMapOf<Identifier, List<AdvisorResult>>()

        val packages = ortResult.getPackages(skipExcluded).map { it.pkg }

        runBlocking {
            providers.map { provider ->
                async {
                    provider.retrievePackageVulnerabilities(packages)
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

        val advisorRun = AdvisorRun(startTime, endTime, Environment(), config, advisorRecord)
        return ortResult.copy(advisor = advisorRun)
    }
}
