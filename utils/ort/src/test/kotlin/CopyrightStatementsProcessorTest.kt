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

package org.ossreviewtoolkit.utils.ort

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.PropertyAccessor

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.model.yamlMapper

class CopyrightStatementsProcessorTest : WordSpec() {
    private val processor = CopyrightStatementsProcessor()

    init {
        "process" should {
            "return a result with items merged by owner and prefix, sorted by owner and year" {
                val input = File("src/test/assets/copyright-statements.txt").readLines()

                val result = processor.process(input).toYaml()

                val expectedResult = File("src/test/assets/copyright-statements-expected-output.yml").readText()
                result shouldBe expectedResult
            }
        }
    }
}

private fun CopyrightStatementsProcessor.Result.toYaml(): String =
    yamlMapper.copy()
        // Disable getter serialization without changing field serialization.
        .setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE)
        .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
        .writeValueAsString(this)
