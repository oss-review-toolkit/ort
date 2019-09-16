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

package com.here.ort.utils

import com.here.ort.spdx.enumSetOf
import com.here.ort.spdx.SpdxExpression
import com.here.ort.spdx.SpdxDeclaredLicenseMapping
import com.here.ort.spdx.SpdxLicense
import com.here.ort.spdx.SpdxLicenseAliasMapping
import com.here.ort.spdx.SpdxLicenseIdExpression

import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.StringSpec

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
                processedLicense!!.validate(SpdxExpression.Strictness.ALLOW_DEPRECATED)
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

        "A prefix is removed from the license" {
            val processedLicense = DeclaredLicenseProcessor.process("https://choosealicense.com/licenses/apache-2.0")

            processedLicense shouldNotBe null
            processedLicense!!.spdxLicenses() shouldBe enumSetOf(SpdxLicense.APACHE_2_0)
        }
    }
}
