/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.conan

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.utils.test.getAssetFile

class ConanTest : WordSpec({
    "parseConanDataYaml()" should {
        "parse a single-source map" {
            val yaml = """
                sources:
                  3.6.1:
                    sha256: b1bfedcd5b289ff22aee87c9d600f515767ebf45f77168cb6d64f231f518a82e
                    url: https://github.com/openssl/openssl/releases/download/openssl-3.6.1/openssl-3.6.1.tar.gz
            """.trimIndent()

            val conanData = parseConanDataYaml(yaml, "3.6.1")

            conanData.sources.shouldBeSingleton {
                it.url shouldBe
                    "https://github.com/openssl/openssl/releases/download/openssl-3.6.1/openssl-3.6.1.tar.gz"
                it.sha256 shouldBe "b1bfedcd5b289ff22aee87c9d600f515767ebf45f77168cb6d64f231f518a82e"
            }

            conanData.hasPatches shouldBe false
        }

        "parse a multi-source list" {
            val conanDataFile = getAssetFile("conandata-libselinux-3.6.yml")

            val conanData = parseConanDataYaml(conanDataFile.readText(), "3.6")

            conanData.sources should containExactly(
                ConanSourceEntry(
                    url = "https://github.com/SELinuxProject/selinux/releases/download/3.6/libselinux-3.6.tar.gz",
                    sha256 = "ba4e0ef34b270e7672a5e5f1b523fe2beab3a40bb33d9389f4ad3a8728f21b52"
                ),
                ConanSourceEntry(
                    url = "https://github.com/SELinuxProject/selinux/releases/download/3.6/libsepol-3.6.tar.gz",
                    sha256 = "c9dc585ea94903d784d597c861cd5dce6459168f95e22b31a0eab1cdd800975a"
                )
            )
            conanData.hasPatches shouldBe true
        }

        "return an empty sources list for an unknown version" {
            val yaml = """
                sources:
                  3.6.1:
                    sha256: abc
                    url: https://example.com/pkg-3.6.1.tar.gz
            """.trimIndent()

            val conanData = parseConanDataYaml(yaml, "9.9.9")

            conanData.sources should beEmpty()
            conanData.hasPatches shouldBe false
        }
    }

    "parseSourceArtifacts()" should {
        val conan = ConanFactory.create()

        "return all source artifacts for a multi-source package" {
            val conanDataFile = getAssetFile("conandata-libselinux-3.6.yml")
            val conanData = parseConanDataYaml(conanDataFile.readText(), "3.6")

            val artifacts = conan.parseSourceArtifacts(conanData)

            artifacts shouldHaveSize 2
            with(artifacts.first()) {
                url shouldBe
                    "https://github.com/SELinuxProject/selinux/releases/download/3.6/libselinux-3.6.tar.gz"
                hash.algorithm shouldBe HashAlgorithm.SHA256
                hash.value shouldBe "ba4e0ef34b270e7672a5e5f1b523fe2beab3a40bb33d9389f4ad3a8728f21b52"
            }
        }

        "return an empty list when there are no sources" {
            val artifacts = conan.parseSourceArtifacts(ConanData.EMPTY)

            artifacts should beEmpty()
        }
    }
})
