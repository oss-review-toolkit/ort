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

import com.here.ort.spdx.SpdxExpression
import com.here.ort.spdx.SpdxLicenseIdMapping
import com.here.ort.spdx.SpdxLicenseStringMapping

import io.kotlintest.shouldNotBe
import io.kotlintest.specs.StringSpec

class DeclaredLicenseProcessorTest : StringSpec() {
    /**
     * A collection of declared license strings found in open source packages.
     */
    private val declaredLicenses = SpdxLicenseIdMapping.mapping.keys + SpdxLicenseStringMapping.mapping.keys

    init {
        "Declared licenses can be processed" {
            declaredLicenses.forEach { declaredLicense ->
                val processedLicense = DeclaredLicenseProcessor.process(declaredLicense)

                processedLicense shouldNotBe null
                processedLicense!!.validate(SpdxExpression.Strictness.ALLOW_DEPRECATED)
            }
        }
    }
}
