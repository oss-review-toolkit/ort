/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.advisors.vulnerablecode

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.plugins.api.PluginConfig

class VulnerableCodeFunTest : WordSpec({
    "Vulnerable Go packages" should {
        "return findings for QUIC" {
            val vc = VulnerableCodeFactory().create(PluginConfig())
            val id = Identifier("Go::github.com/quic-go/quic-go:0.40.0")
            val pkg = Package.EMPTY.copy(id, purl = id.toPurl())

            val findings = vc.retrievePackageFindings(setOf(pkg))

            findings.values.flatMap { it.summary.issues } should beEmpty()
            with(findings.values.flatMap { it.vulnerabilities }.associateBy { it.id }) {
                keys shouldContainAll setOf(
                    "CVE-2023-49295"
                )

                getValue("CVE-2023-49295").references.find {
                    it.url.toString() == "https://nvd.nist.gov/vuln/detail/CVE-2023-49295"
                } shouldNotBeNull {
                    scoringSystem shouldBe "cvssv3"
                    severity shouldBe "MEDIUM"
                    score shouldBe 6.5f
                    vector shouldBe "CVSS:3.1/AV:N/AC:L/PR:L/UI:N/S:U/C:N/I:N/A:H"
                }
            }
        }
    }

    "Vulnerable Maven packages" should {
        "return findings for Guava" {
            val vc = VulnerableCodeFactory().create(PluginConfig())
            val id = Identifier("Maven:com.google.guava:guava:19.0")
            val pkg = Package.EMPTY.copy(id, purl = id.toPurl())

            val findings = vc.retrievePackageFindings(setOf(pkg))

            findings.values.flatMap { it.summary.issues } should beEmpty()
            with(findings.values.flatMap { it.vulnerabilities }.associateBy { it.id }) {
                keys shouldContainAll setOf(
                    "CVE-2018-10237",
                    "CVE-2020-8908",
                    "CVE-2023-2976"
                )

                getValue("CVE-2023-2976").references.find {
                    it.url.toString() == "https://nvd.nist.gov/vuln/detail/CVE-2023-2976"
                } shouldNotBeNull {
                    scoringSystem shouldBe "cvssv3"
                    severity shouldBe "HIGH"
                    score shouldBe 7.1f
                    vector shouldBe "CVSS:3.1/AV:L/AC:L/PR:L/UI:N/S:U/C:H/I:H/A:N"
                }
            }
        }

        "return findings for Commons-Compress" {
            val vc = VulnerableCodeFactory().create(PluginConfig())
            val id = Identifier("Maven:org.apache.commons:commons-compress:1.23.0")
            val pkg = Package.EMPTY.copy(id, purl = id.toPurl())

            val findings = vc.retrievePackageFindings(setOf(pkg))

            findings.values.flatMap { it.summary.issues } should beEmpty()
            with(findings.values.flatMap { it.vulnerabilities }.associateBy { it.id }) {
                keys shouldContainAll setOf(
                    "CVE-2023-42503"
                )

                getValue("CVE-2023-42503").references.find {
                    it.url.toString() == "https://nvd.nist.gov/vuln/detail/CVE-2023-42503"
                } shouldNotBeNull {
                    scoringSystem shouldBe "cvssv3"
                    severity shouldBe "MEDIUM"
                    score shouldBe 5.5f
                    vector shouldBe "CVSS:3.1/AV:L/AC:L/PR:N/UI:R/S:U/C:N/I:N/A:H"
                }
            }
        }
    }

    "Vulnerable NPM packages" should {
        "return findings for Elliptic" {
            val vc = VulnerableCodeFactory().create(PluginConfig())
            val id = Identifier("NPM::elliptic:6.5.7")
            val pkg = Package.EMPTY.copy(id, purl = id.toPurl())

            val findings = vc.retrievePackageFindings(setOf(pkg))

            findings.values.flatMap { it.summary.issues } should beEmpty()
            with(findings.values.flatMap { it.vulnerabilities }.associateBy { it.id }) {
                keys shouldContainAll setOf(
                    "CVE-2024-48948"
                )

                getValue("CVE-2024-48948").references.find {
                    it.url.toString() == "https://github.com/indutny/elliptic"
                } shouldNotBeNull {
                    scoringSystem shouldBe "cvssv3.1"
                    severity shouldBe "MEDIUM"
                    score shouldBe 5.3f
                    vector shouldBe "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:L/A:N"
                }
            }
        }
    }
})
