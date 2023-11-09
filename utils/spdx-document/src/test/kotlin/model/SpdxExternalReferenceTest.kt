/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.utils.spdxdocument.model

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContainExactly

import org.ossreviewtoolkit.utils.spdxdocument.SpdxModelMapper

class SpdxExternalReferenceTest : WordSpec({
    "Serializing categories" should {
        "use dashes in names" {
            SpdxModelMapper.toJson(SpdxExternalReference.Category.entries) shouldEqualJson """
                [
                  "SECURITY",
                  "PACKAGE-MANAGER",
                  "PERSISTENT-ID",
                  "OTHER"
                ]
            """.trimIndent()
        }
    }

    "Deserializing categories" should {
        "accept dashes in names" {
            SpdxModelMapper.fromJson<List<SpdxExternalReference.Category>>(
                """
                    [
                      "SECURITY",
                      "PACKAGE-MANAGER",
                      "PERSISTENT-ID",
                      "OTHER"
                    ]
                """.trimIndent()
            ) shouldContainExactly SpdxExternalReference.Category.entries
        }

        "accept underscores in names" {
            SpdxModelMapper.fromJson<List<SpdxExternalReference.Category>>(
                """
                    [
                      "SECURITY",
                      "PACKAGE_MANAGER",
                      "PERSISTENT_ID",
                      "OTHER"
                    ]
                """.trimIndent()
            ) shouldContainExactly SpdxExternalReference.Category.entries
        }
    }
})
