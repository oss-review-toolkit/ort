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

package org.ossreviewtoolkit.plugins.scanners.fossid

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll

import java.time.LocalDateTime

class FossIdNamingProviderTest : WordSpec() {

    override suspend fun afterEach(testCase: TestCase, result: TestResult) {
        unmockkAll()
    }

    companion object {
        const val MAX_SCAN_CODE_LEN = 255
    }

    init {
        "createScanCode" should {
            val namingProvider = FossIdNamingProvider(null, null, emptyMap())

            val mockedDateTime = LocalDateTime.of(2024, 4, 1, 10, 0)
            val expectedTimestamp = "20240401_100000"

            val longBranchName = "origin/feature/LOREM-123321_lorem-ipsum-dolor-sit-amet-consectetur-adipiscing-elit-" +
                "aliquam-laoreet-ac-nulla-in-bibendum-phasellus-sodales-vel-lorem-consequat-efficitur-morbi-viverra-a" +
                "ccumsan-libero-a-tincidunt-libero-venenatis-nec-nulla-facilisi-vestibulum-pharetra-finibus-mi-vitae-" +
                "luctus"

            val longScanPattern = "#projectName_#currentTimestamp_lorem-ipsum-dolor-sit-amet-consectetur-adipiscing-e" +
                "lit-aliquam-laoreet-ac-nulla-in-bibendum-phasellus-sodales-vel-lorem-consequat-efficitur-morbi-viver" +
                "ra-accumsan-libero-a-tincidunt-libero-venenatis-nec-nulla-facilisi-vestibulum-pharetra-finibus-mi-vi" +
                "tae-luctus"

            "create code without branch name, when it's empty" {
                mockkStatic(LocalDateTime::class) {
                    every { LocalDateTime.now() } returns mockedDateTime

                    namingProvider.createScanCode(
                        "example-project-name", null, ""
                    ) shouldBeEqual "example-project-name_$expectedTimestamp"
                }
            }

            "create code with branch name" {
                mockkStatic(LocalDateTime::class) {
                    every { LocalDateTime.now() } returns mockedDateTime

                    namingProvider.createScanCode(
                        "example-project-name", null, "CODE-2233_Red-dots-added-to-layout"
                    ) shouldBeEqual "example-project-name_" + expectedTimestamp + "_CODE-2233_Red-dots-added-to-layout"
                }
                unmockkAll()
            }

            "create code with branch name and delta tag" {
                mockkStatic(LocalDateTime::class) {
                    every { LocalDateTime.now() } returns mockedDateTime

                    namingProvider.createScanCode(
                        "example-project-name", FossId.DeltaTag.DELTA, "CODE-2233_Red-dots-added-to-layout"
                    ) shouldBeEqual "example-project-name_" + expectedTimestamp +
                        "_delta_CODE-2233_Red-dots-added-to-layout"
                }
                unmockkAll()
            }

            "remove all non-standard signs from branch name when creating code" {
                mockkStatic(LocalDateTime::class) {
                    every { LocalDateTime.now() } returns mockedDateTime

                    namingProvider.createScanCode(
                        "example-project-name", null, "feature/CODE-12%%$@@&^_SOME_*&^#!*text!!"
                    ) shouldBeEqual "example-project-name_" +
                        expectedTimestamp + "_feature_CODE-12________SOME_______text__"
                }
            }

            "truncate very long scan id to fit maximum length accepted by FossID (255 chars)" {
                mockkStatic(LocalDateTime::class) {
                    every { LocalDateTime.now() } returns mockedDateTime

                    namingProvider.createScanCode(
                        "example-project-name", FossId.DeltaTag.DELTA, longBranchName
                    ).length shouldBeLessThanOrEqual MAX_SCAN_CODE_LEN
                }
            }

            "create code without branch name form custom naming pattern" {
                val customScanPattern = "#projectName_#currentTimestamp"

                val namingProviderWithLongScanPattern = FossIdNamingProvider(null, customScanPattern, emptyMap())
                mockkStatic(LocalDateTime::class) {
                    every { LocalDateTime.now() } returns mockedDateTime

                    namingProviderWithLongScanPattern.createScanCode(
                        "example-project-name", null, ""
                    ) shouldBeEqual "example-project-name_20240401_100000"
                }
            }

            "create code without branch name form custom naming pattern when branch name is provided" {
                val customScanPattern = "#projectName_#currentTimestamp"

                val namingProviderWithLongScanPattern = FossIdNamingProvider(null, customScanPattern, emptyMap())
                mockkStatic(LocalDateTime::class) {
                    every { LocalDateTime.now() } returns mockedDateTime

                    namingProviderWithLongScanPattern.createScanCode(
                        "example-project-name", null, "feature/LOREM-3212"
                    ) shouldBeEqual "example-project-name_20240401_100000"
                }
            }

            "create code without branch name form custom naming pattern when too long branch name is provided" {
                val customScanPattern = "#projectName_#currentTimestamp_#branch"
                val namingProviderWithLongScanPattern = FossIdNamingProvider(null, customScanPattern, emptyMap())
                mockkStatic(LocalDateTime::class) {
                    every { LocalDateTime.now() } returns mockedDateTime

                    namingProviderWithLongScanPattern.createScanCode(
                        "example-project-name", null, longBranchName
                    ).length shouldBeLessThanOrEqual MAX_SCAN_CODE_LEN
                }
            }

            "throw an exception if scan code pattern is too long" {
                val namingProviderWithLongScanPattern = FossIdNamingProvider(null, longScanPattern, emptyMap())
                mockkStatic(LocalDateTime::class) {
                    every { LocalDateTime.now() } returns mockedDateTime

                    shouldThrow<IllegalArgumentException> {
                        namingProviderWithLongScanPattern.createScanCode("example-project-name", null, "")
                    }
                }
            }
        }
    }
}
