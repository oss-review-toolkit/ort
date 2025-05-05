/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.utils.ort

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.maps.beEmpty as beEmptyMap
import io.kotest.matchers.maps.containExactly as containExactlyEntries
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.SpdxDeclaredLicenseMapping
import org.ossreviewtoolkit.utils.spdx.SpdxException
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.SpdxLicense
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseIdExpression
import org.ossreviewtoolkit.utils.spdx.SpdxSimpleLicenseMapping
import org.ossreviewtoolkit.utils.spdx.toExpression
import org.ossreviewtoolkit.utils.spdx.toSpdx

class DeclaredLicenseProcessorTest : StringSpec() {
    /**
     * A collection of declared license strings found in open source packages.
     */
    private val declaredLicenses = SpdxDeclaredLicenseMapping.mapping.keys +
        SpdxSimpleLicenseMapping.simpleExpressionMapping.keys +
        SpdxSimpleLicenseMapping.deprecatedExpressionMapping.keys

    init {
        "Declared licenses can be processed" {
            declaredLicenses.forAll { declaredLicense ->
                val processedLicense = DeclaredLicenseProcessor.process(declaredLicense)

                // Include the declared license in the comparison to see where a failure comes from.
                "$processedLicense from $declaredLicense" shouldNotBe "null from $declaredLicense"
                processedLicense!!.validate(SpdxExpression.Strictness.ALLOW_CURRENT)
            }
        }

        "Mapped licenses are de-duplicated" {
            val declaredLicenses = setOf("Apache2", "Apache-2")

            val processedLicenses = DeclaredLicenseProcessor.process(declaredLicenses)

            processedLicenses.spdxExpression shouldBe SpdxLicenseIdExpression("Apache-2.0")
            processedLicenses.mapped should containExactlyEntries(
                "Apache2" to SpdxLicense.APACHE_2_0.toExpression() as SpdxExpression,
                "Apache-2" to SpdxLicense.APACHE_2_0.toExpression() as SpdxExpression
            )
            processedLicenses.unmapped should beEmpty()
        }

        "Licenses are not mapped to deprecated SPDX licenses" {
            declaredLicenses.forAll { declaredLicense ->
                val processedLicense = DeclaredLicenseProcessor.process(declaredLicense)

                processedLicense shouldNotBeNull {
                    shouldNotThrow<SpdxException> {
                        validate(SpdxExpression.Strictness.ALLOW_CURRENT)
                    }
                }
            }
        }

        "Prefixes and suffixes are removed from the license" {
            val processedLicense = DeclaredLicenseProcessor.process(
                "https://choosealicense.com/licenses/apache-2.0.txt"
            )

            processedLicense shouldBe SpdxLicenseIdExpression("Apache-2.0")
        }

        "Stripping URL surroundings should not make any mapping redundant" {
            SpdxDeclaredLicenseMapping.mapping.forAll { (license, expression) ->
                val strippedLicense = DeclaredLicenseProcessor.stripUrlSurroundings(license)

                withClue("Stripping '$license' to '$strippedLicense' makes the mapping to '$expression' redundant") {
                    SpdxSimpleLicenseMapping.map(strippedLicense) should beNull()
                }
            }
        }

        "The SPDX expression only contains valid licenses" {
            val declaredLicenses = setOf("Apache-2.0", "invalid")

            val processedLicenses = DeclaredLicenseProcessor.process(declaredLicenses)

            processedLicenses.spdxExpression shouldBe SpdxLicenseIdExpression("Apache-2.0")
            processedLicenses.mapped should beEmptyMap()
            processedLicenses.unmapped should containExactly("invalid")
        }

        "Processing a compound SPDX expression should result in the same expression" {
            val declaredLicenses = setOf("Apache-2.0 AND LicenseRef-Proprietary")

            val processedLicenses = DeclaredLicenseProcessor.process(declaredLicenses)

            processedLicenses.spdxExpression shouldBe SpdxExpression.parse("Apache-2.0 AND LicenseRef-Proprietary")
            processedLicenses.mapped should beEmptyMap()
            processedLicenses.unmapped should beEmpty()
        }

        "The declared license mapping is applied" {
            val declaredLicenses = setOf("Apache-2.0", "https://domain/path/license.html")
            val declaredLicenseMapping = mapOf("https://domain/path/license.html" to "MIT".toSpdx())

            val processedLicenses = DeclaredLicenseProcessor.process(declaredLicenses, declaredLicenseMapping)

            processedLicenses.spdxExpression shouldBe "Apache-2.0 AND MIT".toSpdx()
            processedLicenses.mapped should containExactlyEntries("https://domain/path/license.html" to "MIT".toSpdx())
            processedLicenses.unmapped should beEmpty()
        }

        "The declared license mapping discards licenses which are mapped to 'NONE' when applied " {
            val declaredLicenses = setOf("Copyright (c) the authors.", "Apache-2.0", "MIT")
            val declaredLicenseMapping = mapOf("Copyright (c) the authors." to SpdxConstants.NONE.toSpdx())

            val processedLicenses = DeclaredLicenseProcessor.process(declaredLicenses, declaredLicenseMapping)

            processedLicenses.spdxExpression shouldBe "Apache-2.0 AND MIT".toSpdx()
            processedLicenses.mapped should containExactlyEntries(
                "Copyright (c) the authors." to SpdxConstants.NONE.toSpdx()
            )
            processedLicenses.unmapped should beEmpty()
        }
    }
}
