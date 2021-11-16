/*
 * Copyright (C) 2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.reporter.reporters.gitlab

import io.kotest.core.TestConfiguration
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.CuratedPackage
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.ScopeExclude
import org.ossreviewtoolkit.model.config.ScopeExcludeReason
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.normalizeLineBreaks
import org.ossreviewtoolkit.utils.core.Environment
import org.ossreviewtoolkit.utils.test.createTestTempDir

class GitLabLicenseModelReporterFunTest : WordSpec({
    "GitLabLicenseModelReporter" should {
        "create the expected JSON license model containing only non-excluded packages" {
            val ortResult = createOrtResult()

            val jsonLicenseModel = generateReport(ortResult = ortResult, skipExcluded = true) + "\n"

            jsonLicenseModel shouldBe expectedOutput("gitlab-license-model-test-skip-excluded-expected-output.json")
        }

        "create the expected JSON license model containing all packages referenced by any project " {
            val ortResult = createOrtResult()

            val jsonLicenseModel = generateReport(ortResult = ortResult, skipExcluded = false) + "\n"

            jsonLicenseModel shouldBe expectedOutput("gitlab-license-model-test-expected-output.json")
        }
    }
})

private fun expectedOutput(assetName: String): String = File("src/funTest/assets/$assetName").readText()

private fun TestConfiguration.generateReport(ortResult: OrtResult, skipExcluded: Boolean): String =
    GitLabLicenseModelReporter().generateReport(
        input = ReporterInput(ortResult = ortResult),
        outputDir = createTestTempDir(),
        options = mapOf(GitLabLicenseModelReporter.OPTION_SKIP_EXCLUDED to skipExcluded.toString())
    ).single().readText().normalizeLineBreaks()

private fun createOrtResult(): OrtResult {
    return OrtResult(
        repository = Repository.EMPTY.copy(
            config = RepositoryConfiguration(
                excludes = Excludes(
                    scopes = listOf(
                        ScopeExclude(
                            pattern = "test",
                            reason = ScopeExcludeReason.TEST_DEPENDENCY_OF
                        )
                    )
                )
            )
        ),
        analyzer = AnalyzerRun(
            environment = Environment(),
            config = AnalyzerConfiguration(allowDynamicVersions = true),
            result = AnalyzerResult(
                projects = sortedSetOf(
                    Project.EMPTY.copy(
                        id = Identifier("Gradle:some-group:some-gradle-project:0.0.1"),
                        definitionFilePath = "some/path/build.gradle",
                        scopeDependencies = sortedSetOf(
                            Scope(
                                name = "compile",
                                dependencies = sortedSetOf(
                                    PackageReference(
                                        id = Identifier("Maven:some-group:first-package:0.0.1")
                                    )
                                )
                            ),
                            Scope(
                                name = "test",
                                dependencies = sortedSetOf(
                                    PackageReference(
                                        id = Identifier("Maven:some-group:excluded-package:0.0.4")
                                    )
                                )
                            )
                        )
                    ),
                    Project.EMPTY.copy(
                        id = Identifier("PIP::some-pip-project:0.0.2"),
                        scopeDependencies = sortedSetOf(
                            Scope(
                                name = "install",
                                dependencies = sortedSetOf(
                                    PackageReference(
                                        id = Identifier("PIP::second-package:0.0.2")
                                    )
                                )
                            )
                        )
                    )
                ),
                packages = sortedSetOf(
                    curatedPackage(
                        id = Identifier("Maven:some-group:first-package:0.0.1"),
                        declaredLicenses = listOf(
                            "GPL-2.0-or-later WITH Classpath-exception-2.0 AND MIT",
                            "BSD-2-Clause",
                            "Some unmappable license string",
                            "LicenseRef-scancode-asmus"
                        )
                    ),
                    curatedPackage(
                        id = Identifier("PIP::second-package:0.0.2"),
                        declaredLicenses = listOf("BSD-2-Clause AND Apache-2.0")
                    ),
                    curatedPackage(
                        id = Identifier("PIP::unreferenced-package:0.0.3"),
                        declaredLicenses = listOf("LicenseRef-scancode-public-domain-disclaimer")
                    ),
                    curatedPackage(
                        id = Identifier("Maven:some-group:excluded-package:0.0.4"),
                        declaredLicenses = listOf("LicenseRef-scancode-josl-1.0")
                    )
                )
            )
        )
    )
}

private fun curatedPackage(id: Identifier, declaredLicenses: Collection<String>): CuratedPackage =
    CuratedPackage(
        pkg = Package(
            id = id,
            binaryArtifact = RemoteArtifact.EMPTY,
            declaredLicenses = declaredLicenses.toSortedSet(),
            description = "",
            homepageUrl = "",
            sourceArtifact = RemoteArtifact.EMPTY,
            vcs = VcsInfo.EMPTY
        )
    )
