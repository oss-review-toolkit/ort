/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.advisors.blackduck

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
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.test.identifierToPackage
import org.ossreviewtoolkit.utils.test.readResourceValue

class BlackDuckFunTest : WordSpec({
    /**
     * To run the test against a real instance, and / or to re-record the responses:
     *
     * 1. Set the BLACK_DUCK_SERVER_URL and BLACK_DUCK_API_TOKEN environment variables.
     * 2. Delete 'recorded-responses.json'.
     * 3. Run the functional test.
     */
    val serverUrl = Os.env["BLACK_DUCK_SERVER_URL"]
    val apiToken = Os.env["BLACK_DUCK_API_TOKEN"]
    val componentServiceClient = ResponseCachingComponentServiceClient(
        overrideUrl = javaClass.getResource("/recorded-responses.json"),
        serverUrl = serverUrl,
        apiToken = apiToken
    )

    val blackDuck = BlackDuck(blackDuckApi = componentServiceClient)

    afterEach { componentServiceClient.flush() }

    "retrievePackageFindings()" should {
        "return the vulnerabilities for the supported ecosystems by purl" {
            val packages = setOf(
                "Crate::sys-info:0.7.0",
                "Gem::rack:2.0.4",
                "Maven:com.jfinal:jfinal:1.4",
                "NPM::rebber:1.0.0",
                "NuGet::Bunkum:4.0.0",
                "Pod::AFNetworking:0.10.0",
                "Pub::http:0.13.1",
                "PyPI::django:3.2"
            ).mapTo(mutableSetOf()) {
                identifierToPackage(it)
            }

            val packageFindings = blackDuck.retrievePackageFindings(packages).mapKeys { it.key.id.toCoordinates() }

            packageFindings.keys shouldContainExactlyInAnyOrder packages.map { it.id.toCoordinates() }
            packageFindings.keys.forAll { id ->
                packageFindings.getValue(id).vulnerabilities shouldNot beEmpty()
            }
        }

        "return the vulnerabilities for some supported namespaces by origin-id" {
            val packages = setOf(
                "Conan::libtiff:4.6.0" to "conan:libtiff/4.6.0@_/_#44d09b1f75a7fd97d4f7daea5e7aed8e:" +
                    "76d7c7f96feb2c0e80e06c3ce83fb313b77d8ef1#4e4eafa7fcd218e85d2fe31ee79c7552",
                "Git::Qt:6.5.3" to "long_tail:git://code.qt.io/qt/qt5#v6.5.3",
                "Github::behdad/harbuzz:2.2.0" to "github:behdad/harfbuzz:2.2.0",
                "NuGet::Bunkum:4.0.0" to "nuget:Bunkum/4.0.0",
                "PyPI::donfig:0.2.0" to "pypi:donfig/0.2.0"
            ).mapTo(mutableSetOf()) { (coordinates, originId) ->
                Package.EMPTY.copy(
                    id = Identifier(coordinates),
                    labels = mapOf(
                        BlackDuck.PACKAGE_LABEL_BLACK_DUCK_ORIGIN_ID to originId
                    )
                )
            }

            val packageFindings = blackDuck.retrievePackageFindings(packages).mapKeys { it.key.id.toCoordinates() }

            packageFindings.keys shouldContainExactlyInAnyOrder packages.map { it.id.toCoordinates() }
            packageFindings.keys.forAll { id ->
                packageFindings.getValue(id).vulnerabilities shouldNot beEmpty()
            }
        }

        "return the expected result for the given package(s)" {
            val expectedResult = readResourceValue<Map<Identifier, AdvisorResult>>(
                "/retrieve-package-findings-expected-result.yml"
            )

            val packages = setOf(
                // Package using CVSS 3.1 vector:
                "Crate::sys-info:0.7.0",
                // Package using CVSS 2 vector only:
                "Pod::AFNetworking:0.10.0"
            ).mapTo(mutableSetOf()) {
                identifierToPackage(it)
            }

            val packageFindings = blackDuck.retrievePackageFindings(packages).mapKeys { it.key.id }

            packageFindings.patchTimes().toYaml().patchServerUrl(serverUrl) shouldBe
                expectedResult.patchTimes().toYaml()
        }
    }
})

internal fun String.patchServerUrl(serverUrl: String?) =
    serverUrl?.let { replace(it, "https://BLACK_DUCK_SERVER_HOST") } ?: this

private fun Map<Identifier, AdvisorResult>.patchTimes(): Map<Identifier, AdvisorResult> =
    mapValues { (_, advisorResult) ->
        advisorResult.normalizeVulnerabilityData().copy(
            summary = advisorResult.summary.copy(
                startTime = Instant.EPOCH,
                endTime = Instant.EPOCH
            )
        )
    }
