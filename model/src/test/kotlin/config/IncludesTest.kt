/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.CuratedPackage
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.Scope

class IncludesTest : WordSpec() {
    private val packageId = Identifier("type", "namespace", "name", "version")

    private val pkg = CuratedPackage(metadata = org.ossreviewtoolkit.model.Package.EMPTY.copy(id = packageId))

    private val projectId1 = packageId.copy(name = "project1")
    private val projectId2 = packageId.copy(name = "project2")
    private val project1 = Project.EMPTY.copy(id = projectId1, definitionFilePath = "path1")
    private val project2 = Project.EMPTY.copy(id = projectId2, definitionFilePath = "path2")

    private val pathInclude1 = PathInclude("path1", PathIncludeReason.SOURCE_OF, "")
    private val pathInclude2 = PathInclude("path2", PathIncludeReason.SOURCE_OF, "")
    private val pathInclude3 = PathInclude("path3", PathIncludeReason.SOURCE_OF, "")

    private val scope1 = Scope("scope1", setOf(PackageReference(packageId)))
    private val scope2 = Scope("scope2", setOf(PackageReference(packageId)))
    private val scopeProject1 = Scope("scopeProject1", setOf(PackageReference(project1.id)))

    private lateinit var ortResult: OrtResult

    override suspend fun beforeEach(testCase: TestCase) {
        ortResult = OrtResult(
            repository = Repository.EMPTY,
            analyzer = AnalyzerRun.EMPTY
        )
    }

    private fun setIncludes(paths: List<PathInclude> = emptyList()) {
        setIncludes(Includes(paths = paths))
    }

    private fun setIncludes(includes: Includes) {
        val config = ortResult.repository.config.copy(includes = includes)
        ortResult = ortResult.replaceConfig(config)
    }

    private fun setProjects(vararg projects: Project) {
        val packages = mutableSetOf<Package>()
        if (packageId in projects.flatMap { ortResult.dependencyNavigator.projectDependencies(it) }) packages += pkg.metadata
        val analyzerResult = ortResult.analyzer!!.result.copy(
            projects = projects.toSet(),
            packages = packages
        )
        ortResult = ortResult.copy(analyzer = ortResult.analyzer!!.copy(result = analyzerResult))
    }

    init {
        "isExcluded" should {
            "return false for a package if no includes are defined" {
                setProjects(
                    project1.copy(scopeDependencies = setOf(scope1))
                )

                ortResult.isExcluded(packageId) shouldBe false
            }

            "return true if some path includes are defined but all projects depending on a package are not included" {
                setProjects(
                    project1.copy(scopeDependencies = setOf(scope1)),
                    project2.copy(scopeDependencies = setOf(scope2))
                )

                setIncludes(
                    paths = listOf(PathInclude("someOtherPath", PathIncludeReason.SOURCE_OF, ""))
                )

                ortResult.isExcluded(packageId) shouldBe true
            }

            "return false if all projects depending on a package are included by path includes" {
                setProjects(
                    project1.copy(scopeDependencies = setOf(scope1)),
                    project2.copy(scopeDependencies = setOf(scope2))
                )

                setIncludes(
                    paths = listOf(pathInclude1, pathInclude2)
                )

                ortResult.isExcluded(packageId) shouldBe false
            }

            "return false if only part of the projects depending on a package are included by path includes" {
                setProjects(
                    project1.copy(scopeDependencies = setOf(scope1)),
                    project2.copy(scopeDependencies = setOf(scope2))
                )

                setIncludes(
                    paths = listOf(pathInclude1)
                )

                ortResult.isExcluded(packageId) shouldBe false
            }

            "return false if a project is included by path includes but not all dependencies on the project are included" {
                setProjects(
                    project1,
                    project2.copy(scopeDependencies = setOf(scopeProject1))
                )

                setIncludes(
                    paths = listOf(pathInclude2)
                )

                ortResult.isExcluded(project1.id) shouldBe false
            }
        }

        "isPackageExcluded" should {
            "return false if the package is not found" {
                setProjects(
                    project1
                )

                ortResult.isPackageExcluded(packageId) shouldBe false
            }

            "return false if the package is neither excluded nor included" {
                setProjects(
                    project1.copy(scopeDependencies = setOf(scope1))
                )

                ortResult.isPackageExcluded(packageId) shouldBe false
            }

            "return false if all projects depending on a package are included by path include" {
                setProjects(
                    project1.copy(scopeDependencies = setOf(scope1)),
                    project2.copy(scopeDependencies = setOf(scope2))
                )

                setIncludes(
                    paths = listOf(pathInclude1, pathInclude2)
                )

                ortResult.isPackageExcluded(packageId) shouldBe false
            }

            "return false if only part of the projects depending on a package are included by path includes" {
                setProjects(
                    project1.copy(scopeDependencies = setOf(scope1)),
                    project2.copy(scopeDependencies = setOf(scope2))
                )

                setIncludes(
                    paths = listOf(pathInclude1)
                )

                ortResult.isPackageExcluded(packageId) shouldBe false
            }

            "return true if some path include are defined but all projects depending on a package are not included" {
                setProjects(
                    project1.copy(scopeDependencies = setOf(scope1)),
                    project2.copy(scopeDependencies = setOf(scope2))
                )

                setIncludes(
                    paths = listOf(pathInclude3)
                )

                ortResult.isPackageExcluded(packageId) shouldBe true
            }

            "return false if there are no dependencies on the project" {
                setProjects(
                    project1,
                    project2
                )

                ortResult.isPackageExcluded(project1.id) shouldBe false
            }
        }

        "isProjectExcluded" should {
            "return false if the definition file path is matched by a path include" {
                setIncludes(Includes(paths = listOf(pathInclude1)))
                setProjects(project1)

                ortResult.isProjectExcluded(project1.id) shouldBe false
            }

            "return true if some path includes are defined but the definition file path is not matched by a path include" {
                setIncludes(Includes(paths = listOf(pathInclude2)))
                setProjects(project1)

                ortResult.isProjectExcluded(project1.id) shouldBe true
            }

            "return false if there are no path includes" {
                setProjects(project1)

                ortResult.isProjectExcluded(project1.id) shouldBe false
            }
        }

        "EMPTY" should {
            "not contain any inclusions" {
                Includes.EMPTY.paths should beEmpty()
            }
        }
    }
}
