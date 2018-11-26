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

import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

import java.io.File

class ProjectTest : WordSpec({
    "collectDependencies" should {
        "get all dependencies by default" {
            val expectedDependencies = listOf(
                    "Maven:junit:junit:4.12",
                    "Maven:org.apache.commons:commons-lang3:3.5",
                    "Maven:org.apache.commons:commons-text:1.1",
                    "Maven:org.apache.struts:struts2-assembly:2.5.14.1",
                    "Maven:org.hamcrest:hamcrest-core:1.3"
            )

            val analyzerResultsFile =
                    File("../analyzer/src/funTest/assets/projects/synthetic/gradle-expected-output-lib.yml")
            val project = analyzerResultsFile.readValue<ProjectAnalyzerResult>().project

            project.collectDependencies().map { it.id.toString() } shouldBe expectedDependencies
        }

        "get no dependencies for a depth of 0" {
            val analyzerResultsFile =
                    File("../analyzer/src/funTest/assets/projects/synthetic/gradle-expected-output-lib.yml")
            val project = analyzerResultsFile.readValue<ProjectAnalyzerResult>().project

            project.collectDependencies(maxDepth = 0).map { it.id.toString() } shouldBe emptyList()
        }

        "get only direct dependencies for a depth of 1" {
            val expectedDependencies = listOf(
                    "Maven:junit:junit:4.12",
                    "Maven:org.apache.commons:commons-text:1.1",
                    "Maven:org.apache.struts:struts2-assembly:2.5.14.1"
            )

            val analyzerResultsFile =
                    File("../analyzer/src/funTest/assets/projects/synthetic/gradle-expected-output-lib.yml")
            val project = analyzerResultsFile.readValue<ProjectAnalyzerResult>().project

            project.collectDependencies(maxDepth = 1).map { it.id.toString() } shouldBe expectedDependencies
        }
    }
})
