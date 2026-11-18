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

package org.ossreviewtoolkit.model.utils

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.Identifier

import org.semver4j.range.Range

class VersionUtilsTest : WordSpec({
    "getIvyVersionRanges()" should {
        "return an empty range list for pathological cases" {
            "".getIvyVersionRanges().get() should beEmpty()
            " ".getIvyVersionRanges().get() should beEmpty()
            "foo".getIvyVersionRanges().get() should beEmpty()
            "null".getIvyVersionRanges().get() should beEmpty()
        }

        "return an empty range list for invalid ranges" {
            "[a,b]".getIvyVersionRanges().get() should beEmpty()
            "[1,2))".getIvyVersionRanges().get() should beEmpty()
            "[1.2.3,4.5.6".getIvyVersionRanges().get() should beEmpty()
            "[1.2.3.4,5.6.7.8]".getIvyVersionRanges().get() should beEmpty()
        }

        "return an empty range list for single versions" {
            "2".getIvyVersionRanges().get() should beEmpty()
            "2.0".getIvyVersionRanges().get() should beEmpty()
            "2.0.0".getIvyVersionRanges().get() should beEmpty()
        }

        "return an empty range list for symbolic versions that point to a single version" {
            "main".getIvyVersionRanges().get() should beEmpty()
            "master".getIvyVersionRanges().get() should beEmpty()
            "latest".getIvyVersionRanges().get() should beEmpty()
            "latest.release".getIvyVersionRanges().get() should beEmpty()
        }

        "return the correct range list for Ivy version ranges" {
            "[1.2.3,4.5.6]".getIvyVersionRanges().get() shouldBe listOf(
                listOf(Range("1.2.3", Range.RangeOperator.GTE), Range("4.5.6", Range.RangeOperator.LTE))
            )

            "[3.3.0,)".getIvyVersionRanges().get() shouldBe listOf(
                listOf(Range("3.3.0", Range.RangeOperator.GTE))
            )
        }
    }

    "isApplicableIvyVersion()" should {
        "support 'Sub Revision Matcher' syntax" {
            with("1.0.+") {
                isApplicableIvyVersion("1.0.1") shouldBe true
                isApplicableIvyVersion("1.0.a") shouldBe true
                isApplicableIvyVersion("1.0.1.0") shouldBe true
            }

            with("1.1+") {
                isApplicableIvyVersion("1.1") shouldBe true
                isApplicableIvyVersion("1.1.5") shouldBe true
                isApplicableIvyVersion("1.10") shouldBe true
            }
        }

        "handle exclusive braces with three segments" {
            with("]1.2.3,4.5.6[") {
                isApplicableIvyVersion("1.2") shouldBe false
                isApplicableIvyVersion("1.3") shouldBe true
                isApplicableIvyVersion("4.5") shouldBe true
                isApplicableIvyVersion("4.6") shouldBe false

                isApplicableIvyVersion("1.2.3") shouldBe false
                isApplicableIvyVersion("1.2.4") shouldBe true
                isApplicableIvyVersion("4.5.5") shouldBe true
                isApplicableIvyVersion("4.5.6") shouldBe false

                isApplicableIvyVersion("1.2.2.999") shouldBe false
                isApplicableIvyVersion("1.2.3.1") shouldBe true
                isApplicableIvyVersion("4.5.5.999") shouldBe true
                isApplicableIvyVersion("4.5.6.0") shouldBe false
            }
        }

        "handle inclusive braces with three segments" {
            with("[1.2.3,4.5.6]") {
                isApplicableIvyVersion("1.2") shouldBe false
                isApplicableIvyVersion("1.3") shouldBe true
                isApplicableIvyVersion("4.5") shouldBe true
                isApplicableIvyVersion("4.6") shouldBe false

                isApplicableIvyVersion("1.2.2") shouldBe false
                isApplicableIvyVersion("1.2.3") shouldBe true
                isApplicableIvyVersion("4.5.6") shouldBe true
                isApplicableIvyVersion("4.5.7") shouldBe false

                isApplicableIvyVersion("1.2.2.999") shouldBe false
                isApplicableIvyVersion("1.2.3.0") shouldBe true
                isApplicableIvyVersion("4.5.6.0") shouldBe true
                isApplicableIvyVersion("4.5.6.1") shouldBe false
            }
        }

        "handle exclusive braces with four segments" {
            with("]1.2.3.4,5.6.7.8[") {
                isApplicableIvyVersion("1.2.3") shouldBe false
                isApplicableIvyVersion("1.2.4") shouldBe true
                isApplicableIvyVersion("5.6.7") shouldBe true
                isApplicableIvyVersion("5.6.8") shouldBe false

                isApplicableIvyVersion("1.2.3.4") shouldBe false
                isApplicableIvyVersion("1.2.4.5") shouldBe true
                isApplicableIvyVersion("5.6.7.7") shouldBe true
                isApplicableIvyVersion("5.6.7.8") shouldBe false
            }
        }

        "handle inclusive braces with four segments" {
            with("[1.2.3.4,5.6.7.8]") {
                isApplicableIvyVersion("1.2.3") shouldBe false
                isApplicableIvyVersion("1.2.4") shouldBe true
                isApplicableIvyVersion("5.6.7") shouldBe true
                isApplicableIvyVersion("5.6.8") shouldBe false

                isApplicableIvyVersion("1.2.3.3") shouldBe false
                isApplicableIvyVersion("1.2.3.4") shouldBe true
                isApplicableIvyVersion("5.6.7.8") shouldBe true
                isApplicableIvyVersion("5.6.7.9") shouldBe false
            }
        }

        "handle more complex versions at inclusive lower bound" {
            with("[1.2.3.4,5.7.6.8]") {
                isApplicableIvyVersion("v1.2.3.4-beta.1+exp.sha.5114f85") shouldBe false
                isApplicableIvyVersion("v1.2.3.4-SNAPSHOT") shouldBe false

                isApplicableIvyVersion("1.2.3.3+20261116") shouldBe false
                isApplicableIvyVersion("1.2.3.4+20261116") shouldBe true

                isApplicableIvyVersion("1.2.3.3+build.45") shouldBe false
                isApplicableIvyVersion("1.2.3.4+build.45") shouldBe true
            }
        }

        "handle lowercase and uppercase v-prefixes" {
            with("[1.2.3.4,1.2.5.0]") {
                isApplicableIvyVersion("v1.2.4") shouldBe true
                isApplicableIvyVersion("V1.2.4") shouldBe true
            }
        }
    }

    "hasIvyVersionRanges()" should {
        "work for up to four segments with less than 4 digits" {
            "[1.2.3.4,5.0]".hasIvyVersionRange() shouldBe true
            "[1.2.3.4.5,6.0]".hasIvyVersionRange() shouldBe false
        }
    }

    "encodeFourIntoThreeSegments()" should {
        "encode a version string" {
            "1".encodeFourIntoThreeSegments() shouldBe "1.0.0"
            "1.2".encodeFourIntoThreeSegments() shouldBe "1.2.0"
            "1.2.3".encodeFourIntoThreeSegments() shouldBe "1.2.30000"
            "1.2.3.4".encodeFourIntoThreeSegments() shouldBe "1.2.30004"
        }

        "encode a version string with v-prefix" {
            "v1.2".encodeFourIntoThreeSegments() shouldBe "v1.2.0"
            "v1.2.3.4".encodeFourIntoThreeSegments() shouldBe "v1.2.30004"
        }

        "encode a version string with pre-release" {
            "1.2-rc.12".encodeFourIntoThreeSegments() shouldBe "1.2.0-rc.12"
            "1.2.3.4-rc.12".encodeFourIntoThreeSegments() shouldBe "1.2.30004-rc.12"
        }

        "encode a version string with build metadata" {
            "1.2+20261116".encodeFourIntoThreeSegments() shouldBe "1.2.0+20261116"
            "1.2.3.4+20261116".encodeFourIntoThreeSegments() shouldBe "1.2.30004+20261116"

            "1.2+build.45".encodeFourIntoThreeSegments() shouldBe "1.2.0+build.45"
            "1.2.3.4+build.45".encodeFourIntoThreeSegments() shouldBe "1.2.30004+build.45"
        }

        "encode a version string with combined cases" {
            "1.2-beta.1+exp.sha.5114f85".encodeFourIntoThreeSegments() shouldBe "1.2.0-beta.1+exp.sha.5114f85"
            "1.2.3.4-beta.1+exp.sha.5114f85".encodeFourIntoThreeSegments() shouldBe "1.2.30004-beta.1+exp.sha.5114f85"

            "v1.2-beta.1+exp.sha.5114f85".encodeFourIntoThreeSegments() shouldBe "v1.2.0-beta.1+exp.sha.5114f85"
            "v1.2.3.4-beta.1+exp.sha.5114f85".encodeFourIntoThreeSegments() shouldBe "v1.2.30004-beta.1+exp.sha.5114f85"
        }
    }
})

private fun String.hasIvyVersionRange() = Identifier(":::$this").hasIvyVersionRange()

private fun String.isApplicableIvyVersion(version: String) =
    Identifier(":::$this").isApplicableIvyVersion(Identifier(":::$version"))
