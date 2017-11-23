/*
 * Copyright (c) 2017 HERE Europe B.V.
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

import com.here.ort.analyzer.managers.SBT
import com.here.ort.util.yamlMapper

import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.FreeSpec

import java.io.File

class SbtTest : FreeSpec({
    val sbt = SBT.create()

    "Dependencies of the" - {
        "external 'directories' project should be detected correctly" {
            val projectName = "directories"
            val projectDir = File("src/funTest/assets/projects/external/$projectName")

            val definitionFile = File(projectDir, "build.sbt")
            val expectedOutputFile = File(projectDir.parentFile, "$projectName-expected-output.yml")

            // Even if we do not explicit depend on the definitionFile, explicitly check for it before calling
            // resolveDependencies() to avoid potentially less readable errors from "sbt makePom". Similar for the
            // expected output file.
            definitionFile.isFile shouldBe true
            expectedOutputFile.isFile shouldBe true

            val resolutionResult = sbt.resolveDependencies(projectDir, listOf(definitionFile))

            // Because of the mapping from SBT to POM files we cannot use definitionFile as the key, so just ensure
            // there is exactly one entry to take.
            resolutionResult.size shouldBe 1

            val actualResult = yamlMapper.writeValueAsString(resolutionResult.values.first())
            val expectedResult = expectedOutputFile.readText()

            actualResult shouldBe expectedResult
        }
    }
})
