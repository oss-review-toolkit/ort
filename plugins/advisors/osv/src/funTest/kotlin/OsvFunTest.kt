/*
 * Copyright (C) 2022 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.vulnerabilities.Vulnerability
import org.ossreviewtoolkit.utils.test.identifierToPackage
import org.ossreviewtoolkit.utils.test.readResourceValue

class OsvFunTest : WordSpec({
    "retrievePackageFindings()" should {
        "return the vulnerabilities for the supported ecosystems" {
            val osv = createOsv()
            val dummyPackage = "A:dummy:package:1.2.3"
            val packageCoordinates = setOf(
                dummyPackage,
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
            )
            val packages = packageCoordinates.mapTo(mutableSetOf()) { identifierToPackage(it) }

            val packageFindings = osv.retrievePackageFindings(packages).mapKeys { it.key.id.toCoordinates() }

            packageFindings.keys shouldContainExactlyInAnyOrder packageCoordinates - dummyPackage
            packageFindings.keys.forAll { coordinates ->
                packageFindings.getValue(coordinates).vulnerabilities shouldNot beEmpty()
            }
        }

        "return the expected result for the given package(s)" {
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

            val packageFindings = osv.retrievePackageFindings(packages).entries.associate {
                it.key.id to it.value.vulnerabilities
            }

            val expectedResult = readResourceValue<Map<Identifier, List<Vulnerability>>>(
                "/retrieve-package-findings-expected-result.yml"
            )

            packageFindings shouldBe expectedResult
        }

        "return the vulnerabilities for the commit of Hadoop 3.3.1" {
            val osv = createOsv()
            val pkg = Package.EMPTY.copy(
                vcsProcessed = VcsInfo.EMPTY.copy(revision = "a3b9c37a397ad4188041dd80621bdeefc46885f2")
            )

            val packageFindings = osv.retrievePackageFindings(setOf(pkg)).entries.associate {
                it.key.id to it.value.vulnerabilities
            }

            val expectedResult = readResourceValue<Map<Identifier, List<Vulnerability>>>(
                "/hadoop-commit-has-expected-result.yml"
            )

            packageFindings shouldBe expectedResult
        }
    }
})

private fun createOsv(): Osv = OsvFactory.create()
