/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import io.kotlintest.matchers.beEmpty
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

import java.io.File
import java.time.Instant

private fun readAnalyzerResult(analyzerResultFilename: String): Project =
    File("../analyzer/src/funTest/assets/projects/synthetic")
        .resolve(analyzerResultFilename)
        .readValue<ProjectAnalyzerResult>().project

class ProjectTest : WordSpec({
    "collectDependencies" should {
        "get all dependencies by default" {
            val project = readAnalyzerResult("gradle-expected-output-lib.yml")

            val dependencies = project.collectDependencies().map { it.toCoordinates() }

            dependencies shouldContainExactlyInAnyOrder listOf(
                "Maven:junit:junit:4.12",
                "Maven:org.apache.commons:commons-lang3:3.5",
                "Maven:org.apache.commons:commons-text:1.1",
                "Maven:org.apache.struts:struts2-assembly:2.5.14.1",
                "Maven:org.hamcrest:hamcrest-core:1.3"
            )
        }

        "get no dependencies for a depth of 0" {
            val project = readAnalyzerResult("gradle-expected-output-lib.yml")

            val dependencies = project.collectDependencies(maxDepth = 0)

            dependencies should beEmpty()
        }

        "get only direct dependencies for a depth of 1" {
            val project = readAnalyzerResult("gradle-expected-output-lib.yml")

            val dependencies = project.collectDependencies(maxDepth = 1).map { it.toCoordinates() }

            dependencies shouldContainExactlyInAnyOrder listOf(
                "Maven:junit:junit:4.12",
                "Maven:org.apache.commons:commons-text:1.1",
                "Maven:org.apache.struts:struts2-assembly:2.5.14.1"
            )
        }
    }

    "collectIssues" should {
        "find all issues" {
            val project = readAnalyzerResult("gradle-expected-output-lib-without-repo.yml")

            val issues = project.collectIssues()

            issues shouldBe mapOf(
                Identifier("Unknown:org.apache.commons:commons-text:1.1") to setOf(
                    OrtIssue(
                        Instant.EPOCH,
                        "Gradle",
                        "Unresolved: ModuleVersionNotFoundException: Cannot resolve external dependency " +
                                "org.apache.commons:commons-text:1.1 because no repositories are defined."
                    )
                ),
                Identifier("Unknown:junit:junit:4.12") to setOf(
                    OrtIssue(
                        Instant.EPOCH,
                        "Gradle",
                        "Unresolved: ModuleVersionNotFoundException: Cannot resolve external dependency " +
                                "junit:junit:4.12 because no repositories are defined."
                    )
                )
            )
        }
    }
})
