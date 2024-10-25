/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.model.utils

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beEmpty
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldNotContainIgnoringCase

import java.io.File

import org.ossreviewtoolkit.model.readValue

class PurlUtilsTest : StringSpec({
    val testDataFile = File("src/test/assets/test-suite-data.json")
    val testData = testDataFile.readValue<List<TestSuiteData>>()
    val testCases = testData.filterNot { it.isInvalid }

    "The purl test suite should pass" {
        assertSoftly {
            testCases.forEach { testCase ->
                val purl = createPurl(
                    testCase.type.orEmpty(),
                    testCase.namespace.orEmpty(),
                    testCase.name.orEmpty(),
                    testCase.version.orEmpty(),
                    testCase.qualifiers.orEmpty(),
                    testCase.subpath.orEmpty()
                )

                purl shouldBe testCase.canonicalPurl
            }
        }
    }

    "The purl test cases should never have an empty namespace" {
        assertSoftly {
            testCases.filter { it.namespace != null }.forEach { testCase ->
                testCase.namespace shouldNot beEmpty()
            }
        }
    }

    "The purl test cases should never contain slashes in names" {
        assertSoftly {
            testCases.forEach { testCase ->
                testCase.name shouldNotContain "/"
                testCase.name shouldNotContainIgnoringCase "%2F" // Encoded "/".
            }
        }
    }
})

private data class TestSuiteData(
    val description: String,
    val purl: String,
    val canonicalPurl: String?,
    val type: String?,
    val namespace: String?,
    val name: String?,
    val version: String?,
    val qualifiers: Map<String, String>?,
    val subpath: String?,
    val isInvalid: Boolean
)
