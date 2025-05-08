/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.advisors.osv

import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import java.time.Instant

import org.ossreviewtoolkit.advisor.normalizeVulnerabilityData
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.utils.test.identifierToPackage
import org.ossreviewtoolkit.utils.test.readResourceValue

class OsvFunTest : WordSpec({
    "retrievePackageFindings()" should {
        "return the vulnerabilities for the supported ecosystems" {
            val osv = createOsv()
            val packages = setOf(
                "Crate::sys-info:0.7.0",
                "Composer:thorsten:phpmyfaq:3.0.7",
                "Gem::rack:2.0.4",
                "Go::github.com/nats-io/nats-server/v2:2.1.0",
                "Hackage::xml-conduit:0.5.0",
                "Maven:com.jfinal:jfinal:1.4",
                "NPM::rebber:1.0.0",
                "NuGet::Bunkum:4.0.0",
                "Pub::http:0.13.1",
                "PyPI::django:3.2",
                "Swift::github.com/apple/swift-nio:2.41.0"
            ).mapTo(mutableSetOf()) {
                identifierToPackage(it)
            }

            val packageFindings = osv.retrievePackageFindings(packages).mapKeys { it.key.id.toCoordinates() }

            packageFindings.keys shouldContainExactlyInAnyOrder packages.map { it.id.toCoordinates() }
            packageFindings.keys.forAll { coordinates ->
                packageFindings.getValue(coordinates).vulnerabilities shouldNot beEmpty()
            }
        }

        "return the expected result for the given package(s)" {
            val expectedResult = readResourceValue<Map<Identifier, AdvisorResult>>(
                "/retrieve-package-findings-expected-result.json"
            )

            val osv = createOsv()
            // The following packages have been chosen because they have only one vulnerability with the oldest possible
            // modified date from the current OSV database, in order to hopefully minimize the flakiness.
            val packages = setOf(
                // Package with severity:
                "NPM::find-my-way:3.0.0",
                // Package without severity, but with severity inside the databaseSpecific JSON object:
                "NPM::discord-markdown:2.3.0",
                // Package without severity:
                "PyPI::donfig:0.2.0"
            ).mapTo(mutableSetOf()) {
                identifierToPackage(it)
            }

            val packageFindings = osv.retrievePackageFindings(packages).mapKeys { it.key.id }

            packageFindings.patchTimes() shouldBe expectedResult.patchTimes()
        }
    }
})

private fun createOsv(): Osv = OsvFactory.create()

private fun Map<Identifier, AdvisorResult>.patchTimes(): Map<Identifier, AdvisorResult> =
    mapValues { (_, advisorResult) ->
        advisorResult.normalizeVulnerabilityData().copy(
            summary = advisorResult.summary.copy(
                startTime = Instant.EPOCH,
                endTime = Instant.EPOCH
            )
        )
    }
