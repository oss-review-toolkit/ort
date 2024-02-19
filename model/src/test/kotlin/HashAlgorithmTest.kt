/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.model

import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

import java.io.File

class HashAlgorithmTest : WordSpec({
    // Use a file that is always checked out with Unix line endings in the working tree to get the same file contents on
    // all platforms and in the Git object store.
    val file = File("../LICENSE")

    "String representations" should {
        "contain a dash after 'SHA' for MessageDigest compatibility" {
            HashAlgorithm.entries.filter { it.toString().startsWith("SHA") }.forAll {
                it.toString() shouldStartWith "SHA-"
            }
        }

        "match the empty value for 0-byte input" {
            HashAlgorithm.entries.filter { it.isVerifiable }.forAll {
                it.calculate(byteArrayOf()) shouldBe it.emptyValue
            }
        }
    }

    "Calculating the SHA1" should {
        "yield the correct result for a file" {
            // The expected hash was calculated with "sha1sum".
            HashAlgorithm.SHA1.calculate(file) shouldBe "c00ef43045659b53da5d71d49b8cd7e528c9d55b"
        }
    }

    "Calculating the SHA1GIT" should {
        "yield the correct result for a file" {
            // The expected hash was calculated with "git hash-object".
            HashAlgorithm.SHA1GIT.calculate(file) shouldBe "3f4d322ebd76de0f1bbb9c867e1f818f5202efd3"
        }

        "yield the correct result for a resource should " {
            // The expected hash was calculated with "git hash-object".
            HashAlgorithm.SHA1GIT.calculate("/licenses/Apache-2.0") shouldBe
                "261eeb9e9f8b2b4b0d119366dda99c6fd7d35c64"
        }

        "return null for a non-existent file" {
            HashAlgorithm.SHA1GIT.calculate("/license/DOESNOTEXIST") shouldBe null
        }
    }
})
