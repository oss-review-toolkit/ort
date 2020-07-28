/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.model.licenses

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.Matcher
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should

import org.ossreviewtoolkit.model.LicenseSource
import org.ossreviewtoolkit.spdx.SpdxExpression
import org.ossreviewtoolkit.spdx.toSpdx

class LicenseViewTest : WordSpec() {
    init {
        "ALL" should {
            "return the correct licenses" {
                val view = LicenseView.ALL

                view.licenses(packageWithoutLicense, emptyList()) should beEmpty()

                view.licenses(packageWithoutLicense, detectedLicenses) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.DETECTED,
                    "LicenseRef-b" to LicenseSource.DETECTED
                )

                view.licenses(packageWithOnlyConcludedLicense, emptyList()) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.CONCLUDED,
                    "LicenseRef-b" to LicenseSource.CONCLUDED
                )

                view.licenses(packageWithOnlyConcludedLicense, detectedLicenses) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.CONCLUDED,
                    "LicenseRef-b" to LicenseSource.CONCLUDED,
                    "LicenseRef-a" to LicenseSource.DETECTED,
                    "LicenseRef-b" to LicenseSource.DETECTED
                )

                view.licenses(packageWithOnlyDeclaredLicense, emptyList()) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.DECLARED,
                    "LicenseRef-b" to LicenseSource.DECLARED
                )

                view.licenses(packageWithOnlyDeclaredLicense, detectedLicenses) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.DECLARED,
                    "LicenseRef-b" to LicenseSource.DECLARED,
                    "LicenseRef-a" to LicenseSource.DETECTED,
                    "LicenseRef-b" to LicenseSource.DETECTED
                )

                view.licenses(packageWithConcludedAndDeclaredLicense, emptyList()) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.CONCLUDED,
                    "LicenseRef-b" to LicenseSource.CONCLUDED,
                    "LicenseRef-a" to LicenseSource.DECLARED,
                    "LicenseRef-b" to LicenseSource.DECLARED
                )

                view.licenses(
                    packageWithConcludedAndDeclaredLicense,
                    detectedLicenses
                ) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.CONCLUDED,
                    "LicenseRef-b" to LicenseSource.CONCLUDED,
                    "LicenseRef-a" to LicenseSource.DECLARED,
                    "LicenseRef-b" to LicenseSource.DECLARED,
                    "LicenseRef-a" to LicenseSource.DETECTED,
                    "LicenseRef-b" to LicenseSource.DETECTED
                )
            }
        }

        "CONCLUDED_OR_REST" should {
            "return the correct licenses" {
                val view = LicenseView.CONCLUDED_OR_REST

                view.licenses(
                    packageWithoutLicense,
                    emptyList()
                ) should beEmpty()

                view.licenses(
                    packageWithoutLicense,
                    detectedLicenses
                ) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.DETECTED,
                    "LicenseRef-b" to LicenseSource.DETECTED
                )

                view.licenses(
                    packageWithOnlyConcludedLicense,
                    emptyList()
                ) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.CONCLUDED,
                    "LicenseRef-b" to LicenseSource.CONCLUDED
                )

                view.licenses(
                    packageWithOnlyConcludedLicense,
                    detectedLicenses
                ) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.CONCLUDED,
                    "LicenseRef-b" to LicenseSource.CONCLUDED
                )

                view.licenses(
                    packageWithOnlyDeclaredLicense,
                    emptyList()
                ) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.DECLARED,
                    "LicenseRef-b" to LicenseSource.DECLARED
                )

                view.licenses(
                    packageWithOnlyDeclaredLicense,
                    detectedLicenses
                ) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.DECLARED,
                    "LicenseRef-b" to LicenseSource.DECLARED,
                    "LicenseRef-a" to LicenseSource.DETECTED,
                    "LicenseRef-b" to LicenseSource.DETECTED
                )

                view.licenses(
                    packageWithConcludedAndDeclaredLicense,
                    emptyList()
                ) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.CONCLUDED,
                    "LicenseRef-b" to LicenseSource.CONCLUDED
                )

                view.licenses(
                    packageWithConcludedAndDeclaredLicense,
                    detectedLicenses
                ) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.CONCLUDED,
                    "LicenseRef-b" to LicenseSource.CONCLUDED
                )
            }
        }

        "CONCLUDED_OR_DECLARED_OR_DETECTED" should {
            "return the correct licenses" {
                val view = LicenseView.CONCLUDED_OR_DECLARED_OR_DETECTED

                view.licenses(
                    packageWithoutLicense,
                    emptyList()
                ) should beEmpty()

                view.licenses(
                    packageWithoutLicense,
                    detectedLicenses
                ) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.DETECTED,
                    "LicenseRef-b" to LicenseSource.DETECTED
                )

                view.licenses(
                    packageWithOnlyConcludedLicense,
                    emptyList()
                ) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.CONCLUDED,
                    "LicenseRef-b" to LicenseSource.CONCLUDED
                )

                view.licenses(
                    packageWithOnlyConcludedLicense,
                    detectedLicenses
                ) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.CONCLUDED,
                    "LicenseRef-b" to LicenseSource.CONCLUDED
                )

                view.licenses(
                    packageWithOnlyDeclaredLicense,
                    emptyList()
                ) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.DECLARED,
                    "LicenseRef-b" to LicenseSource.DECLARED
                )

                view.licenses(
                    packageWithOnlyDeclaredLicense,
                    detectedLicenses
                ) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.DECLARED,
                    "LicenseRef-b" to LicenseSource.DECLARED
                )

                view.licenses(
                    packageWithConcludedAndDeclaredLicense,
                    emptyList()
                ) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.CONCLUDED,
                    "LicenseRef-b" to LicenseSource.CONCLUDED
                )

                view.licenses(
                    packageWithConcludedAndDeclaredLicense,
                    detectedLicenses
                ) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.CONCLUDED,
                    "LicenseRef-b" to LicenseSource.CONCLUDED
                )
            }
        }

        "CONCLUDED_OR_DETECTED" should {
            "return the correct licenses" {
                val view = LicenseView.CONCLUDED_OR_DETECTED
                view.licenses(
                    packageWithoutLicense,
                    emptyList()
                ) should beEmpty()

                view.licenses(
                    packageWithoutLicense,
                    detectedLicenses
                ) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.DETECTED,
                    "LicenseRef-b" to LicenseSource.DETECTED
                )

                view.licenses(
                    packageWithOnlyConcludedLicense,
                    emptyList()
                ) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.CONCLUDED,
                    "LicenseRef-b" to LicenseSource.CONCLUDED
                )

                view.licenses(
                    packageWithOnlyConcludedLicense,
                    detectedLicenses
                ) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.CONCLUDED,
                    "LicenseRef-b" to LicenseSource.CONCLUDED
                )

                view.licenses(
                    packageWithOnlyDeclaredLicense,
                    emptyList()
                ) should beEmpty()

                view.licenses(
                    packageWithOnlyDeclaredLicense,
                    detectedLicenses
                ) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.DETECTED,
                    "LicenseRef-b" to LicenseSource.DETECTED
                )

                view.licenses(
                    packageWithConcludedAndDeclaredLicense,
                    emptyList()
                ) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.CONCLUDED,
                    "LicenseRef-b" to LicenseSource.CONCLUDED
                )

                view.licenses(
                    packageWithConcludedAndDeclaredLicense,
                    detectedLicenses
                ) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.CONCLUDED,
                    "LicenseRef-b" to LicenseSource.CONCLUDED
                )
            }
        }

        "ONLY_CONCLUDED" should {
            "return only the concluded licenses" {
                val view = LicenseView.ONLY_CONCLUDED
                view.licenses(
                    packageWithoutLicense,
                    emptyList()
                ) should beEmpty()

                view.licenses(
                    packageWithoutLicense,
                    detectedLicenses
                ) should beEmpty()

                view.licenses(
                    packageWithOnlyConcludedLicense,
                    emptyList()
                ) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.CONCLUDED,
                    "LicenseRef-b" to LicenseSource.CONCLUDED
                )

                view.licenses(
                    packageWithOnlyConcludedLicense,
                    detectedLicenses
                ) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.CONCLUDED,
                    "LicenseRef-b" to LicenseSource.CONCLUDED
                )

                view.licenses(
                    packageWithOnlyDeclaredLicense,
                    emptyList()
                ) should beEmpty()

                view.licenses(
                    packageWithOnlyDeclaredLicense,
                    detectedLicenses
                ) should beEmpty()

                view.licenses(
                    packageWithConcludedAndDeclaredLicense,
                    emptyList()
                ) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.CONCLUDED,
                    "LicenseRef-b" to LicenseSource.CONCLUDED
                )

                view.licenses(
                    packageWithConcludedAndDeclaredLicense,
                    detectedLicenses
                ) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.CONCLUDED,
                    "LicenseRef-b" to LicenseSource.CONCLUDED
                )
            }
        }

        "ONLY_DECLARED" should {
            "return only the declared licenses" {
                val view = LicenseView.ONLY_DECLARED

                view.licenses(
                    packageWithoutLicense,
                    emptyList()
                ) should beEmpty()

                view.licenses(
                    packageWithoutLicense,
                    detectedLicenses
                ) should beEmpty()

                view.licenses(
                    packageWithOnlyConcludedLicense,
                    emptyList()
                ) should beEmpty()

                view.licenses(
                    packageWithOnlyConcludedLicense,
                    detectedLicenses
                ) should beEmpty()

                view.licenses(
                    packageWithOnlyDeclaredLicense,
                    emptyList()
                ) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.DECLARED,
                    "LicenseRef-b" to LicenseSource.DECLARED
                )

                view.licenses(
                    packageWithOnlyDeclaredLicense,
                    detectedLicenses
                ) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.DECLARED,
                    "LicenseRef-b" to LicenseSource.DECLARED
                )

                view.licenses(
                    packageWithConcludedAndDeclaredLicense,
                    emptyList()
                ) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.DECLARED,
                    "LicenseRef-b" to LicenseSource.DECLARED
                )

                view.licenses(
                    packageWithConcludedAndDeclaredLicense,
                    detectedLicenses
                ) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.DECLARED,
                    "LicenseRef-b" to LicenseSource.DECLARED
                )
            }
        }

        "ONLY_DETECTED" should {
            "return only the detected licenses" {
                val view = LicenseView.ONLY_DETECTED

                view.licenses(
                    packageWithoutLicense,
                    emptyList()
                ) should beEmpty()

                view.licenses(
                    packageWithoutLicense,
                    detectedLicenses
                ) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.DETECTED,
                    "LicenseRef-b" to LicenseSource.DETECTED
                )

                view.licenses(
                    packageWithOnlyConcludedLicense,
                    emptyList()
                ) should beEmpty()

                view.licenses(
                    packageWithOnlyConcludedLicense,
                    detectedLicenses
                ) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.DETECTED,
                    "LicenseRef-b" to LicenseSource.DETECTED
                )

                view.licenses(
                    packageWithOnlyDeclaredLicense,
                    emptyList()
                ) should beEmpty()

                view.licenses(
                    packageWithOnlyDeclaredLicense,
                    detectedLicenses
                ) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.DETECTED,
                    "LicenseRef-b" to LicenseSource.DETECTED
                )

                view.licenses(
                    packageWithConcludedAndDeclaredLicense,
                    emptyList()
                ) should beEmpty()

                view.licenses(
                    packageWithConcludedAndDeclaredLicense,
                    detectedLicenses
                ) should containLicensesWithSources(
                    "LicenseRef-a" to LicenseSource.DETECTED,
                    "LicenseRef-b" to LicenseSource.DETECTED
                )
            }
        }
    }
}

fun containLicensesWithSources(
    vararg licenses: Pair<String, LicenseSource>
): Matcher<List<Pair<SpdxExpression, LicenseSource>>?> =
    containExactlyInAnyOrder(licenses.map { Pair(it.first.toSpdx(), it.second) })
