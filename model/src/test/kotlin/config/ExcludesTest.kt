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

package org.ossreviewtoolkit.model.config

import io.kotest.core.spec.style.WordSpec
import io.kotest.core.test.TestCase
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.contain
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.CuratedPackage
import org.ossreviewtoolkit.model.Environment
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.Scope

class ExcludesTest : WordSpec() {
    private val id = Identifier("type", "namespace", "name", "version")

    private val projectId1 = id.copy(name = "project1")
    private val projectId2 = id.copy(name = "project2")
    private val projectId3 = id.copy(name = "project3")

    private val project1 = Project.EMPTY.copy(id = projectId1, definitionFilePath = "path1")
    private val project2 = Project.EMPTY.copy(id = projectId2, definitionFilePath = "path2")
    private val project3 = Project.EMPTY.copy(id = projectId3, definitionFilePath = "path3")

    private val pathExclude1 = PathExclude("path1", PathExcludeReason.BUILD_TOOL_OF, "")
    private val pathExclude2 = PathExclude("path2", PathExcludeReason.BUILD_TOOL_OF, "")
    private val pathExclude3 = PathExclude("**.ext", PathExcludeReason.BUILD_TOOL_OF, "")
    private val pathExclude4 = PathExclude("**/file.ext", PathExcludeReason.BUILD_TOOL_OF, "")

    private val scope1 = Scope("scope1", sortedSetOf(PackageReference(id)))
    private val scope2 = Scope("scope2", sortedSetOf(PackageReference(id)))

    private val scopeExclude1 = ScopeExclude("scope1", ScopeExcludeReason.PROVIDED_DEPENDENCY_OF, "")
    private val scopeExclude2 = ScopeExclude("scope2", ScopeExcludeReason.PROVIDED_DEPENDENCY_OF, "")

    private lateinit var ortResult: OrtResult

    override fun beforeTest(testCase: TestCase) {
        ortResult = OrtResult(
            repository = Repository.EMPTY,
            analyzer = AnalyzerRun(
                environment = Environment(),
                config = AnalyzerConfiguration(ignoreToolVersions = false, allowDynamicVersions = false),
                result = AnalyzerResult.EMPTY.copy(
                    packages = sortedSetOf(
                        CuratedPackage(pkg = Package.EMPTY.copy(id = id))
                    )
                )
            )
        )
    }

    private fun setExcludes(excludes: Excludes) {
        val config = ortResult.repository.config.copy(excludes = excludes)
        ortResult = ortResult.replaceConfig(config)
    }

    private fun setProjects(vararg projects: Project) {
        val analyzerResult = ortResult.analyzer!!.result.copy(projects = projects.toSortedSet())
        ortResult = ortResult.copy(analyzer = ortResult.analyzer!!.copy(result = analyzerResult))
    }

    init {
        "findPathExcludes" should {
            "find the correct path excludes for a path" {
                val excludes = Excludes(paths = listOf(pathExclude1, pathExclude2, pathExclude3, pathExclude4))

                excludes.findPathExcludes("") should beEmpty()
                excludes.findPathExcludes("path1") should containExactly(pathExclude1)
                excludes.findPathExcludes("path2") should containExactly(pathExclude2)
                excludes.findPathExcludes("test.ext") should containExactly(pathExclude3)
                excludes.findPathExcludes("directory/test.ext") should containExactly(pathExclude3)
                excludes.findPathExcludes("directory/file.ext") should containExactly(pathExclude3, pathExclude4)
            }

            "find the correct path excludes for a project" {
                val excludes = Excludes(paths = listOf(pathExclude1, pathExclude2, pathExclude3, pathExclude4))

                setProjects(project1, project2, project3)

                excludes.findPathExcludes(project1, ortResult) should containExactly(pathExclude1)
                excludes.findPathExcludes(project2, ortResult) should containExactly(pathExclude2)
                excludes.findPathExcludes(project3, ortResult) should beEmpty()
            }
        }

        "findScopeExcludes" should {
            "return an empty list if there are no matching scope excludes" {
                val excludes = Excludes(scopes = listOf(scopeExclude2))

                excludes.findScopeExcludes(scope1) should beEmpty()
            }

            "find the correct scope excludes" {
                val excludes = Excludes(
                    scopes = listOf(scopeExclude1, scopeExclude2)
                )

                val scopeExcludes = excludes.findScopeExcludes(scope1)

                scopeExcludes should haveSize(1)
                scopeExcludes should contain(scopeExclude1)
            }
        }

        "isPackageExcluded" should {
            "return true if the package does not appear in the analyzer result" {
                ortResult.isPackageExcluded(id) shouldBe true
            }

            "return true if all occurrences of the package are excluded" {
                setExcludes(
                    Excludes(
                        paths = listOf(pathExclude1),
                        scopes = listOf(scopeExclude2)
                    )
                )

                setProjects(
                    project1.copy(scopes = sortedSetOf(scope1)),
                    project2.copy(scopes = sortedSetOf(scope2))
                )

                ortResult.isPackageExcluded(id) shouldBe true
            }

            "return false if not all occurrences of the package are excluded" {
                setExcludes(
                    Excludes(
                        paths = listOf(pathExclude1),
                        scopes = listOf(scopeExclude2)
                    )
                )

                setProjects(
                    project1.copy(scopes = sortedSetOf(scope1)),
                    project2.copy(scopes = sortedSetOf(scope1, scope2))
                )

                ortResult.isPackageExcluded(id) shouldBe false
            }

            "return false if no occurrences of the package are excluded" {
                setProjects(
                    project1.copy(scopes = sortedSetOf(scope1)),
                    project2.copy(scopes = sortedSetOf(scope2))
                )

                ortResult.isPackageExcluded(id) shouldBe false
            }
        }

        "isPathExcluded" should {
            "return true if any path exclude matches a file" {
                val excludes = Excludes(paths = listOf(pathExclude1, pathExclude2))

                excludes.isPathExcluded("path1") shouldBe true
                excludes.isPathExcluded("path2") shouldBe true
            }

            "return false if no path exclude matches a file" {
                val excludes = Excludes(paths = listOf(pathExclude1, pathExclude2))

                excludes.isPathExcluded("") shouldBe false
                excludes.isPathExcluded("path1/file") shouldBe false
                excludes.isPathExcluded("path3") shouldBe false
            }

            "return false if no path exclude is defined" {
                val excludes = Excludes()

                excludes.isPathExcluded("path") shouldBe false
            }
        }

        "isProjectExcluded" should {
            "return true if the definition file path is matched by a path exclude" {
                setExcludes(Excludes(paths = listOf(pathExclude1)))
                setProjects(project1)

                ortResult.isProjectExcluded(project1.id) shouldBe true
            }

            "return false if nothing is excluded" {
                setProjects(project1)

                ortResult.isProjectExcluded(project1.id) shouldBe false
            }
        }

        "isScopeExcluded" should {
            "return true if the scope is excluded" {
                val excludes = Excludes(scopes = listOf(scopeExclude1))

                excludes.isScopeExcluded(scope1) shouldBe true
            }

            "return true if the scope is excluded using a regex" {
                val excludes = Excludes(scopes = listOf(scopeExclude1.copy(pattern = "sc.*")))

                excludes.isScopeExcluded(scope1) shouldBe true
            }

            "return false if the scope is not excluded" {
                val excludes = Excludes()

                excludes.isScopeExcluded(scope1) shouldBe false
            }
        }
    }
}
