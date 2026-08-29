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

        "return an empty list for single versions" {
            "2".getIvyVersionRanges().get() shouldBe listOf()
            "2.0".getIvyVersionRanges().get() shouldBe listOf()
            "2.0.0".getIvyVersionRanges().get() shouldBe listOf()
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
            with(Identifier(":::1.0.+")) {
                isApplicableIvyVersion(Identifier(":::1.0.1")) shouldBe true
                isApplicableIvyVersion(Identifier(":::1.0.a")) shouldBe true
                isApplicableIvyVersion(Identifier(":::1.0.1.0")) shouldBe true
            }

            with(Identifier(":::1.1+")) {
                isApplicableIvyVersion(Identifier(":::1.1")) shouldBe true
                isApplicableIvyVersion(Identifier(":::1.1.5")) shouldBe true
                isApplicableIvyVersion(Identifier(":::1.10")) shouldBe true
            }
        }
    }
})
