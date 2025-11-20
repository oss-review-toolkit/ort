/*
 * Copyright (C) 2024 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

    "Should throw an exception for purl type not found" {
        val exception = kotlin.runCatching {
            PurlType.fromString("unknowntype")
        }.exceptionOrNull()

        exception shouldBe IllegalArgumentException("Unknown purl type: unknowntype")
    }

    "Should map known Purl types to Ort identifier types correctly" {
        PurlType.getOrtTypeFromPurlType("apk") shouldBe "Apk"
        PurlType.getOrtTypeFromPurlType("bazel") shouldBe "Bazel"
        PurlType.getOrtTypeFromPurlType("bitbucket") shouldBe "Bitbucket"
        PurlType.getOrtTypeFromPurlType("bower") shouldBe "Bower"
        PurlType.getOrtTypeFromPurlType("cargo") shouldBe "Cargo"
        PurlType.getOrtTypeFromPurlType("carthage") shouldBe "Carthage"
        PurlType.getOrtTypeFromPurlType("cocoapods") shouldBe "CocoaPods"
        PurlType.getOrtTypeFromPurlType("composer") shouldBe "Composer"
        PurlType.getOrtTypeFromPurlType("conan") shouldBe "Conan"
        PurlType.getOrtTypeFromPurlType("conda") shouldBe "Conda"
        PurlType.getOrtTypeFromPurlType("cran") shouldBe "Cran"
        PurlType.getOrtTypeFromPurlType("deb") shouldBe "Deb"
        PurlType.getOrtTypeFromPurlType("docker") shouldBe "Docker"
        PurlType.getOrtTypeFromPurlType("drupal") shouldBe "Drupal"
        PurlType.getOrtTypeFromPurlType("gem") shouldBe "Gem"
        PurlType.getOrtTypeFromPurlType("generic") shouldBe "Generic"
        PurlType.getOrtTypeFromPurlType("github") shouldBe "GitHub"
        PurlType.getOrtTypeFromPurlType("gitlab") shouldBe "GitLab"
        PurlType.getOrtTypeFromPurlType("golang") shouldBe "Go"
        PurlType.getOrtTypeFromPurlType("hackage") shouldBe "Hackage"
        PurlType.getOrtTypeFromPurlType("hex") shouldBe "Hex"
        PurlType.getOrtTypeFromPurlType("huggingface") shouldBe "HuggingFace"
        PurlType.getOrtTypeFromPurlType("maven") shouldBe "Maven"
        PurlType.getOrtTypeFromPurlType("mlflow") shouldBe "MlFlow"
        PurlType.getOrtTypeFromPurlType("npm") shouldBe "NPM"
        PurlType.getOrtTypeFromPurlType("nuget") shouldBe "NuGet"
        PurlType.getOrtTypeFromPurlType("otp") shouldBe "Otp"
        PurlType.getOrtTypeFromPurlType("pub") shouldBe "Pub"
        PurlType.getOrtTypeFromPurlType("pypi") shouldBe "PyPi"
        PurlType.getOrtTypeFromPurlType("rpm") shouldBe "RPM"
        PurlType.getOrtTypeFromPurlType("swift") shouldBe "Swift"
    }

    "Should map unknown Purl types to Ort identifier types correctly" {
        PurlType.getOrtTypeFromPurlType("unknowntype") shouldBe "Unknowntype"
        PurlType.getOrtTypeFromPurlType("jit") shouldBe "Jit"
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
