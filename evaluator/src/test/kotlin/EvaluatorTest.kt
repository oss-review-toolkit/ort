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

package com.here.ort.evaluator

import com.here.ort.model.OrtResult
import com.here.ort.model.Repository
import com.here.ort.model.VcsInfo
import com.here.ort.model.config.RepositoryConfiguration

import io.kotlintest.matchers.beEmpty
import io.kotlintest.matchers.haveSize
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

class EvaluatorTest : WordSpec() {
    init {
        val ortResult = OrtResult(Repository(VcsInfo.EMPTY, VcsInfo.EMPTY, RepositoryConfiguration()))

        "checkSyntax" should {
            "succeed if the script can be compiled" {
                val script = javaClass.getResource("/rules/no_gpl_declared.kts").readText()

                val result = Evaluator(ortResult).checkSyntax(script)

                result shouldBe true
            }

            "fail if the script can not be compiled" {
                val result = Evaluator(ortResult).checkSyntax("""
                    broken script
                    """.trimIndent())

                result shouldBe false
            }
        }

        "evaluate" should {
            "return no errors for an empty script" {
                val result = Evaluator(ortResult).run("")

                result.errors should beEmpty()
            }

            "contain rule errors in the result" {
                val result = Evaluator(ortResult).run("""
                    evalErrors += OrtError(source = "source 1", message = "message 1")
                    evalErrors += OrtError(source = "source 2", message = "message 2")
                    """.trimIndent())

                result.errors should haveSize(2)
                result.errors[0].let {
                    it.source shouldBe "source 1"
                    it.message shouldBe "message 1"
                }
                result.errors[1].let {
                    it.source shouldBe "source 2"
                    it.message shouldBe "message 2"
                }
            }
        }
    }
}
