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

import io.kotlintest.matchers.beEmpty
import io.kotlintest.matchers.collections.contain
import io.kotlintest.matchers.collections.containOnlyNulls
import io.kotlintest.matchers.containAll
import io.kotlintest.matchers.haveSize
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

class ExcludesTest : WordSpec() {
    init {
        val id = Identifier("type", "namespace", "name", "version")

        val projectId1 = id.copy(name = "project1")
        val projectId2 = id.copy(name = "project2")
        val projectId3 = id.copy(name = "project3")

        val project1 = Project.EMPTY.copy(id = projectId1, definitionFilePath = "path1")
        val project2 = Project.EMPTY.copy(id = projectId2, definitionFilePath = "path2")
        val project3 = Project.EMPTY.copy(id = projectId3, definitionFilePath = "path3")

        val projectExclude1 = ProjectExclude("path1", ProjectExcludeReason.BUILD_TOOL_OF, "")
        val projectExclude2 = ProjectExclude("path2", ProjectExcludeReason.BUILD_TOOL_OF, "")
        val projectExclude3 = ProjectExclude("path3", ProjectExcludeReason.BUILD_TOOL_OF, "")

        val scope1 = Scope("scope1", sortedSetOf(PackageReference(id)))
        val scope2 = Scope("scope2", sortedSetOf(PackageReference(id)))

        val scopeExclude1 = ScopeExclude("scope1", ScopeExcludeReason.PROVIDED_BY, "")
        val scopeExclude2 = ScopeExclude("scope2", ScopeExcludeReason.PROVIDED_BY, "")

        val projectExcludeWithScopes1 = ProjectExclude("path1", scopes = listOf(scopeExclude1))
        val projectExcludeWithScopes2 = ProjectExclude("path2", scopes = listOf(scopeExclude2))

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

        "findScopeExcludes" should {
            "return an empty list if there are no matching scope excludes" {
                val excludes = Excludes(
                        projects = listOf(projectExcludeWithScopes2),
                        scopes = listOf(scopeExclude2)
                )

                excludes.findScopeExcludes(scope1, project1) should beEmpty()
            }

            "find the correct global scope excludes" {
                val excludes = Excludes(
                        scopes = listOf(scopeExclude1, scopeExclude2)
                )

                val scopeExcludes = excludes.findScopeExcludes(scope1, project1)

                scopeExcludes should haveSize(1)
                scopeExcludes should contain(scopeExclude1)
            }

            "find the correct project specific scope excludes" {
                val excludes = Excludes(
                        projects = listOf(projectExcludeWithScopes1, projectExcludeWithScopes2)
                )

                val scopeExcludes = excludes.findScopeExcludes(scope1, project1)

                scopeExcludes should haveSize(1)
                scopeExcludes should contain(scopeExclude1)
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

        "projectExcludesById" should {
            "return null values for projects without a project exclude" {
                val excludes = Excludes()

                val excludesById = excludes.projectExcludesById(setOf(project1, project2, project3))

                excludesById.keys should haveSize(3)
                excludesById.keys should containAll(projectId1, projectId2, projectId3)
                excludesById.values should containOnlyNulls()
            }

            "return the correct mapping of ids to project excludes" {
                val excludes = Excludes(
                        projects = listOf(projectExclude1, projectExclude2, projectExclude3)
                )

                val excludesById = excludes.projectExcludesById(setOf(project1, project2, project3))

                excludesById.keys should haveSize(3)
                excludesById[projectId1] shouldBe projectExclude1
                excludesById[projectId2] shouldBe projectExclude2
                excludesById[projectId3] shouldBe projectExclude3
            }

            "only return mappings for requested projects" {
                val excludes = Excludes(
                        projects = listOf(projectExclude1, projectExclude2, projectExclude3)
                )

                val excludesById = excludes.projectExcludesById(setOf(project1, project2))

                excludesById.keys should haveSize(2)
                excludesById[projectId1] shouldBe projectExclude1
                excludesById[projectId2] shouldBe projectExclude2
            }
        }

        "scopeExcludesByName" should {
            "return empty lists for scopes without a scope exclude" {
                val excludes = Excludes()

                val excludesByName = excludes.scopeExcludesByName(project1, listOf(scope1, scope2))

                excludesByName.keys should haveSize(2)
                excludesByName.keys should containAll(scope1.name, scope2.name)
                excludesByName[scope1.name]!! should beEmpty()
                excludesByName[scope2.name]!! should beEmpty()
            }

            "return the correct mapping of scope names to scope excludes" {
                val excludes = Excludes(
                        projects = listOf(projectExcludeWithScopes1, projectExcludeWithScopes2, projectExclude3),
                        scopes = listOf(scopeExclude2)
                )

                val excludesByName = excludes.scopeExcludesByName(project1, setOf(scope1, scope2))

                excludesByName.keys should haveSize(2)
                excludesByName.keys should containAll(scope1.name, scope2.name)
                excludesByName[scope1.name]!!.let {
                    it should haveSize(1)
                    it should contain(scopeExclude1)
                }
                excludesByName[scope2.name]!!.let {
                    it should haveSize(1)
                    it should contain(scopeExclude2)
                }
            }

            "only return mappings for requested scopes" {
                val excludes = Excludes(
                        projects = listOf(projectExcludeWithScopes1, projectExcludeWithScopes2)
                )

                val excludesByName = excludes.scopeExcludesByName(project1, listOf(scope1))

                excludesByName.keys should haveSize(1)
                excludesByName.keys should contain(scope1.name)
                excludesByName[scope1.name]!!.let {
                    it should haveSize(1)
                    it should contain(scopeExclude1)
                }
            }
        }
    }
}
