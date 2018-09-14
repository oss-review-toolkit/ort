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

package com.here.ort.model.config

import com.here.ort.model.AnalyzerResult
import com.here.ort.model.Identifier
import com.here.ort.model.PackageReference
import com.here.ort.model.Project
import com.here.ort.model.Scope

import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

class ExcludesTest : WordSpec() {
    init {
        "findProjectExclude" should {
            val project = Project.EMPTY.copy(definitionFilePath = "path2")

            "return null if there is no matching project exclude" {
                val excludes = Excludes(
                        projects = listOf(
                                ProjectExclude("path1", ProjectExcludeReason.BUILD_TOOL_OF, comment = "comment"),
                                ProjectExclude("path3", ProjectExcludeReason.BUILD_TOOL_OF, comment = "comment")
                        )
                )

                excludes.findProjectExclude(project) shouldBe null
            }

            "find the correct project exclude" {
                val projectExclude = ProjectExclude("path2", ProjectExcludeReason.BUILD_TOOL_OF, comment = "comment")

                val excludes = Excludes(
                        projects = listOf(
                                ProjectExclude("path1", ProjectExcludeReason.BUILD_TOOL_OF, comment = "comment"),
                                projectExclude,
                                ProjectExclude("path3", ProjectExcludeReason.BUILD_TOOL_OF, comment = "comment")
                        )
                )

                excludes.findProjectExclude(project) shouldBe projectExclude
            }
        }

        "isPackageExcluded" should {
            val id = Identifier("provider", "namespace", "name", "version")
            val projectId1 = Identifier("provider", "namespace", "project1", "version")
            val projectId2 = Identifier("provider", "namespace", "project2", "version")

            "return true if the package does not appear in the analyzer result" {
                val excludes = Excludes()

                val analyzerResult = AnalyzerResult(
                        projects = sortedSetOf(),
                        packages = sortedSetOf(),
                        errors = sortedMapOf()
                )

                excludes.isPackageExcluded(id, analyzerResult) shouldBe true
            }

            "return true if all occurrences of the package are excluded" {
                val excludes = Excludes(
                        projects = listOf(ProjectExclude("path1", ProjectExcludeReason.BUILD_TOOL_OF, "comment")),
                        scopes = listOf(ScopeExclude(Regex("testScope"), ScopeExcludeReason.PROVIDED_BY, "comment"))
                )

                val analyzerResult = AnalyzerResult(
                        projects = sortedSetOf(
                                Project.EMPTY.copy(id = projectId1, definitionFilePath = "path1", scopes = sortedSetOf(
                                        Scope("scope", sortedSetOf(PackageReference(id, sortedSetOf()))))),
                                Project.EMPTY.copy(id = projectId2, definitionFilePath = "path2", scopes = sortedSetOf(
                                        Scope("testScope", sortedSetOf(PackageReference(id, sortedSetOf())))
                                ))
                        ),
                        packages = sortedSetOf(),
                        errors = sortedMapOf()
                )

                excludes.isPackageExcluded(id, analyzerResult) shouldBe true
            }

            "return false if not all occurrences of the package are excluded" {
                val excludes = Excludes(
                        projects = listOf(ProjectExclude("path1", ProjectExcludeReason.BUILD_TOOL_OF, "comment")),
                        scopes = listOf(ScopeExclude(Regex("testScope"), ScopeExcludeReason.PROVIDED_BY, "comment"))
                )

                val analyzerResult = AnalyzerResult(
                        projects = sortedSetOf(
                                Project.EMPTY.copy(id = projectId1, definitionFilePath = "path1", scopes = sortedSetOf(
                                        Scope("scope", sortedSetOf(PackageReference(id, sortedSetOf()))))),
                                Project.EMPTY.copy(id = projectId2, definitionFilePath = "path2", scopes = sortedSetOf(
                                        Scope("scope", sortedSetOf(PackageReference(id, sortedSetOf()))),
                                        Scope("testScope", sortedSetOf(PackageReference(id, sortedSetOf())))
                                ))
                        ),
                        packages = sortedSetOf(),
                        errors = sortedMapOf()
                )

                excludes.isPackageExcluded(id, analyzerResult) shouldBe false
            }

            "return false if no occurrences of the package are excluded" {
                val excludes = Excludes()

                val analyzerResult = AnalyzerResult(
                        projects = sortedSetOf(
                                Project.EMPTY.copy(id = projectId1, definitionFilePath = "path1", scopes = sortedSetOf(
                                        Scope("scope", sortedSetOf(PackageReference(id, sortedSetOf()))))),
                                Project.EMPTY.copy(id = projectId2, definitionFilePath = "path2", scopes = sortedSetOf(
                                        Scope("testScope", sortedSetOf(PackageReference(id, sortedSetOf())))
                                ))
                        ),
                        packages = sortedSetOf(),
                        errors = sortedMapOf()
                )

                excludes.isPackageExcluded(id, analyzerResult) shouldBe false
            }
        }

        "isProjectExcluded" should {
            "return true if the project is completely excluded" {
                val excludes = Excludes(
                        projects = listOf(
                                ProjectExclude("path1", ProjectExcludeReason.BUILD_TOOL_OF, "comment")
                        )
                )

                val project = Project.EMPTY.copy(definitionFilePath = "path1")

                excludes.isProjectExcluded(project) shouldBe true
            }

            "return false if only scopes of the project are excluded" {
                val excludes = Excludes(
                        projects = listOf(
                                ProjectExclude(path = "path1", scopes = listOf(
                                        ScopeExclude(Regex("scope"), ScopeExcludeReason.PROVIDED_BY, "comment")
                                ))
                        )
                )

                val project = Project.EMPTY.copy(definitionFilePath = "path1")

                excludes.isProjectExcluded(project) shouldBe false
            }

            "return false if the project is not in the list of project excludes" {
                val excludes = Excludes()

                val project = Project.EMPTY.copy(definitionFilePath = "path1")

                excludes.isProjectExcluded(project) shouldBe false
            }
        }

        "isScopeExcluded" should {
            val scope = Scope(name = "scope", dependencies = sortedSetOf())
            val project = Project.EMPTY.copy(definitionFilePath = "path1")

            "return true if the scope is excluded in the project exclude" {
                val excludes = Excludes(
                        projects = listOf(
                                ProjectExclude("path1", ProjectExcludeReason.BUILD_TOOL_OF, "comment",
                                        listOf(ScopeExclude(Regex("scope"), ScopeExcludeReason.PROVIDED_BY, "comment")))
                        )
                )

                excludes.isScopeExcluded(scope, project) shouldBe true
            }

            "return true if the scope is excluded globally" {
                val excludes = Excludes(
                        scopes = listOf(ScopeExclude(Regex("scope"), ScopeExcludeReason.PROVIDED_BY, "comment"))
                )

                excludes.isScopeExcluded(scope, project) shouldBe true
            }

            "return true if the scope is excluded using a regex" {
                val excludes = Excludes(
                        scopes = listOf(ScopeExclude(Regex("sc.*"), ScopeExcludeReason.PROVIDED_BY, "comment"))
                )

                excludes.isScopeExcluded(scope, project) shouldBe true
            }

            "return false if the scope is not excluded" {
                val excludes = Excludes()

                excludes.isScopeExcluded(scope, project) shouldBe false
            }
        }
    }
}
