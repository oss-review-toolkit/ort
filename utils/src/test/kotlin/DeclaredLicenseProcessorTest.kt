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

package org.ossreviewtoolkit.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import org.ossreviewtoolkit.spdx.SpdxDeclaredLicenseMapping
import org.ossreviewtoolkit.spdx.SpdxExpression
import org.ossreviewtoolkit.spdx.SpdxLicense
import org.ossreviewtoolkit.spdx.SpdxLicenseAliasMapping
import org.ossreviewtoolkit.spdx.SpdxLicenseIdExpression
import org.ossreviewtoolkit.spdx.enumSetOf

class DeclaredLicenseProcessorTest : StringSpec() {
    /**
     * A collection of declared license strings found in open source packages.
     */
    private val declaredLicenses = SpdxLicenseAliasMapping.mapping.keys + SpdxDeclaredLicenseMapping.mapping.keys

    init {
        "Declared licenses can be processed" {
            declaredLicenses.forEach { declaredLicense ->
                val processedLicense = DeclaredLicenseProcessor.process(declaredLicense)

                // Include the declared license in the comparison to see where a failure comes from.
                "$processedLicense from $declaredLicense" shouldNotBe "null from $declaredLicense"
                processedLicense!!.validate(SpdxExpression.Strictness.ALLOW_CURRENT)
            }
        }

        "Mapped licenses are de-duplicated" {
            val declaredLicenses = listOf("Apache2", "Apache-2")

            val processedLicenses = DeclaredLicenseProcessor.process(declaredLicenses)

            processedLicenses.spdxExpression shouldBe SpdxLicenseIdExpression("Apache-2.0")
            processedLicenses.unmapped shouldBe emptyList()
        }

        "Licenses are not mapped to deprecated SPDX licenses" {
            declaredLicenses.forEach { declaredLicense ->
                val processedLicense = DeclaredLicenseProcessor.process(declaredLicense)

                // Include the declared license in the comparison to see where a failure comes from.
                "$processedLicense from $declaredLicense" shouldNotBe "null from $declaredLicense"
                processedLicense!!.spdxLicenses().forEach {
                    // Include the license ID in the comparison to make it easier to find the wrong mapping.
                    "$it ${it.deprecated}" shouldBe "$it false"
                }
            }
        }

        "Prefixes and suffixes are removed from the license" {
            val processedLicense = DeclaredLicenseProcessor.process(
                "https://choosealicense.com/licenses/apache-2.0.txt"
            )

            processedLicense shouldNotBe null
            processedLicense!!.spdxLicenses() shouldBe enumSetOf(SpdxLicense.APACHE_2_0)
        }

        "Preprocessing licenses does not make mapping redundant" {
            val processableLicenses = SpdxDeclaredLicenseMapping.mapping.keys.filter { declaredLicense ->
                SpdxLicenseAliasMapping.map(DeclaredLicenseProcessor.preprocess(declaredLicense)) != null
            }

            processableLicenses shouldBe emptyList()
        }

        "SPDX expression only contains valid licenses" {
            val declaredLicenses = listOf("Apache-2.0", "invalid")

            val processedLicenses = DeclaredLicenseProcessor.process(declaredLicenses)

            processedLicenses.spdxExpression shouldBe SpdxLicenseIdExpression("Apache-2.0")
            processedLicenses.unmapped should containExactly("invalid")
        }
    }
}
