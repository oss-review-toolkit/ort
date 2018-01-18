/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

package com.here.ort.model

import com.here.ort.utils.yamlMapper

import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec

class VcsInfoTest : StringSpec({
    "Deserializing VcsInfo" should {
        "work when all fields are given" {
            val yaml = """
                ---
                provider: "provider"
                url: "url"
                revision: "revision"
                path: "path"
                """.trimIndent()

            println(yaml)

            val vcsInfo = yamlMapper.readValue(yaml, VcsInfo::class.java)

            vcsInfo.provider shouldBe "provider"
            vcsInfo.url shouldBe "url"
            vcsInfo.revision shouldBe "revision"
            vcsInfo.path shouldBe "path"
        }

        "assign empty strings to missing fields when only provider is set" {
            val yaml = """
                ---
                provider: "provider"
                """.trimIndent()

            val vcsInfo = yamlMapper.readValue(yaml, VcsInfo::class.java)

            vcsInfo.provider shouldBe "provider"
            vcsInfo.url shouldBe ""
            vcsInfo.revision shouldBe ""
            vcsInfo.path shouldBe ""
        }

        "assign empty strings to missing fields when only path is set" {
            val yaml = """
                ---
                path: "path"
                """.trimIndent()

            val vcsInfo = yamlMapper.readValue(yaml, VcsInfo::class.java)

            vcsInfo.provider shouldBe ""
            vcsInfo.url shouldBe ""
            vcsInfo.revision shouldBe ""
            vcsInfo.path shouldBe "path"
        }
    }

    "Merging VcsInfo" should {
        "ignore empty information" {
            val inputA = VcsInfo(
                    provider = "",
                    url = "",
                    revision = "",
                    path = ""
            )

            val inputB = VcsInfo(
                    provider = "provider",
                    url = "url",
                    revision = "revision",
                    path = "path"
            )

            val output = VcsInfo(
                    provider = "provider",
                    url = "url",
                    revision = "revision",
                    path = "path"
            )

            inputA.merge(inputB) shouldBe output
        }

        "prefer provider spelling that matches VCS class names" {
            val inputA = VcsInfo(
                    provider = "Git",
                    url = "",
                    revision = "",
                    path = ""
            )

            val inputB = VcsInfo(
                    provider = "git",
                    url = "",
                    revision = "",
                    path = ""
            )

            inputA.merge(inputB).provider shouldBe "Git"
            inputB.merge(inputA).provider shouldBe "Git"
        }

        "prefer more complete information" {
            val inputA = VcsInfo(
                    provider = "Git",
                    url = "https://github.com/babel/babel.git",
                    revision = "master",
                    path = "packages/babel-cli"
            )

            val inputB = VcsInfo(
                    provider = "git",
                    url = "https://github.com/babel/babel/tree/master/packages/babel-cli.git",
                    revision = "",
                    path = ""
            )

            val output = VcsInfo(
                    provider = "Git",
                    url = "https://github.com/babel/babel.git",
                    revision = "master",
                    path = "packages/babel-cli"
            )

            inputA.merge(inputB) shouldBe output
        }

        "mix and match empty revision fields" {
            val inputA = VcsInfo(
                    provider = "Git",
                    url = "https://github.com/chalk/ansi-regex.git",
                    revision = "",
                    path = ""
            )

            val inputB = VcsInfo(
                    provider = "git",
                    url = "git+https://github.com/chalk/ansi-regex.git",
                    revision = "7c908e7b4eb6cd82bfe1295e33fdf6d166c7ed85",
                    path = ""
            )

            val output = VcsInfo(
                    provider = "Git",
                    url = "https://github.com/chalk/ansi-regex.git",
                    revision = "7c908e7b4eb6cd82bfe1295e33fdf6d166c7ed85",
                    path = ""
            )

            inputA.merge(inputB) shouldBe output
        }

        "mix and match empty revision and path fields" {
            val inputA = VcsInfo(
                    provider = "Git",
                    url = "ssh://git@github.com/EsotericSoftware/kryo.git",
                    revision = "",
                    path = "kryo-shaded"
            )

            val inputB = VcsInfo(
                    provider = "git",
                    url = "ssh://git@github.com/EsotericSoftware/kryo.git/kryo-shaded",
                    revision = "3a2eb7b3f3f04652e2dc40764c963f2bc99a92f5",
                    path = ""
            )

            val output = VcsInfo(
                    provider = "Git",
                    url = "ssh://git@github.com/EsotericSoftware/kryo.git",
                    revision = "3a2eb7b3f3f04652e2dc40764c963f2bc99a92f5",
                    path = "kryo-shaded"
            )

            inputA.merge(inputB) shouldBe output
        }
    }
})
