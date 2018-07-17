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

package com.here.ort.analyzer

import com.here.ort.analyzer.managers.Stack
import com.here.ort.model.AnalyzerConfiguration
import com.here.ort.model.yamlMapper
import com.here.ort.utils.OS
import com.here.ort.utils.test.USER_DIR

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

class StackTest : StringSpec({
    val projectsDir = File("src/funTest/assets/projects")

    "Dependencies should be resolved correctly for quickcheck-state-machine" {
        val definitionFile = File(projectsDir, "external/quickcheck-state-machine/stack.yaml")

        val config = AnalyzerConfiguration(false, false)
        val result = Stack.create(config).resolveDependencies(USER_DIR, listOf(definitionFile))[definitionFile]
        val expectedOutput = if (OS.isWindows) {
            "external/quickcheck-state-machine-expected-output-win32.yml"
        } else {
            "external/quickcheck-state-machine-expected-output.yml"
        }
        val expectedResult = File(projectsDir, expectedOutput).readText()
        val actualResult = yamlMapper.writeValueAsString(result)

        actualResult shouldBe expectedResult
    }

    "Dependencies should be resolved correctly for quickcheck-state-machine-example" {
        val definitionFile = File(projectsDir, "external/quickcheck-state-machine/example/stack.yaml")

        val config = AnalyzerConfiguration(false, false)
        val result = Stack.create(config).resolveDependencies(USER_DIR, listOf(definitionFile))[definitionFile]
        val expectedOutput = if (OS.isWindows) {
            "external/quickcheck-state-machine-example-expected-output-win32.yml"
        } else {
            "external/quickcheck-state-machine-example-expected-output.yml"
        }
        val expectedResult = File(projectsDir, expectedOutput).readText()
        val actualResult = yamlMapper.writeValueAsString(result)

        actualResult shouldBe expectedResult
    }
})
