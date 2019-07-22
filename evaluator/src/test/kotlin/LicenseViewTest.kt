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
            LicenseView.All.licenses(packageWithoutLicense, emptyList()) shouldBe emptyList()

            LicenseView.All.licenses(packageWithoutLicense, detectedLicenses) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.DETECTED),
                Pair("LicenseRef-b", LicenseSource.DETECTED)
            )

            LicenseView.All.licenses(packageWithOnlyConcludedLicense, emptyList()) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.CONCLUDED),
                Pair("LicenseRef-b", LicenseSource.CONCLUDED)
            )

            LicenseView.All.licenses(packageWithOnlyConcludedLicense, detectedLicenses) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.CONCLUDED),
                Pair("LicenseRef-b", LicenseSource.CONCLUDED),
                Pair("LicenseRef-a", LicenseSource.DETECTED),
                Pair("LicenseRef-b", LicenseSource.DETECTED)
            )

            LicenseView.All.licenses(packageWithOnlyDeclaredLicense, emptyList()) shouldBe listOf(
                Pair("license-a", LicenseSource.DECLARED),
                Pair("license-b", LicenseSource.DECLARED)
            )

            LicenseView.All.licenses(packageWithOnlyDeclaredLicense, detectedLicenses) shouldBe listOf(
                Pair("license-a", LicenseSource.DECLARED),
                Pair("license-b", LicenseSource.DECLARED),
                Pair("LicenseRef-a", LicenseSource.DETECTED),
                Pair("LicenseRef-b", LicenseSource.DETECTED)
            )

            LicenseView.All.licenses(packageWithConcludedAndDeclaredLicense, emptyList()) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.CONCLUDED),
                Pair("LicenseRef-b", LicenseSource.CONCLUDED),
                Pair("license-a", LicenseSource.DECLARED),
                Pair("license-b", LicenseSource.DECLARED)
            )

            LicenseView.All.licenses(packageWithConcludedAndDeclaredLicense, detectedLicenses) shouldBe listOf(
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
            LicenseView.ConcludedOrRest.licenses(
                packageWithoutLicense,
                emptyList()
            ) shouldBe emptyList()

            LicenseView.ConcludedOrRest.licenses(
                packageWithoutLicense,
                detectedLicenses
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.DETECTED),
                Pair("LicenseRef-b", LicenseSource.DETECTED)
            )

            LicenseView.ConcludedOrRest.licenses(
                packageWithOnlyConcludedLicense,
                emptyList()
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.CONCLUDED),
                Pair("LicenseRef-b", LicenseSource.CONCLUDED)
            )

            LicenseView.ConcludedOrRest.licenses(
                packageWithOnlyConcludedLicense,
                detectedLicenses
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.CONCLUDED),
                Pair("LicenseRef-b", LicenseSource.CONCLUDED)
            )

            LicenseView.ConcludedOrRest.licenses(
                packageWithOnlyDeclaredLicense,
                emptyList()
            ) shouldBe listOf(
                Pair("license-a", LicenseSource.DECLARED),
                Pair("license-b", LicenseSource.DECLARED)
            )

            LicenseView.ConcludedOrRest.licenses(
                packageWithOnlyDeclaredLicense,
                detectedLicenses
            ) shouldBe listOf(
                Pair("license-a", LicenseSource.DECLARED),
                Pair("license-b", LicenseSource.DECLARED),
                Pair("LicenseRef-a", LicenseSource.DETECTED),
                Pair("LicenseRef-b", LicenseSource.DETECTED)
            )

            LicenseView.ConcludedOrRest.licenses(
                packageWithConcludedAndDeclaredLicense,
                emptyList()
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.CONCLUDED),
                Pair("LicenseRef-b", LicenseSource.CONCLUDED)
            )

            LicenseView.ConcludedOrRest.licenses(
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
            LicenseView.ConcludedOrDeclaredOrDetected.licenses(
                packageWithoutLicense,
                emptyList()
            ) shouldBe emptyList()

            LicenseView.ConcludedOrDeclaredOrDetected.licenses(
                packageWithoutLicense,
                detectedLicenses
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.DETECTED),
                Pair("LicenseRef-b", LicenseSource.DETECTED)
            )

            LicenseView.ConcludedOrDeclaredOrDetected.licenses(
                packageWithOnlyConcludedLicense,
                emptyList()
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.CONCLUDED),
                Pair("LicenseRef-b", LicenseSource.CONCLUDED)
            )

            LicenseView.ConcludedOrDeclaredOrDetected.licenses(
                packageWithOnlyConcludedLicense,
                detectedLicenses
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.CONCLUDED),
                Pair("LicenseRef-b", LicenseSource.CONCLUDED)
            )

            LicenseView.ConcludedOrDeclaredOrDetected.licenses(
                packageWithOnlyDeclaredLicense,
                emptyList()
            ) shouldBe listOf(
                Pair("license-a", LicenseSource.DECLARED),
                Pair("license-b", LicenseSource.DECLARED)
            )

            LicenseView.ConcludedOrDeclaredOrDetected.licenses(
                packageWithOnlyDeclaredLicense,
                detectedLicenses
            ) shouldBe listOf(
                Pair("license-a", LicenseSource.DECLARED),
                Pair("license-b", LicenseSource.DECLARED)
            )

            LicenseView.ConcludedOrDeclaredOrDetected.licenses(
                packageWithConcludedAndDeclaredLicense,
                emptyList()
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.CONCLUDED),
                Pair("LicenseRef-b", LicenseSource.CONCLUDED)
            )

            LicenseView.ConcludedOrDeclaredOrDetected.licenses(
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
            LicenseView.OnlyConcluded.licenses(
                packageWithoutLicense,
                emptyList()
            ) shouldBe emptyList()

            LicenseView.OnlyConcluded.licenses(
                packageWithoutLicense,
                detectedLicenses
            ) shouldBe emptyList()

            LicenseView.OnlyConcluded.licenses(
                packageWithOnlyConcludedLicense,
                emptyList()
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.CONCLUDED),
                Pair("LicenseRef-b", LicenseSource.CONCLUDED)
            )

            LicenseView.OnlyConcluded.licenses(
                packageWithOnlyConcludedLicense,
                detectedLicenses
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.CONCLUDED),
                Pair("LicenseRef-b", LicenseSource.CONCLUDED)
            )

            LicenseView.OnlyConcluded.licenses(
                packageWithOnlyDeclaredLicense,
                emptyList()
            ) shouldBe emptyList()

            LicenseView.OnlyConcluded.licenses(
                packageWithOnlyDeclaredLicense,
                detectedLicenses
            ) shouldBe emptyList()

            LicenseView.OnlyConcluded.licenses(
                packageWithConcludedAndDeclaredLicense,
                emptyList()
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.CONCLUDED),
                Pair("LicenseRef-b", LicenseSource.CONCLUDED)
            )

            LicenseView.OnlyConcluded.licenses(
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
            LicenseView.OnlyDeclared.licenses(
                packageWithoutLicense,
                emptyList()
            ) shouldBe emptyList()

            LicenseView.OnlyDeclared.licenses(
                packageWithoutLicense,
                detectedLicenses
            ) shouldBe emptyList()

            LicenseView.OnlyDeclared.licenses(
                packageWithOnlyConcludedLicense,
                emptyList()
            ) shouldBe emptyList()

            LicenseView.OnlyDeclared.licenses(
                packageWithOnlyConcludedLicense,
                detectedLicenses
            ) shouldBe emptyList()

            LicenseView.OnlyDeclared.licenses(
                packageWithOnlyDeclaredLicense,
                emptyList()
            ) shouldBe listOf(
                Pair("license-a", LicenseSource.DECLARED),
                Pair("license-b", LicenseSource.DECLARED)
            )

            LicenseView.OnlyDeclared.licenses(
                packageWithOnlyDeclaredLicense,
                detectedLicenses
            ) shouldBe listOf(
                Pair("license-a", LicenseSource.DECLARED),
                Pair("license-b", LicenseSource.DECLARED)
            )

            LicenseView.OnlyDeclared.licenses(
                packageWithConcludedAndDeclaredLicense,
                emptyList()
            ) shouldBe listOf(
                Pair("license-a", LicenseSource.DECLARED),
                Pair("license-b", LicenseSource.DECLARED)
            )

            LicenseView.OnlyDeclared.licenses(
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
            LicenseView.OnlyDetected.licenses(
                packageWithoutLicense,
                emptyList()
            ) shouldBe emptyList()

            LicenseView.OnlyDetected.licenses(
                packageWithoutLicense,
                detectedLicenses
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.DETECTED),
                Pair("LicenseRef-b", LicenseSource.DETECTED)
            )

            LicenseView.OnlyDetected.licenses(
                packageWithOnlyConcludedLicense,
                emptyList()
            ) shouldBe emptyList()

            LicenseView.OnlyDetected.licenses(
                packageWithOnlyConcludedLicense,
                detectedLicenses
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.DETECTED),
                Pair("LicenseRef-b", LicenseSource.DETECTED)
            )

            LicenseView.OnlyDetected.licenses(
                packageWithOnlyDeclaredLicense,
                emptyList()
            ) shouldBe emptyList()

            LicenseView.OnlyDetected.licenses(
                packageWithOnlyDeclaredLicense,
                detectedLicenses
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.DETECTED),
                Pair("LicenseRef-b", LicenseSource.DETECTED)
            )

            LicenseView.OnlyDetected.licenses(
                packageWithConcludedAndDeclaredLicense,
                emptyList()
            ) shouldBe emptyList()

            LicenseView.OnlyDetected.licenses(
                packageWithConcludedAndDeclaredLicense,
                detectedLicenses
            ) shouldBe listOf(
                Pair("LicenseRef-a", LicenseSource.DETECTED),
                Pair("LicenseRef-b", LicenseSource.DETECTED)
            )
        }
    }
})
