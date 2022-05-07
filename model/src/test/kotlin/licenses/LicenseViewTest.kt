/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
 * Copyright (C) 2022 Bosch.IO GmbH
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

package org.ossreviewtoolkit.model.licenses

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.Matcher
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containAll
import io.kotest.matchers.should

import org.ossreviewtoolkit.model.LicenseSource
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.model.utils.FileArchiver
import org.ossreviewtoolkit.model.utils.PackageConfigurationProvider
import org.ossreviewtoolkit.utils.spdx.SpdxSingleLicenseExpression
import org.ossreviewtoolkit.utils.test.createDefault
import org.ossreviewtoolkit.utils.test.transformingCollectionMatcher

class LicenseViewTest : WordSpec() {
    private val licenseInfoResolver = LicenseInfoResolver(
        provider = DefaultLicenseInfoProvider(ortResult, PackageConfigurationProvider.EMPTY),
        copyrightGarbage = CopyrightGarbage(),
        addAuthorsToCopyrights = false,
        archiver = FileArchiver.createDefault()
    )

    private fun LicenseView.getLicensesWithSources(
        pkg: Package
    ): List<Pair<SpdxSingleLicenseExpression, LicenseSource>> =
        filter(licenseInfoResolver.resolveLicenseInfo(pkg.id)).licenses.flatMap { resolvedLicense ->
            resolvedLicense.sources.map { resolvedLicense.license to it }
        }

    private fun containLicensesWithSources(
        vararg licenses: Pair<String, LicenseSource>
    ): Matcher<List<Pair<SpdxSingleLicenseExpression, LicenseSource>>?> =
        transformingCollectionMatcher(
            expected = licenses.toList(),
            matcher = ::containAll
        ) { list ->
            list.map { (expression, source) -> expression.toString() to source }
        }

    init {
        "ALL" should {
            "return the correct licenses" {
                val view = LicenseView.ALL

                view.getLicensesWithSources(packageWithoutLicense) should beEmpty()

                view.getLicensesWithSources(packageWithConcludedLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.CONCLUDED,
                            "LicenseRef-b" to LicenseSource.CONCLUDED
                        )

                view.getLicensesWithSources(packageWithDeclaredLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.DECLARED,
                            "LicenseRef-b" to LicenseSource.DECLARED
                        )

                view.getLicensesWithSources(packageWithDetectedLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.DETECTED,
                            "LicenseRef-b" to LicenseSource.DETECTED
                        )

                view.getLicensesWithSources(packageWithConcludedAndDeclaredLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.CONCLUDED,
                            "LicenseRef-b" to LicenseSource.CONCLUDED,
                            "LicenseRef-a" to LicenseSource.DECLARED,
                            "LicenseRef-b" to LicenseSource.DECLARED
                        )

                view.getLicensesWithSources(packageWithConcludedAndDetectedLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.CONCLUDED,
                            "LicenseRef-b" to LicenseSource.CONCLUDED,
                            "LicenseRef-a" to LicenseSource.DETECTED,
                            "LicenseRef-b" to LicenseSource.DETECTED
                        )

                view.getLicensesWithSources(packageWithDeclaredAndDetectedLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.DECLARED,
                            "LicenseRef-b" to LicenseSource.DECLARED,
                            "LicenseRef-a" to LicenseSource.DETECTED,
                            "LicenseRef-b" to LicenseSource.DETECTED
                        )

                view.getLicensesWithSources(packageWithConcludedAndDeclaredAndDetectedLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.CONCLUDED,
                            "LicenseRef-b" to LicenseSource.CONCLUDED,
                            "LicenseRef-a" to LicenseSource.DECLARED,
                            "LicenseRef-b" to LicenseSource.DECLARED,
                            "LicenseRef-a" to LicenseSource.DETECTED,
                            "LicenseRef-b" to LicenseSource.DETECTED
                        )
            }
        }

        "CONCLUDED_OR_DECLARED_AND_DETECTED" should {
            "return the correct licenses" {
                val view = LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED

                view.getLicensesWithSources(packageWithoutLicense) should beEmpty()

                view.getLicensesWithSources(packageWithConcludedLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.CONCLUDED,
                            "LicenseRef-b" to LicenseSource.CONCLUDED
                        )

                view.getLicensesWithSources(packageWithDeclaredLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.DECLARED,
                            "LicenseRef-b" to LicenseSource.DECLARED
                        )

                view.getLicensesWithSources(packageWithDetectedLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.DETECTED,
                            "LicenseRef-b" to LicenseSource.DETECTED
                        )

                view.getLicensesWithSources(packageWithConcludedAndDeclaredLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.CONCLUDED,
                            "LicenseRef-b" to LicenseSource.CONCLUDED
                        )

                view.getLicensesWithSources(packageWithConcludedAndDetectedLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.CONCLUDED,
                            "LicenseRef-b" to LicenseSource.CONCLUDED
                        )

                view.getLicensesWithSources(packageWithDeclaredAndDetectedLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.DECLARED,
                            "LicenseRef-b" to LicenseSource.DECLARED,
                            "LicenseRef-a" to LicenseSource.DETECTED,
                            "LicenseRef-b" to LicenseSource.DETECTED
                        )

                view.getLicensesWithSources(packageWithConcludedAndDeclaredAndDetectedLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.CONCLUDED,
                            "LicenseRef-b" to LicenseSource.CONCLUDED
                        )
            }
        }

        "CONCLUDED_OR_DECLARED_OR_DETECTED" should {
            "return the correct licenses" {
                val view = LicenseView.CONCLUDED_OR_DECLARED_OR_DETECTED

                view.getLicensesWithSources(packageWithoutLicense) should beEmpty()

                view.getLicensesWithSources(packageWithConcludedLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.CONCLUDED,
                            "LicenseRef-b" to LicenseSource.CONCLUDED
                        )

                view.getLicensesWithSources(packageWithDeclaredLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.DECLARED,
                            "LicenseRef-b" to LicenseSource.DECLARED
                        )

                view.getLicensesWithSources(packageWithDetectedLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.DETECTED,
                            "LicenseRef-b" to LicenseSource.DETECTED
                        )

                view.getLicensesWithSources(packageWithConcludedAndDeclaredLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.CONCLUDED,
                            "LicenseRef-b" to LicenseSource.CONCLUDED
                        )

                view.getLicensesWithSources(packageWithConcludedAndDetectedLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.CONCLUDED,
                            "LicenseRef-b" to LicenseSource.CONCLUDED
                        )

                view.getLicensesWithSources(packageWithDeclaredAndDetectedLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.DECLARED,
                            "LicenseRef-b" to LicenseSource.DECLARED
                        )

                view.getLicensesWithSources(packageWithConcludedAndDeclaredAndDetectedLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.CONCLUDED,
                            "LicenseRef-b" to LicenseSource.CONCLUDED
                        )
            }
        }

        "CONCLUDED_OR_DETECTED" should {
            "return the correct licenses" {
                val view = LicenseView.CONCLUDED_OR_DETECTED

                view.getLicensesWithSources(packageWithoutLicense) should beEmpty()

                view.getLicensesWithSources(packageWithConcludedLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.CONCLUDED,
                            "LicenseRef-b" to LicenseSource.CONCLUDED
                        )

                view.getLicensesWithSources(packageWithDeclaredLicense) should beEmpty()

                view.getLicensesWithSources(packageWithDetectedLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.DETECTED,
                            "LicenseRef-b" to LicenseSource.DETECTED
                        )

                view.getLicensesWithSources(packageWithConcludedAndDeclaredLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.CONCLUDED,
                            "LicenseRef-b" to LicenseSource.CONCLUDED
                        )

                view.getLicensesWithSources(packageWithConcludedAndDetectedLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.CONCLUDED,
                            "LicenseRef-b" to LicenseSource.CONCLUDED
                        )

                view.getLicensesWithSources(packageWithDeclaredAndDetectedLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.DETECTED,
                            "LicenseRef-b" to LicenseSource.DETECTED
                        )

                view.getLicensesWithSources(packageWithConcludedAndDeclaredAndDetectedLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.CONCLUDED,
                            "LicenseRef-b" to LicenseSource.CONCLUDED
                        )
            }
        }

        "ONLY_CONCLUDED" should {
            "return only the concluded licenses" {
                val view = LicenseView.ONLY_CONCLUDED

                view.getLicensesWithSources(packageWithoutLicense) should beEmpty()

                view.getLicensesWithSources(packageWithConcludedLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.CONCLUDED,
                            "LicenseRef-b" to LicenseSource.CONCLUDED
                        )

                view.getLicensesWithSources(packageWithDeclaredLicense) should beEmpty()

                view.getLicensesWithSources(packageWithDetectedLicense) should beEmpty()

                view.getLicensesWithSources(packageWithConcludedAndDeclaredLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.CONCLUDED,
                            "LicenseRef-b" to LicenseSource.CONCLUDED
                        )

                view.getLicensesWithSources(packageWithConcludedAndDetectedLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.CONCLUDED,
                            "LicenseRef-b" to LicenseSource.CONCLUDED
                        )

                view.getLicensesWithSources(packageWithDeclaredAndDetectedLicense) should beEmpty()

                view.getLicensesWithSources(packageWithConcludedAndDeclaredAndDetectedLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.CONCLUDED,
                            "LicenseRef-b" to LicenseSource.CONCLUDED
                        )
            }
        }

        "ONLY_DECLARED" should {
            "return only the declared licenses" {
                val view = LicenseView.ONLY_DECLARED

                view.getLicensesWithSources(packageWithoutLicense) should beEmpty()

                view.getLicensesWithSources(packageWithConcludedLicense) should beEmpty()

                view.getLicensesWithSources(packageWithDeclaredLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.DECLARED,
                            "LicenseRef-b" to LicenseSource.DECLARED
                        )

                view.getLicensesWithSources(packageWithDetectedLicense) should beEmpty()

                view.getLicensesWithSources(packageWithConcludedAndDeclaredLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.DECLARED,
                            "LicenseRef-b" to LicenseSource.DECLARED
                        )

                view.getLicensesWithSources(packageWithConcludedAndDetectedLicense) should beEmpty()

                view.getLicensesWithSources(packageWithDeclaredAndDetectedLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.DECLARED,
                            "LicenseRef-b" to LicenseSource.DECLARED
                        )

                view.getLicensesWithSources(packageWithConcludedAndDeclaredAndDetectedLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.DECLARED,
                            "LicenseRef-b" to LicenseSource.DECLARED
                        )
            }
        }

        "ONLY_DETECTED" should {
            "return only the detected licenses" {
                val view = LicenseView.ONLY_DETECTED

                view.getLicensesWithSources(packageWithoutLicense) should beEmpty()

                view.getLicensesWithSources(packageWithConcludedLicense) should beEmpty()

                view.getLicensesWithSources(packageWithDeclaredLicense) should beEmpty()

                view.getLicensesWithSources(packageWithDetectedLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.DETECTED,
                            "LicenseRef-b" to LicenseSource.DETECTED
                        )

                view.getLicensesWithSources(packageWithConcludedAndDeclaredLicense) should beEmpty()

                view.getLicensesWithSources(packageWithConcludedAndDetectedLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.DETECTED,
                            "LicenseRef-b" to LicenseSource.DETECTED
                        )

                view.getLicensesWithSources(packageWithDeclaredAndDetectedLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.DETECTED,
                            "LicenseRef-b" to LicenseSource.DETECTED
                        )

                view.getLicensesWithSources(packageWithConcludedAndDeclaredAndDetectedLicense) should
                        containLicensesWithSources(
                            "LicenseRef-a" to LicenseSource.DETECTED,
                            "LicenseRef-b" to LicenseSource.DETECTED
                        )
            }
        }
    }
}
