/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

import kotlinx.coroutines.runBlocking

import org.ossreviewtoolkit.model.AdvisorRecord
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorResultContainer
import org.ossreviewtoolkit.model.AdvisorRun
import org.ossreviewtoolkit.model.Environment
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.model.readValue

/**
 * The class to retrieve security advisories.
 */
abstract class Advisor(val advisorName: String, protected val config: AdvisorConfiguration) {
    companion object {
        private val LOADER = ServiceLoader.load(AdvisorFactory::class.java)!!

        /**
         * The list of all available advisors in the classpath
         */
        val ALL by lazy { LOADER.iterator().asSequence().toList() }
    }

    fun retrieveVulnerabilityInformation(
        ortResultFile: File,
        skipExcluded: Boolean = false
    ): OrtResult {
        require(ortResultFile.isFile) {
            "The provided ORT result file '${ortResultFile.canonicalPath}' does not exist."
        }

        val startTime = Instant.now()

        val ortResult = ortResultFile.readValue<OrtResult>()

        requireNotNull(ortResult.analyzer) {
            "The provided ORT result file '${ortResultFile.canonicalPath}' does not contain an analyzer result."
        }

        val packages = ortResult.getPackages(skipExcluded).map { it.pkg }

        val results = runBlocking { retrievePackageVulnerabilities(packages) }
        val resultContainers = results.map { (pkg, results) ->
            AdvisorResultContainer(pkg.id, results)
        }.toSortedSet()
        val advisorRecord = AdvisorRecord(resultContainers)

        val endTime = Instant.now()

        val advisorRun = AdvisorRun(startTime, endTime, Environment(), config, advisorRecord)

        return ortResult.copy(advisor = advisorRun)
    }

    protected abstract suspend fun retrievePackageVulnerabilities(
        packages: List<Package>
    ): Map<Package, List<AdvisorResult>>
}
