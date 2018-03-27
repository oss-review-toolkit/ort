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
import io.kotlintest.specs.WordSpec

class MergedAnalyzerResultTest : WordSpec() {
    init {
        "MergedResultsBuilder" should {
            "merge results from all files" {
                val builder = MergedResultsBuilder(true,
                        ScannedDirectoryDetails("name", "/absolute/path", VcsInfo.EMPTY))
                val project1 = createTestProject(1)
                val project2 = createTestProject(2)

                builder.addResult("/analyzer-results/project-1.yml",
                        AnalyzerResult(
                                true,
                                project1,
                                sortedSetOf(createTestPackage(1)),
                                emptyList()
                        )
                )
                builder.addResult("/analyzer-results/project-2.yml",
                        AnalyzerResult(
                                true,
                                project2,
                                sortedSetOf(createTestPackage(2), createTestPackage(1)),
                                listOf("Some error that occurred.")
                        )
                )

                val mergedResults = builder.build()

                mergedResults.errors.size shouldBe 2
                mergedResults.errors[project1.id]?.size shouldBe 0
                mergedResults.errors[project2.id]?.size shouldBe 1
                mergedResults.projectResultsFiles.size shouldBe 2
                mergedResults.packages.size shouldBe 2
                mergedResults.projects.size shouldBe 2
            }
        }
    }

    private fun createTestPackage(i: Int) = Package(
            id = Identifier(
                    provider = "provider-$i",
                    namespace = "namespace-$i",
                    name = "package-$i",
                    version = "version-$i"
            ),
            declaredLicenses = sortedSetOf(),
            description = "",
            homepageUrl = "",
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = RemoteArtifact.EMPTY,
            vcs = VcsInfo.EMPTY
    )

    private fun createTestProject(i: Int) = Project(
            id = Identifier(
                    provider = "provider-$i",
                    namespace = "namespace-$i",
                    name = "project-$i",
                    version = "version-$i"),
            declaredLicenses = sortedSetOf("license-$i"),
            aliases = emptyList(),
            vcs = VcsInfo.EMPTY,
            vcsProcessed = VcsInfo.EMPTY,
            homepageUrl = "",
            scopes = sortedSetOf(
                    Scope(
                            "scope-$i",
                            true,
                            sortedSetOf(createTestPackage(i).toReference())
                    )
            )
    )
}
