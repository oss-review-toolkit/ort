/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.evaluator

import com.here.ort.model.LicenseSource

import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

class LicenseViewTest : WordSpec({
    "All" should {
        "return the correct licenses" {
            val view = LicenseView.All

            view.licenses(packageWithoutLicense, emptyList()) shouldBe emptyList()

            view.licenses(packageWithoutLicense, detectedLicenses) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.DETECTED),
                Pair("LicenseRef-b", LicenseSource.DETECTED)
            )

            view.licenses(packageWithOnlyConcludedLicense, emptyList()) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.CONCLUDED),
                Pair("LicenseRef-b", LicenseSource.CONCLUDED)
            )

            view.licenses(packageWithOnlyConcludedLicense, detectedLicenses) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.CONCLUDED),
                Pair("LicenseRef-b", LicenseSource.CONCLUDED),
                Pair("LicenseRef-a", LicenseSource.DETECTED),
                Pair("LicenseRef-b", LicenseSource.DETECTED)
            )

            view.licenses(packageWithOnlyDeclaredLicense, emptyList()) shouldBe listOf(
                Pair("license-a", LicenseSource.DECLARED),
                Pair("license-b", LicenseSource.DECLARED)
            )

            view.licenses(packageWithOnlyDeclaredLicense, detectedLicenses) shouldBe listOf(
                Pair("license-a", LicenseSource.DECLARED),
                Pair("license-b", LicenseSource.DECLARED),
                Pair("LicenseRef-a", LicenseSource.DETECTED),
                Pair("LicenseRef-b", LicenseSource.DETECTED)
            )

            view.licenses(packageWithConcludedAndDeclaredLicense, emptyList()) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.CONCLUDED),
                Pair("LicenseRef-b", LicenseSource.CONCLUDED),
                Pair("license-a", LicenseSource.DECLARED),
                Pair("license-b", LicenseSource.DECLARED)
            )

            view.licenses(packageWithConcludedAndDeclaredLicense, detectedLicenses) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.CONCLUDED),
                Pair("LicenseRef-b", LicenseSource.CONCLUDED),
                Pair("license-a", LicenseSource.DECLARED),
                Pair("license-b", LicenseSource.DECLARED),
                Pair("LicenseRef-a", LicenseSource.DETECTED),
                Pair("LicenseRef-b", LicenseSource.DETECTED)
            )
        }
    }

    "ConcludedOrRest" should {
        "return the correct licenses" {
            val view = LicenseView.ConcludedOrRest

            view.licenses(
                packageWithoutLicense,
                emptyList()
            ) shouldBe emptyList()

            view.licenses(
                packageWithoutLicense,
                detectedLicenses
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.DETECTED),
                Pair("LicenseRef-b", LicenseSource.DETECTED)
            )

            view.licenses(
                packageWithOnlyConcludedLicense,
                emptyList()
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.CONCLUDED),
                Pair("LicenseRef-b", LicenseSource.CONCLUDED)
            )

            view.licenses(
                packageWithOnlyConcludedLicense,
                detectedLicenses
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.CONCLUDED),
                Pair("LicenseRef-b", LicenseSource.CONCLUDED)
            )

            view.licenses(
                packageWithOnlyDeclaredLicense,
                emptyList()
            ) shouldBe listOf(
                Pair("license-a", LicenseSource.DECLARED),
                Pair("license-b", LicenseSource.DECLARED)
            )

            view.licenses(
                packageWithOnlyDeclaredLicense,
                detectedLicenses
            ) shouldBe listOf(
                Pair("license-a", LicenseSource.DECLARED),
                Pair("license-b", LicenseSource.DECLARED),
                Pair("LicenseRef-a", LicenseSource.DETECTED),
                Pair("LicenseRef-b", LicenseSource.DETECTED)
            )

            view.licenses(
                packageWithConcludedAndDeclaredLicense,
                emptyList()
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.CONCLUDED),
                Pair("LicenseRef-b", LicenseSource.CONCLUDED)
            )

            view.licenses(
                packageWithConcludedAndDeclaredLicense,
                detectedLicenses
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.CONCLUDED),
                Pair("LicenseRef-b", LicenseSource.CONCLUDED)
            )
        }
    }

    "ConcludedOrDeclaredOrDetected" should {
        "return the correct licenses" {
            val view = LicenseView.ConcludedOrDeclaredOrDetected

            view.licenses(
                packageWithoutLicense,
                emptyList()
            ) shouldBe emptyList()

            view.licenses(
                packageWithoutLicense,
                detectedLicenses
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.DETECTED),
                Pair("LicenseRef-b", LicenseSource.DETECTED)
            )

            view.licenses(
                packageWithOnlyConcludedLicense,
                emptyList()
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.CONCLUDED),
                Pair("LicenseRef-b", LicenseSource.CONCLUDED)
            )

            view.licenses(
                packageWithOnlyConcludedLicense,
                detectedLicenses
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.CONCLUDED),
                Pair("LicenseRef-b", LicenseSource.CONCLUDED)
            )

            view.licenses(
                packageWithOnlyDeclaredLicense,
                emptyList()
            ) shouldBe listOf(
                Pair("license-a", LicenseSource.DECLARED),
                Pair("license-b", LicenseSource.DECLARED)
            )

            view.licenses(
                packageWithOnlyDeclaredLicense,
                detectedLicenses
            ) shouldBe listOf(
                Pair("license-a", LicenseSource.DECLARED),
                Pair("license-b", LicenseSource.DECLARED)
            )

            view.licenses(
                packageWithConcludedAndDeclaredLicense,
                emptyList()
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.CONCLUDED),
                Pair("LicenseRef-b", LicenseSource.CONCLUDED)
            )

            view.licenses(
                packageWithConcludedAndDeclaredLicense,
                detectedLicenses
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.CONCLUDED),
                Pair("LicenseRef-b", LicenseSource.CONCLUDED)
            )
        }
    }

    "ConcludedOrDetected" should {
        "return the correct licenses" {
            val view = LicenseView.ConcludedOrDetected
            view.licenses(
                packageWithoutLicense,
                emptyList()
            ) shouldBe emptyList()

            view.licenses(
                packageWithoutLicense,
                detectedLicenses
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.DETECTED),
                Pair("LicenseRef-b", LicenseSource.DETECTED)
            )

            view.licenses(
                packageWithOnlyConcludedLicense,
                emptyList()
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.CONCLUDED),
                Pair("LicenseRef-b", LicenseSource.CONCLUDED)
            )

            view.licenses(
                packageWithOnlyConcludedLicense,
                detectedLicenses
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.CONCLUDED),
                Pair("LicenseRef-b", LicenseSource.CONCLUDED)
            )

            view.licenses(
                packageWithOnlyDeclaredLicense,
                emptyList()
            ) shouldBe emptyList()

            view.licenses(
                packageWithOnlyDeclaredLicense,
                detectedLicenses
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.DETECTED),
                Pair("LicenseRef-b", LicenseSource.DETECTED)
            )

            view.licenses(
                packageWithConcludedAndDeclaredLicense,
                emptyList()
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.CONCLUDED),
                Pair("LicenseRef-b", LicenseSource.CONCLUDED)
            )

            view.licenses(
                packageWithConcludedAndDeclaredLicense,
                detectedLicenses
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.CONCLUDED),
                Pair("LicenseRef-b", LicenseSource.CONCLUDED)
            )
        }
    }

    "OnlyConcluded" should {
        "return only the concluded licenses" {
            val view = LicenseView.OnlyConcluded
            view.licenses(
                packageWithoutLicense,
                emptyList()
            ) shouldBe emptyList()

            view.licenses(
                packageWithoutLicense,
                detectedLicenses
            ) shouldBe emptyList()

            view.licenses(
                packageWithOnlyConcludedLicense,
                emptyList()
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.CONCLUDED),
                Pair("LicenseRef-b", LicenseSource.CONCLUDED)
            )

            view.licenses(
                packageWithOnlyConcludedLicense,
                detectedLicenses
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.CONCLUDED),
                Pair("LicenseRef-b", LicenseSource.CONCLUDED)
            )

            view.licenses(
                packageWithOnlyDeclaredLicense,
                emptyList()
            ) shouldBe emptyList()

            view.licenses(
                packageWithOnlyDeclaredLicense,
                detectedLicenses
            ) shouldBe emptyList()

            view.licenses(
                packageWithConcludedAndDeclaredLicense,
                emptyList()
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.CONCLUDED),
                Pair("LicenseRef-b", LicenseSource.CONCLUDED)
            )

            view.licenses(
                packageWithConcludedAndDeclaredLicense,
                detectedLicenses
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.CONCLUDED),
                Pair("LicenseRef-b", LicenseSource.CONCLUDED)
            )
        }
    }

    "OnlyDeclared" should {
        "return only the declared licenses" {
            val view = LicenseView.OnlyDeclared

            view.licenses(
                packageWithoutLicense,
                emptyList()
            ) shouldBe emptyList()

            view.licenses(
                packageWithoutLicense,
                detectedLicenses
            ) shouldBe emptyList()

            view.licenses(
                packageWithOnlyConcludedLicense,
                emptyList()
            ) shouldBe emptyList()

            view.licenses(
                packageWithOnlyConcludedLicense,
                detectedLicenses
            ) shouldBe emptyList()

            view.licenses(
                packageWithOnlyDeclaredLicense,
                emptyList()
            ) shouldBe listOf(
                Pair("license-a", LicenseSource.DECLARED),
                Pair("license-b", LicenseSource.DECLARED)
            )

            view.licenses(
                packageWithOnlyDeclaredLicense,
                detectedLicenses
            ) shouldBe listOf(
                Pair("license-a", LicenseSource.DECLARED),
                Pair("license-b", LicenseSource.DECLARED)
            )

            view.licenses(
                packageWithConcludedAndDeclaredLicense,
                emptyList()
            ) shouldBe listOf(
                Pair("license-a", LicenseSource.DECLARED),
                Pair("license-b", LicenseSource.DECLARED)
            )

            view.licenses(
                packageWithConcludedAndDeclaredLicense,
                detectedLicenses
            ) shouldBe listOf(
                Pair("license-a", LicenseSource.DECLARED),
                Pair("license-b", LicenseSource.DECLARED)
            )
        }
    }

    "OnlyDetected" should {
        "return only the detected licenses" {
            val view = LicenseView.OnlyDetected

            view.licenses(
                packageWithoutLicense,
                emptyList()
            ) shouldBe emptyList()

            view.licenses(
                packageWithoutLicense,
                detectedLicenses
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.DETECTED),
                Pair("LicenseRef-b", LicenseSource.DETECTED)
            )

            view.licenses(
                packageWithOnlyConcludedLicense,
                emptyList()
            ) shouldBe emptyList()

            view.licenses(
                packageWithOnlyConcludedLicense,
                detectedLicenses
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.DETECTED),
                Pair("LicenseRef-b", LicenseSource.DETECTED)
            )

            view.licenses(
                packageWithOnlyDeclaredLicense,
                emptyList()
            ) shouldBe emptyList()

            view.licenses(
                packageWithOnlyDeclaredLicense,
                detectedLicenses
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.DETECTED),
                Pair("LicenseRef-b", LicenseSource.DETECTED)
            )

            view.licenses(
                packageWithConcludedAndDeclaredLicense,
                emptyList()
            ) shouldBe emptyList()

            view.licenses(
                packageWithConcludedAndDeclaredLicense,
                detectedLicenses
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.DETECTED),
                Pair("LicenseRef-b", LicenseSource.DETECTED)
            )
        }
    }
})
