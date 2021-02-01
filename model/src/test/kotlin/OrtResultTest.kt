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

package org.ossreviewtoolkit.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch

import java.lang.IllegalArgumentException
import java.time.Instant
import java.util.Collections.emptySortedSet

import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.spdx.toSpdx
import org.ossreviewtoolkit.utils.test.readOrtResult

class OrtResultTest : WordSpec({
    "collectDependencies" should {
        "be able to get all direct dependencies of a package" {
            val ortResult = readOrtResult(
                "../analyzer/src/funTest/assets/projects/external/sbt-multi-project-example-expected-output.yml"
            )

            val id = Identifier("Maven:com.typesafe.akka:akka-stream_2.12:2.5.6")
            val dependencies = ortResult.collectDependencies(id, 1).map { it.toCoordinates() }

            dependencies should containExactlyInAnyOrder(
                "Maven:com.typesafe.akka:akka-actor_2.12:2.5.6",
                "Maven:com.typesafe:ssl-config-core_2.12:0.2.2",
                "Maven:org.reactivestreams:reactive-streams:1.0.1"
            )
        }
    }

    "collectProjectsAndPackages" should {
        "be able to get all ids except for ones for sub-projects" {
            val ortResult = readOrtResult(
                "../analyzer/src/funTest/assets/projects/synthetic/gradle-all-dependencies-expected-result.yml"
            )

            val ids = ortResult.collectProjectsAndPackages()
            val idsWithoutSubProjects = ortResult.collectProjectsAndPackages(false)
            val actualIds = ids - idsWithoutSubProjects

            ids should haveSize(9)
            idsWithoutSubProjects should haveSize(8)
            actualIds should containExactly(Identifier("Gradle:org.ossreviewtoolkit.gradle.example:lib:1.0.0"))
        }
    }

    "getDefinitionFilePathRelativeToAnalyzerRoot" should {
        "use the correct vcs" {
            val vcs = VcsInfo(type = VcsType.GIT, url = "https://example.com/git", revision = "")
            val nestedVcs1 = VcsInfo(type = VcsType.GIT, url = "https://example.com/git1", revision = "")
            val nestedVcs2 = VcsInfo(type = VcsType.GIT, url = "https://example.com/git2", revision = "")
            val project1 = Project.EMPTY.copy(
                id = Identifier("Gradle:org.ossreviewtoolkit:project1:1.0"),
                definitionFilePath = "project1/build.gradle",
                vcs = vcs,
                vcsProcessed = vcs.normalize()
            )
            val project2 = Project.EMPTY.copy(
                id = Identifier("Gradle:org.ossreviewtoolkit:project2:1.0"),
                definitionFilePath = "project2/build.gradle",
                vcs = nestedVcs1,
                vcsProcessed = nestedVcs1.normalize()
            )
            val project3 = Project.EMPTY.copy(
                id = Identifier("Gradle:org.ossreviewtoolkit:project3:1.0"),
                definitionFilePath = "project3/build.gradle",
                vcs = nestedVcs2,
                vcsProcessed = nestedVcs2.normalize()
            )
            val ortResult = OrtResult(
                Repository(
                    vcs = vcs,
                    nestedRepositories = mapOf(
                        "path/1" to nestedVcs1,
                        "path/2" to nestedVcs2
                    )
                ),
                AnalyzerRun(
                    environment = Environment(),
                    config = AnalyzerConfiguration(ignoreToolVersions = true, allowDynamicVersions = true),
                    result = AnalyzerResult.EMPTY.copy(projects = sortedSetOf(project1, project2, project3))
                )
            )

            ortResult.getDefinitionFilePathRelativeToAnalyzerRoot(project1) shouldBe "project1/build.gradle"
            ortResult.getDefinitionFilePathRelativeToAnalyzerRoot(project2) shouldBe "path/1/project2/build.gradle"
            ortResult.getDefinitionFilePathRelativeToAnalyzerRoot(project3) shouldBe "path/2/project3/build.gradle"
        }

        "fail if no vcs matches" {
            val vcs = VcsInfo(type = VcsType.GIT, url = "https://example.com/git", revision = "")
            val nestedVcs1 = VcsInfo(type = VcsType.GIT, url = "https://example.com/git1", revision = "")
            val nestedVcs2 = VcsInfo(type = VcsType.GIT, url = "https://example.com/git2", revision = "")
            val project = Project.EMPTY.copy(
                id = Identifier("Gradle:org.ossreviewtoolkit:project1:1.0"),
                definitionFilePath = "build.gradle",
                vcs = nestedVcs2,
                vcsProcessed = nestedVcs2.normalize()
            )
            val ortResult = OrtResult(
                Repository(
                    vcs = vcs,
                    nestedRepositories = mapOf(
                        "path/1" to nestedVcs1
                    )
                ),
                AnalyzerRun(
                    environment = Environment(),
                    config = AnalyzerConfiguration(ignoreToolVersions = true, allowDynamicVersions = true),
                    result = AnalyzerResult.EMPTY.copy(projects = sortedSetOf(project))
                )
            )

            val e = shouldThrow<IllegalArgumentException> {
                ortResult.getDefinitionFilePathRelativeToAnalyzerRoot(project) shouldBe "project1/build.gradle"
            }

            e.message shouldMatch "The .* of project .* cannot be found in .*"
        }
    }

    "getDetectedLicensesForId" should {
        "return the detected license" {
            val packageId = Identifier("Maven:org.ossreviewtoolkit:example-project:1.0")

            val ortResult = OrtResult(
                Repository.EMPTY,
                scanner = ScannerRun(
                    Instant.now(),
                    Instant.now(),
                    Environment(),
                    ScannerConfiguration(),
                    ScanRecord(
                        sortedSetOf(
                            ScanResultContainer(
                                id = packageId,
                                results = listOf(
                                    ScanResult(
                                        provenance = Provenance(),
                                        scanner = ScannerDetails.EMPTY,
                                        summary = ScanSummary(
                                            startTime = Instant.EPOCH,
                                            endTime = Instant.EPOCH,
                                            fileCount = 1,
                                            packageVerificationCode = "",
                                            licenseFindings = sortedSetOf(
                                                LicenseFinding(
                                                    "GPL-3.0-only".toSpdx(),
                                                    TextLocation("some/path", 1)
                                                )
                                            ),
                                            copyrightFindings = emptySortedSet()
                                        )
                                    )
                                )
                            )
                        ),
                        AccessStatistics()
                    )
                )
            )

            val detectedLicenses = ortResult.getDetectedLicensesForId(packageId)

            detectedLicenses should haveSize(1)
            detectedLicenses.first() shouldBe "GPL-3.0-only".toSpdx()
        }
    }
})
