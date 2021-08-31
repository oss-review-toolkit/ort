/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2021 TNG Technology Consulting GmbH
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

package org.ossreviewtoolkit.reporter.reporters

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.*
import org.ossreviewtoolkit.model.config.*
import org.ossreviewtoolkit.spdx.toSpdx
import org.ossreviewtoolkit.utils.Environment
import org.ossreviewtoolkit.utils.test.readOrtResult
import java.io.File
import java.util.*

fun traverseProjects1(projects: SortedSet<Project>): Map<String,Identifier> {
    fun traverseProjectDependencies1(project: Project): List<Pair<String,Identifier>> {
        fun traverseDependencies1(dependencies: SortedSet<PackageReference>, path: File): List<Pair<String,Identifier>> {
            fun lambda1(dependency: PackageReference): List<Pair<String,Identifier>> {
                val depPath = path.resolve(dependency.id.name)
                return traverseDependencies1(dependency.dependencies, depPath)
                    .plus(Pair(depPath.toString(),dependency.id))
            }
            return dependencies .flatMap { lambda1(it) }
        }

        return project.scopes
            .flatMap { traverseDependencies1(it.dependencies, File(project.definitionFilePath)) }
    }
    return projects.flatMap { traverseProjectDependencies1(it) }
        .toMap()
}

fun traverseProjects2(projects: SortedSet<Project>, dependencyNavigator: DependencyNavigator): Map<String,Identifier> {
    fun traverseProjectDependencies2(project: Project): List<Pair<String,Identifier>> {
        fun traverseDependencies2(dependencies: Sequence<DependencyNode>, path: File): List<Pair<String,Identifier>> {
            fun lambda2(dependency: DependencyNode): List<Pair<String,Identifier>> {
                val depPath = path.resolve(dependency.id.name)
                return dependency.visitDependencies { traverseDependencies2(it, depPath)
                    .plus(Pair( depPath.toString(), dependency.id) ) }
            }
            return dependencies.toList()
                .flatMap { lambda2(it) }
        }

        return dependencyNavigator.scopeNames(project)
            .flatMap { traverseDependencies2(dependencyNavigator.directDependencies(project,it), File(project.definitionFilePath)) }
    }

    return projects
        .flatMap { traverseProjectDependencies2(it) }
        .toMap()
}

class TraverseDepsTest : WordSpec({
    "TraverseDepsTest" should {
        listOf(
            "inline" to createOrtResult(),
            "static-html-reporter-test-input.yml" to readOrtResult("src/funTest/assets/static-html-reporter-test-input.yml"),
            "cvat-analyzer-result.yml" to readOrtResult("src/funTest/assets/cvat-analyzer-result.yml")
        ).forEach { (name, ortResult) ->

            val analyzerResultProjects = ortResult.analyzer!!.result.projects
            val result1 = traverseProjects1(analyzerResultProjects)
            val result2 = traverseProjects2(analyzerResultProjects, ortResult.dependencyNavigator)

            "${name}: type1 and type2 should return same results" {

                result1.size shouldBe result2.size
                result1.keys.sorted() shouldBe result2.keys.sorted()
                result1.values.sorted() shouldBe result2.values.sorted()
            }

            val projectIds = ortResult.analyzer!!.result.projects.map { it.id }
            val expectedIds = ortResult.analyzer!!.result.packages
                .map { it.pkg.id }
                .filter { !projectIds.contains(it) }
                .toSortedSet()

            "${name}: type1 should contain all expected IDs" {
                val valuesExcludingProjectIds = result1.values
                    .filter { !projectIds.contains(it) }
                    .toSortedSet()
                val idsNotHitInTraversal = expectedIds.filter { ! valuesExcludingProjectIds.contains(it) }
                idsNotHitInTraversal shouldBe emptySet<Identifier>()
            }

            "${name}: type2 should contain all expected IDs" {
                val valuesExcludingProjectIds = result2.values
                    .filter { !projectIds.contains(it) }
                    .toSortedSet()
                val idsNotHitInTraversal = expectedIds.filter { ! valuesExcludingProjectIds.contains(it) }
                idsNotHitInTraversal shouldBe emptySet<Identifier>()
            }
        }
    }
    "TraverseDepsTest with resolved scopes" should {
        listOf(
            "inline" to createOrtResult(),
            "static-html-reporter-test-input.yml" to readOrtResult("src/funTest/assets/static-html-reporter-test-input.yml"),
            "cvat-analyzer-result.yml" to readOrtResult("src/funTest/assets/cvat-analyzer-result.yml")
        ).forEach { (name, ortResult) ->

            val analyzerResultProjects = ortResult.analyzer!!.result.withScopesResolved().projects
            val result1 = traverseProjects1(analyzerResultProjects)
            val result2 = traverseProjects2(analyzerResultProjects, ortResult.dependencyNavigator)

            "${name}: type1 and type2 should return same results" {

                result1.size shouldBe result2.size
                result1.keys.sorted() shouldBe result2.keys.sorted()
                result1.values.sorted() shouldBe result2.values.sorted()
            }

            val projectIds = ortResult.analyzer!!.result.projects.map { it.id }
            val expectedIds = ortResult.analyzer!!.result.packages
                .map { it.pkg.id }
                .filter { !projectIds.contains(it) }
                .toSortedSet()

            "${name}: type1 should contain all expected IDs" {
                val valuesExcludingProjectIds = result1.values
                    .filter { !projectIds.contains(it) }
                    .toSortedSet()
                val idsNotHitInTraversal = expectedIds.filter { ! valuesExcludingProjectIds.contains(it) }
                idsNotHitInTraversal shouldBe emptySet<Identifier>()
            }

            "${name}: type2 should contain all expected IDs" {
                val valuesExcludingProjectIds = result2.values
                    .filter { !projectIds.contains(it) }
                    .toSortedSet()
                val idsNotHitInTraversal = expectedIds.filter { ! valuesExcludingProjectIds.contains(it) }
                idsNotHitInTraversal shouldBe emptySet<Identifier>()
            }
        }
    }
})


@Suppress("LongMethod")
private fun createOrtResult(): OrtResult {
    val analyzedVcs = VcsInfo(
        type = VcsType.GIT,
        revision = "master",
        url = "https://github.com/path/first-project.git",
        path = ""
    )

    return OrtResult(
        repository = Repository(
            config = RepositoryConfiguration(
                excludes = Excludes(
                    scopes = listOf(
                        ScopeExclude(
                            pattern = "test",
                            reason = ScopeExcludeReason.TEST_DEPENDENCY_OF,
                            comment = "Packages for testing only."
                        )
                    )
                )
            ),
            vcs = analyzedVcs,
            vcsProcessed = analyzedVcs
        ),
        analyzer = AnalyzerRun(
            environment = Environment(),
            config = AnalyzerConfiguration(ignoreToolVersions = true, allowDynamicVersions = true),
            result = AnalyzerResult(
                projects = sortedSetOf(
                    Project(
                        id = Identifier("Maven:first-project-group:first-project-name:0.0.1"),
                        declaredLicenses = sortedSetOf("MIT"),
                        definitionFilePath = "pom.xml",
                        homepageUrl = "first project's homepage",
                        scopeDependencies = sortedSetOf(
                            Scope(
                                name = "compile",
                                dependencies = sortedSetOf(
                                    PackageReference(
                                        id = Identifier("Maven:first-package-group:first-package:0.0.1")
                                    ),
                                    PackageReference(
                                        id = Identifier("Maven:second-package-group:second-package:0.0.1")
                                    ),
                                    PackageReference(
                                        id = Identifier("Maven:third-package-group:third-package:0.0.1")
                                    ),
                                    PackageReference(
                                        id = Identifier("Maven:fourth-package-group:fourth-package:0.0.1")
                                    ),
                                    PackageReference(
                                        id = Identifier("Maven:sixth-package-group:sixth-package:0.0.1")
                                    )
                                )
                            ),
                            Scope(
                                name = "test",
                                dependencies = sortedSetOf(
                                    PackageReference(
                                        id = Identifier("Maven:fifth-package-group:fifth-package:0.0.1")
                                    )
                                )
                            )
                        ),
                        vcs = analyzedVcs
                    ),
                    Project(
                        id = Identifier("NPM:second-project-group:second-project-name:0.0.1"),
                        declaredLicenses = sortedSetOf("BSD-3-Clause"),
                        definitionFilePath = "npm-project/package.json",
                        homepageUrl = "first project's homepage",
                        scopeDependencies = sortedSetOf(
                            Scope(
                                name = "devDependencies",
                                dependencies = sortedSetOf(
                                    PackageReference(
                                        id = Identifier("NPM:@something:somepackage:1.2.3"),
                                        dependencies = sortedSetOf(
                                            PackageReference(
                                                id = Identifier("NPM:@something:somepackage-dep:1.2.3"),
                                                dependencies = sortedSetOf(
                                                    PackageReference(
                                                        id = Identifier("NPM:@something:somepackage-dep-dep:1.2.3"),
                                                        dependencies = sortedSetOf(
                                                            PackageReference(
                                                                id = Identifier("NPM:@something:somepackage-dep-dep-dep:1.2.3"),
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        ),
                        vcs = analyzedVcs
                    )
                ),
                packages = sortedSetOf(
                    CuratedPackage(
                        pkg = Package(
                            id = Identifier("Maven:first-package-group:first-package:0.0.1"),
                            binaryArtifact = RemoteArtifact("https://some-host/first-package.jar", Hash.NONE),
                            concludedLicense = "BSD-2-Clause AND BSD-3-Clause AND MIT".toSpdx(),
                            declaredLicenses = sortedSetOf("BSD-3-Clause", "MIT OR GPL-2.0-only"),
                            description = "A package with all supported attributes set, with a VCS URL containing a " +
                                    "user name, and with a scan result containing two copyright finding matched to a " +
                                    "license finding.",
                            homepageUrl = "first package's homepage URL",
                            sourceArtifact = RemoteArtifact("https://some-host/first-package-sources.jar", Hash.NONE),
                            vcs = VcsInfo(
                                type = VcsType.GIT,
                                revision = "master",
                                url = "ssh://git@github.com/path/first-package-repo.git",
                                path = "project-path"
                            )
                        )
                    ),
                    CuratedPackage(
                        pkg = Package(
                            id = Identifier("Maven:second-package-group:second-package:0.0.1"),
                            binaryArtifact = RemoteArtifact.EMPTY,
                            declaredLicenses = sortedSetOf(),
                            description = "A package with minimal attributes set.",
                            homepageUrl = "",
                            sourceArtifact = RemoteArtifact.EMPTY,
                            vcs = VcsInfo.EMPTY
                        )
                    ),
                    CuratedPackage(
                        pkg = Package(
                            id = Identifier("Maven:third-package-group:third-package:0.0.1"),
                            binaryArtifact = RemoteArtifact.EMPTY,
                            declaredLicenses = sortedSetOf("unmappable license"),
                            description = "A package with only unmapped declared license.",
                            homepageUrl = "",
                            sourceArtifact = RemoteArtifact.EMPTY,
                            vcs = VcsInfo.EMPTY
                        )
                    ),
                    CuratedPackage(
                        pkg = Package(
                            id = Identifier("Maven:fourth-package-group:fourth-package:0.0.1"),
                            binaryArtifact = RemoteArtifact.EMPTY,
                            declaredLicenses = sortedSetOf("unmappable license", "MIT"),
                            description = "A package with partially mapped declared license.",
                            homepageUrl = "",
                            sourceArtifact = RemoteArtifact.EMPTY,
                            vcs = VcsInfo.EMPTY
                        )
                    ),
                    CuratedPackage(
                        pkg = Package(
                            id = Identifier("Maven:fifth-package-group:fifth-package:0.0.1"),
                            binaryArtifact = RemoteArtifact.EMPTY,
                            declaredLicenses = sortedSetOf("LicenseRef-scancode-philips-proprietary-notice-2000"),
                            concludedLicense = "LicenseRef-scancode-purdue-bsd".toSpdx(),
                            description = "A package used only from the excluded 'test' scope, with non-SPDX license " +
                                    "IDs in the declared and concluded license.",
                            homepageUrl = "",
                            sourceArtifact = RemoteArtifact.EMPTY,
                            vcs = VcsInfo.EMPTY
                        )
                    ),
                    CuratedPackage(
                        pkg = Package(
                            id = Identifier("Maven:sixth-package-group:sixth-package:0.0.1"),
                            binaryArtifact = RemoteArtifact.EMPTY,
                            declaredLicenses = sortedSetOf("LicenseRef-scancode-asmus"),
                            concludedLicense = "LicenseRef-scancode-srgb".toSpdx(),
                            description = "A package with non-SPDX license IDs in the declared and concluded license.",
                            homepageUrl = "",
                            sourceArtifact = RemoteArtifact.EMPTY,
                            vcs = VcsInfo.EMPTY
                        )
                    )
                ).plus(
                    sortedSetOf(
                        Identifier("NPM:second-project-group:second-project-name:0.0.1"),
                        Identifier("NPM:@something:somepackage:1.2.3"),
                        Identifier("NPM:@something:somepackage-dep:1.2.3"),
                        Identifier("NPM:@something:somepackage-dep-dep:1.2.3"),
                        Identifier("NPM:@something:somepackage-dep-dep-dep:1.2.3"),
                    ).map {
                        CuratedPackage(
                            pkg = Package(
                                id = it,
                                binaryArtifact = RemoteArtifact.EMPTY,
                                declaredLicenses = sortedSetOf("MIT"),
                                description = "Package of ${it}",
                                homepageUrl = "",
                                sourceArtifact = RemoteArtifact.EMPTY,
                                vcs = VcsInfo.EMPTY
                            )
                        )
                    }
                ).toSortedSet()
            )
        )
    )
}
