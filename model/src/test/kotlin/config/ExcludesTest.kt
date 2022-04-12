/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

@file:Suppress("MaxLineLength")

package org.ossreviewtoolkit.model.config

import io.kotest.core.spec.style.WordSpec
import io.kotest.core.test.TestCase
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.CuratedPackage
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.utils.core.Environment

class ExcludesTest : WordSpec() {
    private val id = Identifier("type", "namespace", "name", "version")

    private val pkg = CuratedPackage(pkg = Package.EMPTY.copy(id = id))

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
    private val scopeProject1 = Scope("scopeProject1", sortedSetOf(PackageReference(project1.id)))

    private val scopeExclude1 = ScopeExclude("scope1", ScopeExcludeReason.PROVIDED_DEPENDENCY_OF, "")
    private val scopeExclude2 = ScopeExclude("scope2", ScopeExcludeReason.PROVIDED_DEPENDENCY_OF, "")
    private val scopeExcludeProject1 = ScopeExclude("scopeProject1", ScopeExcludeReason.PROVIDED_DEPENDENCY_OF, "")

    private lateinit var ortResult: OrtResult

    override suspend fun beforeEach(testCase: TestCase) {
        ortResult = OrtResult(
            repository = Repository.EMPTY,
            analyzer = AnalyzerRun(
                environment = Environment(),
                config = AnalyzerConfiguration(allowDynamicVersions = false),
                result = AnalyzerResult.EMPTY
            )
        )
    }

    private fun setExcludes(paths: List<PathExclude> = emptyList(), scopes: List<ScopeExclude> = emptyList()) {
        setExcludes(Excludes(paths = paths, scopes = scopes))
    }

    private fun setExcludes(excludes: Excludes) {
        val config = ortResult.repository.config.copy(excludes = excludes)
        ortResult = ortResult.replaceConfig(config)
    }

    private fun setProjects(vararg projects: Project) {
        val packages = sortedSetOf<CuratedPackage>()
        if (id in projects.flatMap { ortResult.dependencyNavigator.projectDependencies(it) }) packages += pkg
        val analyzerResult = ortResult.analyzer!!.result.copy(
            projects = projects.toSortedSet(),
            packages = packages
        )
        ortResult = ortResult.copy(analyzer = ortResult.analyzer!!.copy(result = analyzerResult))
    }

    init {
        "findPathExcludes" should {
            "find the correct path excludes for a path" {
                val excludes = Excludes(paths = listOf(pathExclude1, pathExclude2, pathExclude3, pathExclude4))

                with(excludes) {
                    findPathExcludes("") should beEmpty()
                    findPathExcludes("path1") should containExactly(pathExclude1)
                    findPathExcludes("path2") should containExactly(pathExclude2)
                    findPathExcludes("test.ext") should containExactly(pathExclude3)
                    findPathExcludes("directory/test.ext") should containExactly(pathExclude3)
                    findPathExcludes("directory/file.ext") should containExactly(pathExclude3, pathExclude4)
                }
            }

            "find the correct path excludes for a project" {
                val excludes = Excludes(paths = listOf(pathExclude1, pathExclude2, pathExclude3, pathExclude4))

                setProjects(project1, project2, project3)

                with(excludes) {
                    findPathExcludes(project1, ortResult) should containExactly(pathExclude1)
                    findPathExcludes(project2, ortResult) should containExactly(pathExclude2)
                    findPathExcludes(project3, ortResult) should beEmpty()
                }
            }
        }

        "findScopeExcludes" should {
            "return an empty list if there are no matching scope excludes" {
                val excludes = Excludes(scopes = listOf(scopeExclude2))

                excludes.findScopeExcludes(scope1.name) should beEmpty()
            }

            "find the correct scope excludes" {
                val excludes = Excludes(
                    scopes = listOf(scopeExclude1, scopeExclude2)
                )

                val scopeExcludes = excludes.findScopeExcludes(scope1.name)

                scopeExcludes should containExactly(scopeExclude1)
            }
        }

        "isExcluded" should {
            "return false if the project is not found" {
                setProjects(
                    project1.copy(scopeDependencies = sortedSetOf(scope1))
                )

                ortResult.isExcluded(project2.id) shouldBe false
            }

            "return false if the package is not found" {
                setProjects(
                    project1
                )

                ortResult.isExcluded(id) shouldBe false
            }

            "return false if the package is not excluded" {
                setProjects(
                    project1.copy(scopeDependencies = sortedSetOf(scope1))
                )

                ortResult.isExcluded(id) shouldBe false
            }

            "return true if all projects depending on a package are excluded by path excludes" {
                setProjects(
                    project1.copy(scopeDependencies = sortedSetOf(scope1)),
                    project2.copy(scopeDependencies = sortedSetOf(scope2))
                )

                setExcludes(
                    paths = listOf(pathExclude1, pathExclude2)
                )

                ortResult.isExcluded(id) shouldBe true
            }

            "return false if only part of the projects depending on a package are excluded by path excludes" {
                setProjects(
                    project1.copy(scopeDependencies = sortedSetOf(scope1)),
                    project2.copy(scopeDependencies = sortedSetOf(scope2))
                )

                setExcludes(
                    paths = listOf(pathExclude1)
                )

                ortResult.isExcluded(id) shouldBe false
            }

            "return true if all scopes containing the package are excluded by scope excludes" {
                setProjects(
                    project1.copy(scopeDependencies = sortedSetOf(scope1)),
                    project2.copy(scopeDependencies = sortedSetOf(scope2))
                )

                setExcludes(
                    scopes = listOf(scopeExclude1, scopeExclude2)
                )

                ortResult.isExcluded(id) shouldBe true
            }

            "return false if only part of the scopes containing the package are excluded by scope excludes" {
                setProjects(
                    project1.copy(scopeDependencies = sortedSetOf(scope1)),
                    project2.copy(scopeDependencies = sortedSetOf(scope2))
                )

                setExcludes(
                    scopes = listOf(scopeExclude1)
                )

                ortResult.isExcluded(id) shouldBe false
            }

            "return true if all dependencies on the package are excluded by path or scope excludes" {
                setProjects(
                    project1.copy(scopeDependencies = sortedSetOf(scope1)),
                    project2.copy(scopeDependencies = sortedSetOf(scope2))
                )

                setExcludes(
                    paths = listOf(pathExclude1),
                    scopes = listOf(scopeExclude2)
                )

                ortResult.isExcluded(id) shouldBe true
            }

            "return true if a project is excluded by path excludes" {
                setProjects(
                    project1
                )

                setExcludes(
                    paths = listOf(pathExclude1)
                )

                ortResult.isExcluded(project1.id) shouldBe true
            }

            "return true if a project and all dependencies on the project are excluded by path excludes" {
                setProjects(
                    project1,
                    project2.copy(scopeDependencies = sortedSetOf(scopeProject1))
                )

                setExcludes(
                    paths = listOf(pathExclude1, pathExclude2)
                )

                ortResult.isExcluded(project1.id) shouldBe true
            }

            "return true if a project is excluded by path excludes and all dependencies on the project are excluded by scope excludes" {
                setProjects(
                    project1,
                    project2.copy(scopeDependencies = sortedSetOf(scopeProject1))
                )

                setExcludes(
                    paths = listOf(pathExclude1),
                    scopes = listOf(scopeExcludeProject1)
                )

                ortResult.isExcluded(project1.id) shouldBe true
            }

            "return false if a project is excluded by path excludes but not all dependencies on the project are excluded" {
                setProjects(
                    project1,
                    project2.copy(scopeDependencies = sortedSetOf(scopeProject1))
                )

                setExcludes(
                    paths = listOf(pathExclude1)
                )

                ortResult.isExcluded(project1.id) shouldBe false
            }

            "return false if a project is not excluded but all dependencies on the project are excluded" {
                setProjects(
                    project1,
                    project2.copy(scopeDependencies = sortedSetOf(scopeProject1))
                )

                setExcludes(
                    scopes = listOf(scopeExcludeProject1)
                )

                ortResult.isExcluded(project1.id) shouldBe false
            }
        }

        "isPackageExcluded" should {
            "return false if the package is not found" {
                setProjects(
                    project1
                )

                ortResult.isPackageExcluded(id) shouldBe false
            }

            "return false if the package is not excluded" {
                setProjects(
                    project1.copy(scopeDependencies = sortedSetOf(scope1))
                )

                ortResult.isPackageExcluded(id) shouldBe false
            }

            "return true if all projects depending on a package are excluded by path excludes" {
                setProjects(
                    project1.copy(scopeDependencies = sortedSetOf(scope1)),
                    project2.copy(scopeDependencies = sortedSetOf(scope2))
                )

                setExcludes(
                    paths = listOf(pathExclude1, pathExclude2)
                )

                ortResult.isPackageExcluded(id) shouldBe true
            }

            "return false if only part of the projects depending on a package are excluded by path excludes" {
                setProjects(
                    project1.copy(scopeDependencies = sortedSetOf(scope1)),
                    project2.copy(scopeDependencies = sortedSetOf(scope2))
                )

                setExcludes(
                    paths = listOf(pathExclude1)
                )

                ortResult.isPackageExcluded(id) shouldBe false
            }

            "return true if all scopes containing the package are excluded by scope excludes" {
                setProjects(
                    project1.copy(scopeDependencies = sortedSetOf(scope1)),
                    project2.copy(scopeDependencies = sortedSetOf(scope2))
                )

                setExcludes(
                    scopes = listOf(scopeExclude1, scopeExclude2)
                )

                ortResult.isPackageExcluded(id) shouldBe true
            }

            "return false if only part of the scopes containing the package are excluded by scope excludes" {
                setProjects(
                    project1.copy(scopeDependencies = sortedSetOf(scope1)),
                    project2.copy(scopeDependencies = sortedSetOf(scope2))
                )

                setExcludes(
                    scopes = listOf(scopeExclude1)
                )

                ortResult.isPackageExcluded(id) shouldBe false
            }

            "return true if all dependencies on the package are excluded by path or scope excludes" {
                setProjects(
                    project1.copy(scopeDependencies = sortedSetOf(scope1)),
                    project2.copy(scopeDependencies = sortedSetOf(scope2))
                )

                setExcludes(
                    paths = listOf(pathExclude1),
                    scopes = listOf(scopeExclude2)
                )

                ortResult.isPackageExcluded(id) shouldBe true
            }

            "return true if all dependencies on the project are excluded" {
                setProjects(
                    project1,
                    project2.copy(scopeDependencies = sortedSetOf(scopeProject1, scopeProject1.copy(name = "scope")))
                )

                setExcludes(
                    scopes = listOf(scopeExcludeProject1, scopeExcludeProject1.copy(pattern = "scope"))
                )

                ortResult.isPackageExcluded(project1.id) shouldBe true
            }

            "return false if not all dependencies on the project are excluded" {
                setProjects(
                    project1,
                    project2.copy(scopeDependencies = sortedSetOf(scopeProject1, scopeProject1.copy(name = "scope")))
                )

                setExcludes(
                    scopes = listOf(scopeExcludeProject1)
                )

                ortResult.isPackageExcluded(project1.id) shouldBe false
            }

            "return false if no dependencies on the project are excluded" {
                setProjects(
                    project1,
                    project2.copy(scopeDependencies = sortedSetOf(scopeProject1))
                )

                ortResult.isPackageExcluded(project1.id) shouldBe false
            }

            "return false if there are no dependencies on the project" {
                setProjects(
                    project1,
                    project2
                )

                ortResult.isPackageExcluded(project1.id) shouldBe false
            }
        }

        "isPathExcluded" should {
            "return true if any path exclude matches a file" {
                val excludes = Excludes(paths = listOf(pathExclude1, pathExclude2))

                with(excludes) {
                    isPathExcluded("path1") shouldBe true
                    isPathExcluded("path2") shouldBe true
                }
            }

            "return false if no path exclude matches a file" {
                val excludes = Excludes(paths = listOf(pathExclude1, pathExclude2))

                with(excludes) {
                    isPathExcluded("") shouldBe false
                    isPathExcluded("path1/file") shouldBe false
                    isPathExcluded("path3") shouldBe false
                }
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

            "return false if the definition file path is not matched by a path exclude" {
                setExcludes(Excludes(paths = listOf(pathExclude2)))
                setProjects(project1)

                ortResult.isProjectExcluded(project1.id) shouldBe false
            }

            "return false if there are no path excludes" {
                setProjects(project1)

                ortResult.isProjectExcluded(project1.id) shouldBe false
            }
        }

        "isScopeExcluded" should {
            "return true if the scope is excluded" {
                val excludes = Excludes(scopes = listOf(scopeExclude1))

                excludes.isScopeExcluded(scope1.name) shouldBe true
            }

            "return true if the scope is excluded using a regex" {
                val excludes = Excludes(scopes = listOf(scopeExclude1.copy(pattern = "sc.*")))

                excludes.isScopeExcluded(scope1.name) shouldBe true
            }

            "return false if the scope is not excluded" {
                val excludes = Excludes()

                excludes.isScopeExcluded(scope1.name) shouldBe false
            }
        }
    }
}
