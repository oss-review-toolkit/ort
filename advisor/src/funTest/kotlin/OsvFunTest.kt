/*
 * Copyright (C) 2022 EPAM Systems, Inc.
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

import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import java.io.File
import java.time.Instant

import org.ossreviewtoolkit.advisor.advisors.Osv
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.model.readValue

class OsvFunTest : StringSpec({
    "retrievePackageFindings() returns vulnerabilities for the supported ecosystems" {
        val osv = createOsv()
        val packages = listOf(
            "Crate::sys-info:0.7.0",
            "Gem::rack:2.0.4",
            "Go::github.com/nats-io/nats-server/v2:2.1.0",
            "Maven:com.jfinal:jfinal:1.4",
            "NPM::rebber:1.0.0",
            "NuGet::Microsoft.ChakraCore:1.10.0",
            "PyPI::Plone:3.2"
            // TODO: Add an identifier for a composer package after https://github.com/google/osv.dev/issues/497 got
            //       fixed. That issue causes queries for vulnerabilities of the "Packagist" ecosystem to always return
            //       an empty result.
        ).map { identifierToPackage(it) }

        val packageFindings = osv.retrievePackageFindings(packages)

        packageFindings.keys shouldContainExactlyInAnyOrder packages
        packageFindings.forAll { (_, advisorResult) ->
            advisorResult shouldNot beEmpty()
        }
    }

    "retrievePackageFindings() returns the expected result for the given package(s)" {
        val expectedResult = File("src/funTest/assets").resolve("retrieve-package-findings-expected-result.json")
            .readValue<Map<Identifier, List<AdvisorResult>>>()
        val osv = createOsv()
        // The following packages have been chosen because they have only one vulnerability with the oldest possible
        // modified date from the current OSV database, in order to hopefully minimize the flakiness.
        val packages = listOf(
            // Package with severity:
            "NPM::find-my-way:3.0.0",
            // Package without severity, but with severity inside the databaseSpecific JSON object:
            "NPM::discord-markdown:2.3.0",
            // Package without severity:
            "PyPI::donfig:0.2.0"
        ).map { identifierToPackage(it) }

        val packageFindings = osv.retrievePackageFindings(packages).mapKeys { it.key.id }

        packageFindings.patchTimes() shouldBe expectedResult.patchTimes()
    }
})

private fun identifierToPackage(id: String): Package =
    Package.EMPTY.copy(id = Identifier(id))

private fun createOsv(): Osv =
    Osv("OSV", AdvisorConfiguration())

private fun Map<Identifier, List<AdvisorResult>>.patchTimes(): Map<Identifier, List<AdvisorResult>> =
    mapValues { (_, advisorResults) ->
        advisorResults.map { advisorResult ->
            advisorResult.copy(
                summary = advisorResult.summary.copy(
                    startTime = Instant.MIN,
                    endTime = Instant.MIN
                )
            )
        }
    }
