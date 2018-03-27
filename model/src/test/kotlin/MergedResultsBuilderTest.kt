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

package com.here.ort.model

import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec

class MergedResultsBuilderTest : StringSpec() {
    init {
        "MergedResultsBuilder merges results from all files" {
            val builder = MergedResultsBuilder(true, ScannedDirectoryDetails("test-project", "/some/path/test-project",
                    VcsInfo.EMPTY))
            val subProject1 = createTestSubProject(1)
            val subProject2 = createTestSubProject(2)

            builder.addResult("/some/other/path/analyzer-results-1.yml",
                    AnalyzerResult(
                            true,
                            subProject1,
                            sortedSetOf(createTestDependencyPkg(1)),
                            emptyList()
                    )
            )
            builder.addResult("/some/other/path/analyzer-results-2.yml",
                    AnalyzerResult(
                            true,
                            subProject2,
                            sortedSetOf(createTestDependencyPkg(2), createTestDependencyPkg(1)),
                            listOf("Some error that occurred.")
                    )
            )

            val mergedResults = builder.build()

            mergedResults.errors.size shouldBe 2
            mergedResults.errors[subProject1.id]?.size shouldBe 0
            mergedResults.errors[subProject2.id]?.size shouldBe 1
            mergedResults.projectResultsFiles.size shouldBe 2
            mergedResults.packages.size shouldBe 2
            mergedResults.projects.size shouldBe 2
        }
    }

    private fun createTestDependencyPkg(i: Int) = Package(
            id = Identifier(
                    provider = "Maven",
                    namespace = "",
                    name = "dependency-$i",
                    version = ""
            ),
            declaredLicenses = sortedSetOf(),
            description = "",
            homepageUrl = "",
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = RemoteArtifact.EMPTY,
            vcs = VcsInfo.EMPTY
    )

    private fun createTestSubProject(i: Int) = Project(
            id = Identifier(
                    provider = "Maven",
                    namespace = "",
                    name = "sub-project-$i",
                    version = ""),
            declaredLicenses = sortedSetOf("MIT"),
            aliases = emptyList(),
            vcs = VcsInfo.EMPTY,
            vcsProcessed = VcsInfo.EMPTY,
            homepageUrl = "",
            scopes = sortedSetOf(
                    Scope(
                            "testScope$i",
                            true,
                            sortedSetOf(createTestDependencyPkg(i).toReference())
                    )
            )
    )
}
