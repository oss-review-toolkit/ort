/*
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

package com.here.ort.spdx

import io.kotlintest.assertSoftly
import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

class SpdxLicenseAliasMappingTest : WordSpec({
    "The mapping" should {
        "contain only parsable keys" {
            val unparsableLicenses = SpdxLicenseAliasMapping.mapping.filterNot { (declaredLicense, _) ->
                try {
                    // Be as lenient as possible when parsing declared licenses as we really only want to check for
                    // general parsability here.
                    SpdxExpression.parse(declaredLicense)
                    true
                } catch (e: SpdxException) {
                    false
                }
            }

            unparsableLicenses shouldBe emptyMap()
        }

        "not contain plain SPDX license ids" {
            assertSoftly {
                SpdxDeclaredLicenseMapping.mapping.forEach { (declaredLicense, _) ->
                    SpdxLicense.forId(declaredLicense) shouldBe null
                }
            }
        }
    }
})
