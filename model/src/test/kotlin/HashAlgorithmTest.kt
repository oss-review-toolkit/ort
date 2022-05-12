/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

import java.io.File

class HashAlgorithmTest : StringSpec({
    // Use a file that is always checked out with Unix line endings in the working tree to get the same file contents on
    // all platforms and in the Git object store.
    val file = File("../LICENSE")

    "SHA string representations need to contain a dash for MessageDigest compatibility" {
        enumValues<HashAlgorithm>().filter { it.toString().startsWith("SHA") }.forAll {
            it.toString() shouldStartWith "SHA-"
        }
    }

    "Calculating the SHA1 on a file should yield the correct result" {
        // The expected hash was calculated with "sha1sum".
        HashAlgorithm.SHA1.calculate(file) shouldBe "c00ef43045659b53da5d71d49b8cd7e528c9d55b"
    }

    "Calculating the SHA1GIT on a file should yield the correct result" {
        // The expected hash was calculated with "git hash-object".
        HashAlgorithm.SHA1GIT.calculate(file) shouldBe "3f4d322ebd76de0f1bbb9c867e1f818f5202efd3"
    }

    "Calculating the SHA1GIT on a resource should yield the correct result" {
        // The expected hash was calculated with "git hash-object".
        HashAlgorithm.SHA1GIT.calculate("/licenses/Apache-2.0") shouldBe
                "261eeb9e9f8b2b4b0d119366dda99c6fd7d35c64"
    }

    "Calculating the SHA1GIT on non-existent field should return null" {
        HashAlgorithm.SHA1GIT.calculate("/license/DOESNOTEXIST") shouldBe null
    }
})
