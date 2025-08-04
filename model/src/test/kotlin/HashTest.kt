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

package org.ossreviewtoolkit.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import java.io.File

class HashTest : WordSpec({
    "Passing a single string value to create()" should {
        "create a NONE hash from an empty value" {
            Hash.create("") shouldBe Hash.NONE
        }

        "create an UNKNOWN hash from the 'UNKOWN' value" {
            Hash.create("UNKNOWN") shouldBe Hash("UNKNOWN", HashAlgorithm.UNKNOWN)
        }

        "create an UNKNOWN hash from a value that matches no other hash" {
            Hash.create("0123456789") shouldBe Hash("0123456789", HashAlgorithm.UNKNOWN)
        }

        "create a SHA1 hash from a SHA-1 value" {
            Hash.create("3b93de94e89006954c58225ad10ac55182d32c16").algorithm shouldBe HashAlgorithm.SHA1
        }

        "create a SHA256 hash from a SHA-256 value" {
            Hash.create(
                "34be7ab36220c79915e4c5c740c7d496abff551ab806f56f44c90cc7cfdf9265"
            ).algorithm shouldBe HashAlgorithm.SHA256
        }

        "create a SHA384 hash from a SHA-384 value" {
            Hash.create(
                "70098a0b4d429071008b9c2b0c21899b7cc2d606ba0b46f94fb91a36eb390fa70e5d5391d1651b54fe0f7d881e45bf80"
            ).algorithm shouldBe HashAlgorithm.SHA384
        }

        "create a SHA512 hash from a SHA-512 value" {
            Hash.create(
                "760d9348be5813285b8ad38795a2a5afe788bcddab4f7190ed581c6ea524c0d67a7dd5bdd5384f197e3b5a58c070ab5b" +
                    "db704493cc8e0ad63ab35f753b6d61bf"
            ).algorithm shouldBe HashAlgorithm.SHA512
        }

        "create the correct hash from an SRI value" {
            val hash = Hash.create("sha1-qBFcVeSnAv5NFQq9OHKCKn4J/Jg=")

            hash.algorithm shouldBe HashAlgorithm.SHA1
            hash.value shouldBe "a8115c55e4a702fe4d150abd3872822a7e09fc98"
        }
    }

    "Passing a string value and name to create()" should {
        "succeed if the name is valid for the created hash" {
            Hash("6a7d2814506e9801f13e767964ae3a8f", "MD5").algorithm shouldBe HashAlgorithm.MD5
        }

        "fail if the name is invalid for the created hash" {
            shouldThrow<IllegalArgumentException> {
                Hash("0123456789", "MD5")
            }
        }
    }

    "toSri()" should {
        "return an SRI value from which a hash can be created" {
            val hash = Hash("a8115c55e4a702fe4d150abd3872822a7e09fc98", HashAlgorithm.SHA1)
            val sri = hash.toSri()

            sri shouldBe "sha1-qBFcVeSnAv5NFQq9OHKCKn4J/Jg="
            with(Hash.create(sri)) {
                value shouldBe "a8115c55e4a702fe4d150abd3872822a7e09fc98"
                algorithm shouldBe HashAlgorithm.SHA1
            }
        }
    }

    "equals()" should {
        "be insensitive to the hash's case" {
            (Hash.create("foo") == Hash.create("FOO")) shouldBe true
        }
    }

    "verify()" should {
        "be insensitive to the hash's case" {
            val licenseFile = File("../LICENSE")

            Hash.create("c00ef43045659b53da5d71d49b8cd7e528c9d55b").verify(licenseFile) shouldBe true
            Hash.create("C00EF43045659B53DA5D71D49B8CD7E528C9D55B").verify(licenseFile) shouldBe true
        }
    }
})
