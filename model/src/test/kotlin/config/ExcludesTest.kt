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
        val id = Identifier("provider", "namespace", "name", "version")

        val projectId1 = id.copy(name = "project1")
        val projectId2 = id.copy(name = "project2")

        val project1 = Project.EMPTY.copy(id = projectId1, definitionFilePath = "path1")
        val project2 = Project.EMPTY.copy(id = projectId2, definitionFilePath = "path2")

        val projectExclude1 = ProjectExclude("path1", ProjectExcludeReason.BUILD_TOOL_OF, "")
        val projectExclude2 = ProjectExclude("path2", ProjectExcludeReason.BUILD_TOOL_OF, "")
        val projectExclude3 = ProjectExclude("path3", ProjectExcludeReason.BUILD_TOOL_OF, "")

        val scope1 = Scope("scope1", sortedSetOf(PackageReference(id, sortedSetOf())))
        val scope2 = Scope("scope2", sortedSetOf(PackageReference(id, sortedSetOf())))

        val scopeExclude1 = ScopeExclude("scope1", ScopeExcludeReason.PROVIDED_BY, "")
        val scopeExclude2 = ScopeExclude("scope2", ScopeExcludeReason.PROVIDED_BY, "")

        val projectExcludeWithScopes1 = ProjectExclude("path1", scopes = listOf(scopeExclude1))

        "findProjectExclude" should {
            "return null if there is no matching project exclude" {
                val excludes = Excludes(projects = listOf(projectExclude1, projectExclude3))

                excludes.findProjectExclude(project2) shouldBe null
            }

            "find the correct project exclude" {
                val excludes = Excludes(projects = listOf(projectExclude1, projectExclude2, projectExclude3))

                excludes.findProjectExclude(project2) shouldBe projectExclude2
            }
        }

        "isPackageExcluded" should {
            "return true if the package does not appear in the analyzer result" {
                Excludes().isPackageExcluded(id, AnalyzerResult.EMPTY) shouldBe true
            }

            "return true if all occurrences of the package are excluded" {
                val excludes = Excludes(
                        projects = listOf(projectExclude1),
                        scopes = listOf(scopeExclude2)
                )

                val analyzerResult = AnalyzerResult.EMPTY.copy(
                        projects = sortedSetOf(
                                project1.copy(scopes = sortedSetOf(scope1)),
                                project2.copy(scopes = sortedSetOf(scope2))
                        )
                )

                excludes.isPackageExcluded(id, analyzerResult) shouldBe true
            }

            "return false if not all occurrences of the package are excluded" {
                val excludes = Excludes(
                        projects = listOf(projectExclude1),
                        scopes = listOf(scopeExclude2)
                )

                val analyzerResult = AnalyzerResult.EMPTY.copy(
                        projects = sortedSetOf(
                                project1.copy(scopes = sortedSetOf(scope1)),
                                project2.copy(scopes = sortedSetOf(scope1, scope2))
                        )
                )

                excludes.isPackageExcluded(id, analyzerResult) shouldBe false
            }

            "return false if no occurrences of the package are excluded" {
                val excludes = Excludes()

                val analyzerResult = AnalyzerResult.EMPTY.copy(
                        projects = sortedSetOf(
                                project1.copy(scopes = sortedSetOf(scope1)),
                                project2.copy(scopes = sortedSetOf(scope2))
                        )
                )

                excludes.isPackageExcluded(id, analyzerResult) shouldBe false
            }
        }

        "isProjectExcluded" should {
            "return true if the project is completely excluded" {
                val excludes = Excludes(projects = listOf(projectExclude1))

                excludes.isProjectExcluded(project1) shouldBe true
            }

            "return false if only scopes of the project are excluded" {
                val excludes = Excludes(projects = listOf(projectExcludeWithScopes1))

                excludes.isProjectExcluded(project1) shouldBe false
            }

            "return false if the project is not in the list of project excludes" {
                val excludes = Excludes()

                excludes.isProjectExcluded(project1) shouldBe false
            }
        }

        "isScopeExcluded" should {
            "return true if the scope is excluded in the project exclude" {
                val excludes = Excludes(projects = listOf(projectExcludeWithScopes1))

                excludes.isScopeExcluded(scope1, project1) shouldBe true
            }

            "return true if the scope is excluded globally" {
                val excludes = Excludes(scopes = listOf(scopeExclude1))

                excludes.isScopeExcluded(scope1, project1) shouldBe true
            }

            "return true if the scope is excluded using a regex" {
                val excludes = Excludes(scopes = listOf(scopeExclude1.copy(name = Regex("sc.*"))))

                excludes.isScopeExcluded(scope1, project1) shouldBe true
            }

            "return false if the scope is not excluded" {
                val excludes = Excludes()

                excludes.isScopeExcluded(scope1, project1) shouldBe false
            }
        }
    }
}
