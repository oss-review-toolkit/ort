/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.advisors.scanoss

import io.kotest.core.annotation.Condition
import io.kotest.core.annotation.EnabledIf
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containAll
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import kotlin.reflect.KClass

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.plugins.api.Secret
import org.ossreviewtoolkit.utils.test.identifierToPackage

@EnabledIf(ScanOssCheck::class)
class ScanOssFunTest : WordSpec({
    val apiKey = checkNotNull(ScanOssCheck.getApiKey())
    val scanoss = ScanOssFactory.create(apiKey = Secret(apiKey))

    "retrievePackageFindings()" should {
        "return the vulnerabilities for the supported ecosystems" {
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

            val packageFindings = scanoss.retrievePackageFindings(packages).mapKeys { it.key.id.toCoordinates() }

            packageFindings.keys shouldContainExactlyInAnyOrder packageCoordinates - dummyPackage
            packageFindings.keys.forAll { coordinates ->
                with(packageFindings.getValue(coordinates)) {
                    vulnerabilities shouldNot beEmpty()
                    summary.issues should beEmpty()
                }
            }
        }

        "return findings for Guava" {
            val id = Identifier("Maven:com.google.guava:guava:19.0")
            val pkg = Package.EMPTY.copy(id = id, purl = id.toPurl())

            val results = scanoss.retrievePackageFindings(setOf(pkg)).values

            results.flatMap { it.summary.issues } should beEmpty()
            with(results.flatMap { it.vulnerabilities }.associateBy { it.id }) {
                keys should containAll(
                    "CVE-2018-10237",
                    "CVE-2020-8908",
                    "CVE-2023-2976"
                )

                val vulnerability = getValue("CVE-2023-2976")
                vulnerability.summary shouldBe "Use of Java's default temporary directory for file creation in " +
                    "`FileBackedOutputStream` in Google Gu..."

                vulnerability.references.find {
                    it.url.toString() == "https://nvd.nist.gov/vuln/detail/CVE-2023-2976"
                } shouldNotBeNull {
                    scoringSystem should beNull()
                    severity shouldBe "MEDIUM"
                    score should beNull()
                    vector should beNull()
                }
            }
        }

        "return findings for Elliptic" {
            val id = Identifier("NPM::elliptic:6.5.7")
            val pkg = Package.EMPTY.copy(id = id, purl = id.toPurl())

            val results = scanoss.retrievePackageFindings(setOf(pkg)).values

            results.flatMap { it.summary.issues } should beEmpty()
            with(results.flatMap { it.vulnerabilities }.associateBy { it.id }) {
                keys should containAll(
                    "CVE-2024-48948"
                )

                val vulnerability = getValue("CVE-2024-48948")
                vulnerability.summary shouldBe "The Elliptic package 6.5.7 for Node.js, in its for ECDSA " +
                    "implementation, does not correctly verify v..."

                vulnerability.references.find {
                    it.url.toString() == "https://nvd.nist.gov/vuln/detail/CVE-2024-48948"
                } shouldNotBeNull {
                    scoringSystem should beNull()
                    severity shouldBe "MEDIUM"
                    score should beNull()
                    vector should beNull()
                }
            }
        }
    }
})

internal object ScanOssCheck : Condition {
    fun getApiKey(): String? = System.getenv("SCANOSS_API_KEY")

    override fun evaluate(kclass: KClass<out Spec>): Boolean = getApiKey() != null
}
