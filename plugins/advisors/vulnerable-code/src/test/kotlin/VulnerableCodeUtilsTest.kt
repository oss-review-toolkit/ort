/*
 * Copyright (C) 2021 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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
import io.kotest.matchers.shouldBe

import java.net.URI

class VulnerableCodeUtilsTest : WordSpec({
    "deriveSummary()" should {
        "return null for a null or blank string" {
            null.deriveSummary() shouldBe null
            "".deriveSummary() shouldBe null
            " ".deriveSummary() shouldBe null
        }

        "return the original string if it fits into the maximum summary length" {
            val description = "a".repeat(MAX_SUMMARY_LENGTH)

            description.deriveSummary() shouldBe description
        }

        "truncate a string that exceeds the maximum summary length" {
            val description = "a".repeat(MAX_SUMMARY_LENGTH) + "b"

            description.deriveSummary() shouldBe "${"a".repeat(MAX_SUMMARY_LENGTH)}..."
        }
    }

    @Suppress("MaxLineLength")
    "fixupUrlEscaping()" should {
        "fixup a wrongly escaped ampersand" {
            val u = """https://nvd.nist.gov/vuln/search/results?adv_search=true&isCpeNameSearch=true&query=cpe:2.3:a:oracle:retail_category_management_planning_\\&_optimization:16.0.3:*:*:*:*:*:*:*"""

            URI(u.fixupUrlEscaping()) shouldBe URI(
                """https://nvd.nist.gov/vuln/search/results?adv_search=true&isCpeNameSearch=true&query=cpe:2.3:a:oracle:retail_category_management_planning_%26_optimization:16.0.3:*:*:*:*:*:*:*"""
            )
        }

        "fixup a wrongly escaped slash" {
            val u = """https://nvd.nist.gov/vuln/search/results?adv_search=true&isCpeNameSearch=true&query=cpe:2.3:a:apple:swiftnio_http\/2:*:*:*:*:*:swift:*:*"""

            URI(u.fixupUrlEscaping()) shouldBe URI(
                """https://nvd.nist.gov/vuln/search/results?adv_search=true&isCpeNameSearch=true&query=cpe:2.3:a:apple:swiftnio_http/2:*:*:*:*:*:swift:*:*"""
            )
        }

        "fixup a wrongly escaped plus" {
            val u = """https://nvd.nist.gov/vuln/search/results?adv_search=true&isCpeNameSearch=true&query=cpe:2.3:a:oracle:hyperion_bi\+:*:*:*:*:*:*:*:*"""

            URI(u.fixupUrlEscaping()) shouldBe URI(
                """https://nvd.nist.gov/vuln/search/results?adv_search=true&isCpeNameSearch=true&query=cpe:2.3:a:oracle:hyperion_bi%2B:*:*:*:*:*:*:*:*"""
            )
        }
    }
})
