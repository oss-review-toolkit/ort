/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

class VcsInfoTest : StringSpec({
    "Deserializing VcsInfo" should {
        "work when all fields are given" {
            val yaml = """
                ---
                type: "type"
                url: "url"
                revision: "revision"
                path: "path"
                """.trimIndent()

            println(yaml)

            val vcsInfo = yamlMapper.readValue(yaml, VcsInfo::class.java)

            vcsInfo.type shouldBe "type"
            vcsInfo.url shouldBe "url"
            vcsInfo.revision shouldBe "revision"
            vcsInfo.path shouldBe "path"
        }

        "assign empty strings to missing fields when only type is set" {
            val yaml = """
                ---
                type: "type"
                """.trimIndent()

            val vcsInfo = yamlMapper.readValue(yaml, VcsInfo::class.java)

            vcsInfo.type shouldBe "type"
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

            vcsInfo.type shouldBe ""
            vcsInfo.url shouldBe ""
            vcsInfo.revision shouldBe ""
            vcsInfo.path shouldBe "path"
        }
    }

    "Merging VcsInfo" should {
        "ignore empty information" {
            val inputA = VcsInfo(
                    type = "",
                    url = "",
                    revision = ""
            )

            val inputB = VcsInfo(
                    type = "type",
                    url = "url",
                    revision = "revision",
                    resolvedRevision = "resolvedRevision",
                    path = "path"
            )

            val output = VcsInfo(
                    type = "type",
                    url = "url",
                    revision = "revision",
                    resolvedRevision = "resolvedRevision",
                    path = "path"
            )

            inputA.merge(inputB) shouldBe output
        }

        "prefer more complete information for GitHub" {
            val inputA = VcsInfo(
                    type = "Git",
                    url = "https://github.com/babel/babel.git",
                    revision = "master",
                    path = "packages/babel-cli"
            )

            val inputB = VcsInfo(
                    type = "git",
                    url = "https://github.com/babel/babel/tree/master/packages/babel-cli.git",
                    revision = ""
            )

            val output = VcsInfo(
                    type = "Git",
                    url = "https://github.com/babel/babel.git",
                    revision = "master",
                    path = "packages/babel-cli"
            )

            inputA.merge(inputB) shouldBe output
        }

        "prefer more complete information for GitLab" {
            val inputA = VcsInfo(
                    type = "",
                    url = "https://gitlab.com/rich-harris/rollup-plugin-buble.git",
                    revision = ""
            )

            val inputB = VcsInfo(
                    type = "git",
                    url = "git+https://gitlab.com/rich-harris/rollup-plugin-buble.git",
                    revision = "9928a569351a80c2f7dc065f61085954daed5312"
            )

            val output = VcsInfo(
                    type = "git",
                    url = "https://gitlab.com/rich-harris/rollup-plugin-buble.git",
                    revision = "9928a569351a80c2f7dc065f61085954daed5312"
            )

            inputA.merge(inputB) shouldBe output
        }

        "mix and match empty revision fields" {
            val inputA = VcsInfo(
                    type = "Git",
                    url = "https://github.com/chalk/ansi-regex.git",
                    revision = ""
            )

            val inputB = VcsInfo(
                    type = "git",
                    url = "git+https://github.com/chalk/ansi-regex.git",
                    revision = "7c908e7b4eb6cd82bfe1295e33fdf6d166c7ed85"
            )

            val output = VcsInfo(
                    type = "Git",
                    url = "https://github.com/chalk/ansi-regex.git",
                    revision = "7c908e7b4eb6cd82bfe1295e33fdf6d166c7ed85"
            )

            inputA.merge(inputB) shouldBe output
        }

        "mix and match empty revision and path fields" {
            val inputA = VcsInfo(
                    type = "Git",
                    url = "ssh://git@github.com/EsotericSoftware/kryo.git",
                    revision = "",
                    path = "kryo-shaded"
            )

            val inputB = VcsInfo(
                    type = "git",
                    url = "ssh://git@github.com/EsotericSoftware/kryo.git/kryo-shaded",
                    revision = "3a2eb7b3f3f04652e2dc40764c963f2bc99a92f5"
            )

            val output = VcsInfo(
                    type = "Git",
                    url = "ssh://git@github.com/EsotericSoftware/kryo.git",
                    revision = "3a2eb7b3f3f04652e2dc40764c963f2bc99a92f5",
                    path = "kryo-shaded"
            )

            inputA.merge(inputB) shouldBe output
        }
    }
})
