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

package org.ossreviewtoolkit.utils.common

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.maps.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.net.URI

class UriUtilsTest : WordSpec({
    "getQueryParameters()" should {
        "return the query parameter for a simple query" {
            URI("https://oss-review-toolkit.org?key=value").getQueryParameters() shouldBe
                mapOf("key" to listOf("value"))
        }

        "work with multiple query parameters" {
            URI("https://oss-review-toolkit.org?key1=value1&key2=value2").getQueryParameters() shouldBe
                mapOf("key1" to listOf("value1"), "key2" to listOf("value2"))
        }

        "return query parameter with multiple values" {
            URI("https://oss-review-toolkit.org?key=value1,value2,value3").getQueryParameters() shouldBe
                mapOf("key" to listOf("value1", "value2", "value3"))

            URI("https://oss-review-toolkit.org?key=value1&key=value2").getQueryParameters() shouldBe
                mapOf("key" to listOf("value1", "value2"))
        }

        "work for URIs without query parameters" {
            URI("https://oss-review-toolkit.org").getQueryParameters() should beEmpty()
        }

        "work with empty values" {
            URI("https://oss-review-toolkit.org?key=").getQueryParameters() shouldBe mapOf("key" to listOf(""))
        }
    }
})
