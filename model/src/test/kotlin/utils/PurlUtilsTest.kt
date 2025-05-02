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

import org.ossreviewtoolkit.utils.test.readResourceValue

class PurlUtilsTest : StringSpec({
    val testData = readResourceValue<List<TestSuiteData>>("/test-suite-data.json")
    val testCases = testData.filterNot { it.isInvalid }.also { testCases ->
        // Perform some sanity checks on the test data itself.
        check(testCases.none { it.namespace?.isEmpty() == true })

        val slashes = setOf("/", "%2F", "%2f")
        check(testCases.none { slashes.any { slash -> it.name != null && slash in it.name } })
    }

    "The purl test suite should pass" {
        assertSoftly {
            testCases.forEach { testCase ->
                // Invalid cases have been filtered out before.
                val type = checkNotNull(testCase.type)

                val purl = createPurl(
                    PurlType.fromString(type),
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
