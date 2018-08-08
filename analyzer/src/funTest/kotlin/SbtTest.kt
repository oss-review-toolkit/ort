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

import com.here.ort.analyzer.managers.SBT
import com.here.ort.downloader.vcs.Git
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.yamlMapper
import com.here.ort.utils.test.patchExpectedResult

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

class SbtTest : StringSpec({
    "Dependencies of the single 'directories' project should be detected correctly" {
        val projectName = "directories"
        val projectDir = File("src/funTest/assets/projects/external/$projectName").absoluteFile
        val expectedOutputFile = projectDir.resolveSibling("$projectName-expected-output.yml")

        // Clean any previously generated POM files / target directories.
        Git.run(projectDir, "clean", "-fd")

        val config = AnalyzerConfiguration(false, false, false)
        val ortResult = Analyzer().analyze(config, projectDir, listOf(SBT))

        val actualResult = yamlMapper.writeValueAsString(ortResult)
        val expectedResult = patchExpectedResult(expectedOutputFile)

        actualResult shouldBe expectedResult
    }

    "Dependencies of the 'sbt-multi-project-example' multi-project should be detected correctly" {
        val projectName = "sbt-multi-project-example"
        val projectDir = File("src/funTest/assets/projects/external/$projectName").absoluteFile
        val expectedOutputFile = File(projectDir.parentFile, "$projectName-expected-output.yml")

        // Clean any previously generated POM files / target directories.
        Git.run(projectDir, "clean", "-fd")

        val config = AnalyzerConfiguration(false, false, false)
        val ortResult = Analyzer().analyze(config, projectDir, listOf(SBT))

        val actualResult = yamlMapper.writeValueAsString(ortResult)
        val expectedResult = patchExpectedResult(expectedOutputFile)

        actualResult shouldBe expectedResult
    }
})
