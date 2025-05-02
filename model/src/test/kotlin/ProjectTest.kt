/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

import io.mockk.mockk

import org.ossreviewtoolkit.utils.test.readResourceValue

private const val MANAGER = "MyManager"

private val projectId = Identifier("$MANAGER:my.example.org:my-project:1.0.0")

class ProjectTest : WordSpec({
    "init" should {
        "fail if both scopeDependencies and scopeNames are provided" {
            shouldThrow<IllegalArgumentException> {
                Project.EMPTY.copy(
                    scopeDependencies = setOf(mockk()),
                    scopeNames = setOf("test", "compile", "other")
                )
            }
        }
    }

    "scopes" should {
        "be initialized from scope dependencies" {
            val project = readResourceValue<ProjectAnalyzerResult>("/maven-expected-output-app.yml").project

            project.scopes shouldBe project.scopeDependencies
        }

        "be initialized to an empty set if no information is available" {
            val project = Project(
                id = projectId,
                definitionFilePath = "/some/path",
                declaredLicenses = emptySet(),
                vcs = VcsInfo.EMPTY,
                homepageUrl = "https//www.test-project.org"
            )

            project.scopes.shouldBeEmpty()
        }
    }
})
