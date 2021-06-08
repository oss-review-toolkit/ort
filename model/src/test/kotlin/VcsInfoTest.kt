/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException
import com.fasterxml.jackson.module.kotlin.readValue

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containAll
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

class VcsInfoTest : WordSpec({
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

            val vcsInfo = yamlMapper.readValue<VcsInfo>(yaml)

            with(vcsInfo) {
                type shouldBe VcsType("type")
                url shouldBe "url"
                revision shouldBe "revision"
                path shouldBe "path"
            }
        }

        "assign empty strings to missing fields when only type is set" {
            val yaml = """
                ---
                type: "type"
                """.trimIndent()

            val vcsInfo = yamlMapper.readValue<VcsInfo>(yaml)

            with(vcsInfo) {
                type shouldBe VcsType("type")
                url shouldBe ""
                revision shouldBe ""
                path shouldBe ""
            }
        }

        "assign empty strings to missing fields when only path is set" {
            val yaml = """
                ---
                path: "path"
                """.trimIndent()

            val vcsInfo = yamlMapper.readValue<VcsInfo>(yaml)

            with(vcsInfo) {
                type shouldBe VcsType.UNKNOWN
                url shouldBe ""
                revision shouldBe ""
                path shouldBe "path"
            }
        }

        "fail if the input contains unknown fields" {
            val yaml = """
                ---
                type: "type"
                url: "url"
                revision: "revision"
                resolved_revision: "resolved_revision"
                path: "path"
                unknown: "unknown"
                """.trimIndent()

            val exception = shouldThrow<UnrecognizedPropertyException> {
                yamlMapper.readValue<VcsInfo>(yaml)
            }

            with(exception) {
                propertyName shouldBe "unknown"
                knownPropertyIds should containAll<Any>("type", "url", "revision", "resolved_revision", "path")
            }
        }
    }

    "Merging VcsInfo" should {
        "ignore empty information" {
            val inputA = VcsInfo(
                type = VcsType.UNKNOWN,
                url = "",
                revision = ""
            )

            val inputB = VcsInfo(
                type = VcsType("type"),
                url = "url",
                revision = "revision",
                path = "path"
            )

            val output = VcsInfo(
                type = VcsType("type"),
                url = "url",
                revision = "revision",
                path = "path"
            )

            inputA.merge(inputB) shouldBe output
        }

        "prefer more complete information for GitHub" {
            val inputA = VcsInfo(
                type = VcsType.GIT,
                url = "https://github.com/babel/babel.git",
                revision = "master",
                path = "packages/babel-cli"
            )

            val inputB = VcsInfo(
                type = VcsType.GIT,
                url = "https://github.com/babel/babel/tree/master/packages/babel-cli.git",
                revision = ""
            )

            val output = VcsInfo(
                type = VcsType.GIT,
                url = "https://github.com/babel/babel.git",
                revision = "master",
                path = "packages/babel-cli"
            )

            inputA.merge(inputB) shouldBe output
        }

        "prefer more complete information for GitLab" {
            val inputA = VcsInfo(
                type = VcsType.UNKNOWN,
                url = "https://gitlab.com/rich-harris/rollup-plugin-buble.git",
                revision = ""
            )

            val inputB = VcsInfo(
                type = VcsType.GIT,
                url = "git+https://gitlab.com/rich-harris/rollup-plugin-buble.git",
                revision = "9928a569351a80c2f7dc065f61085954daed5312"
            )

            val output = VcsInfo(
                type = VcsType.GIT,
                url = "https://gitlab.com/rich-harris/rollup-plugin-buble.git",
                revision = "9928a569351a80c2f7dc065f61085954daed5312"
            )

            inputA.merge(inputB) shouldBe output
        }

        "mix and match empty revision fields" {
            val inputA = VcsInfo(
                type = VcsType.GIT,
                url = "https://github.com/chalk/ansi-regex.git",
                revision = ""
            )

            val inputB = VcsInfo(
                type = VcsType.GIT,
                url = "git+https://github.com/chalk/ansi-regex.git",
                revision = "7c908e7b4eb6cd82bfe1295e33fdf6d166c7ed85"
            )

            val output = VcsInfo(
                type = VcsType.GIT,
                url = "https://github.com/chalk/ansi-regex.git",
                revision = "7c908e7b4eb6cd82bfe1295e33fdf6d166c7ed85"
            )

            inputA.merge(inputB) shouldBe output
        }

        "mix and match empty revision and path fields" {
            val inputA = VcsInfo(
                type = VcsType.GIT,
                url = "ssh://git@github.com/EsotericSoftware/kryo.git",
                revision = "",
                path = "kryo-shaded"
            )

            val inputB = VcsInfo(
                type = VcsType.GIT,
                url = "ssh://git@github.com/EsotericSoftware/kryo.git/kryo-shaded",
                revision = "3a2eb7b3f3f04652e2dc40764c963f2bc99a92f5"
            )

            val output = VcsInfo(
                type = VcsType.GIT,
                url = "ssh://git@github.com/EsotericSoftware/kryo.git",
                revision = "3a2eb7b3f3f04652e2dc40764c963f2bc99a92f5",
                path = "kryo-shaded"
            )

            inputA.merge(inputB) shouldBe output
        }
    }
})
