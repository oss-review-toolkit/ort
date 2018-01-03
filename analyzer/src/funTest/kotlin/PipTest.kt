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

package com.here.ort.analyzer

import com.here.ort.analyzer.managers.PIP
import com.here.ort.utils.yamlMapper

import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.WordSpec

import java.io.File

class PipTest : WordSpec({
    val workingDir = File("src/funTest/assets/projects/external")

    "setup.py dependencies" should {
        "be resolved correctly for spdx-tools-python" {
            val projectDir = File(workingDir, "spdx-tools-python")
            val definitionFile = File(projectDir, "setup.py")

            val result = PIP.create().resolveDependencies(projectDir, listOf(definitionFile))[definitionFile]
            val expectedResult = File(workingDir, "spdx-tools-python-expected-output.yml").readText()

            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }
    }

    "requirements.txt dependencies" should {
        "be resolved correctly for example-python-flask" {
            val projectDir = File(workingDir, "example-python-flask")
            val definitionFile = File(projectDir, "requirements.txt")

            val result = PIP.create().resolveDependencies(projectDir, listOf(definitionFile))[definitionFile]
            val expectedResult = File(workingDir, "example-python-flask-expected-output.yml").readText()

            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }
    }
})
