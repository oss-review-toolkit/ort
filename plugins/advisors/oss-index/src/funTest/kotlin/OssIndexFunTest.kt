/*
 * Copyright (C) 2023 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.advisors.ossindex

import io.kotest.core.annotation.Condition
import io.kotest.core.annotation.EnabledIf
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containAll
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import kotlin.reflect.KClass

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.plugins.advisors.api.normalizeVulnerabilityData
import org.ossreviewtoolkit.plugins.api.Secret

@EnabledIf(OssIndexCredentials::class)
class OssIndexFunTest : WordSpec({
    val oi = OssIndexFactory.create(
        username = checkNotNull(OssIndexCredentials.getUsername()),
        token = Secret(checkNotNull(OssIndexCredentials.getToken()))
    )

    "Vulnerable Maven packages" should {
        "return findings for Guava" {
            val id = Identifier("Maven:com.google.guava:guava:19.0")
            val pkg = Package.EMPTY.copy(id = id, purl = id.toPurl())

            val results = oi.retrievePackageFindings(setOf(pkg)).values.map { it.normalizeVulnerabilityData() }

            results.flatMap { it.summary.issues } should beEmpty()
            with(results.flatMap { it.vulnerabilities }.associateBy { it.id }) {
                keys should containAll(
                    "CVE-2018-10237",
                    "CVE-2020-8908",
                    "CVE-2023-2976"
                )

                val vulnerability = getValue("CVE-2023-2976")
                vulnerability.summary shouldBe "Files or Directories Accessible to External Parties"

                vulnerability.references.find {
                    it.url.toString() == "http://web.nvd.nist.gov/view/vuln/detail?vulnId=CVE-2023-2976"
                } shouldNotBeNull {
                    scoringSystem shouldBe "CVSS:3.1"
                    severity shouldBe "HIGH"
                    score shouldBe 7.1f
                    vector shouldBe "CVSS:3.1/AV:L/AC:L/PR:L/UI:N/S:U/C:H/I:H/A:N"
                }
            }
        }

        "return findings for Commons-Compress" {
            val id = Identifier("Maven:org.apache.commons:commons-compress:1.23.0")
            val pkg = Package.EMPTY.copy(id = id, purl = id.toPurl())

            val results = oi.retrievePackageFindings(setOf(pkg)).values.map { it.normalizeVulnerabilityData() }

            results.flatMap { it.summary.issues } should beEmpty()
            with(results.flatMap { it.vulnerabilities }.associateBy { it.id }) {
                keys should containAll(
                    "CVE-2023-42503"
                )

                val vulnerability = getValue("CVE-2023-42503")
                vulnerability.summary shouldBe "Improper Input Validation"

                vulnerability.references.find {
                    it.url.toString() == "http://web.nvd.nist.gov/view/vuln/detail?vulnId=CVE-2023-42503"
                } shouldNotBeNull {
                    scoringSystem shouldBe "CVSS:3.1"
                    severity shouldBe "MEDIUM"
                    score shouldBe 5.5f
                    vector shouldBe "CVSS:3.1/AV:L/AC:L/PR:N/UI:R/S:U/C:N/I:N/A:H"
                }
            }
        }
    }

    "Vulnerable NPM packages" should {
        "return findings for Elliptic" {
            val id = Identifier("NPM::elliptic:6.5.7")
            val pkg = Package.EMPTY.copy(id = id, purl = id.toPurl())

            val results = oi.retrievePackageFindings(setOf(pkg)).values.map { it.normalizeVulnerabilityData() }

            results.flatMap { it.summary.issues } should beEmpty()
            with(results.flatMap { it.vulnerabilities }.associateBy { it.id }) {
                keys should containAll(
                    "CVE-2024-48948"
                )

                val vulnerability = getValue("CVE-2024-48948")
                vulnerability.summary shouldBe "Improper Verification of Cryptographic Signature"

                vulnerability.references.find {
                    it.url.toString() == "https://github.com/indutny/elliptic/pull/322"
                } shouldNotBeNull {
                    scoringSystem shouldBe "CVSS:4.0"
                    severity shouldBe "MEDIUM"
                    score shouldBe 6.3f
                    vector shouldBe "CVSS:4.0/AV:N/AC:H/AT:P/PR:N/UI:N/VC:N/VI:L/VA:N/SC:N/SI:N/SA:N"
                }
            }
        }
    }
})

internal object OssIndexCredentials : Condition {
    fun getUsername(): String? = System.getenv("OSS_INDEX_USERNAME")
    fun getToken(): String? = System.getenv("OSS_INDEX_PASSWORD")

    override fun evaluate(kclass: KClass<out Spec>): Boolean = getUsername() != null && getToken() != null
}
