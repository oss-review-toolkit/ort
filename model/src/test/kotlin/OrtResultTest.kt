/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.types.beInstanceOf

import java.lang.IllegalArgumentException

import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.IssueResolution
import org.ossreviewtoolkit.model.config.IssueResolutionReason
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.PathExcludeReason
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.model.config.RuleViolationResolution
import org.ossreviewtoolkit.model.config.RuleViolationResolutionReason
import org.ossreviewtoolkit.utils.ort.Environment
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
            val idsWithoutSubProjects = ortResult.collectProjectsAndPackages(includeSubProjects = false)
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
                    config = AnalyzerConfiguration(allowDynamicVersions = true),
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
                    config = AnalyzerConfiguration(allowDynamicVersions = true),
                    result = AnalyzerResult.EMPTY.copy(projects = sortedSetOf(project))
                )
            )

            val e = shouldThrow<IllegalArgumentException> {
                ortResult.getDefinitionFilePathRelativeToAnalyzerRoot(project) shouldBe "project1/build.gradle"
            }

            e.message shouldMatch "The .* of project .* cannot be found in .*"
        }
    }

    "getOpenIssues" should {
        "omit resolved issues" {
            val ortResult = OrtResult.EMPTY.copy(
                repository = Repository.EMPTY.copy(
                    config = RepositoryConfiguration(
                        resolutions = Resolutions(
                            issues = listOf(
                                IssueResolution(
                                    "Issue message to resolve",
                                    IssueResolutionReason.CANT_FIX_ISSUE,
                                    "comment"
                                )
                            )
                        )
                    )
                ),
                analyzer = AnalyzerRun(
                    environment = Environment(),
                    config = AnalyzerConfiguration(),
                    result = AnalyzerResult(
                        projects = sortedSetOf(),
                        packages = sortedSetOf(),
                        issues = sortedMapOf(
                            Identifier("Maven:org.oss-review-toolkit:example:1.0") to
                                    listOf(
                                        OrtIssue(message = "Issue message to resolve", source = ""),
                                        OrtIssue(message = "Non-resolved issue", source = "")
                                    )
                        )
                    )
                )
            )

            val openIssues = ortResult.getOpenIssues(Severity.WARNING)

            openIssues should haveSize(1)
            with(openIssues[0]) {
                message shouldBe "Non-resolved issue"
            }
        }

        "omit issues with violation below threshold" {
            val ortResult = OrtResult.EMPTY.copy(
                analyzer = AnalyzerRun(
                    environment = Environment(),
                    config = AnalyzerConfiguration(),
                    result = AnalyzerResult(
                        projects = sortedSetOf(),
                        packages = sortedSetOf(),
                        issues = sortedMapOf(
                            Identifier("Maven:org.oss-review-toolkit:example:1.0") to
                                    listOf(
                                        OrtIssue(
                                            message = "Issue with severity 'warning'",
                                            source = "",
                                            severity = Severity.WARNING
                                        ),
                                        OrtIssue(
                                            message = "Issue with severity 'hint'.",
                                            source = "",
                                            severity = Severity.HINT
                                        )
                                    )
                        )
                    )
                )
            )

            val openIssues = ortResult.getOpenIssues(Severity.WARNING)

            openIssues should haveSize(1)
            with(openIssues[0]) {
                message shouldBe "Issue with severity 'warning'"
            }
        }

        "omit excluded issues" {
            val ortResult = OrtResult.EMPTY.copy(
                repository = Repository.EMPTY.copy(
                    config = RepositoryConfiguration(
                        excludes = Excludes(
                            paths = listOf(
                                PathExclude(
                                    pattern = "excluded/pom.xml",
                                    reason = PathExcludeReason.TEST_OF
                                )
                            )
                        )
                    )
                ),
                analyzer = AnalyzerRun(
                    environment = Environment(),
                    config = AnalyzerConfiguration(),
                    result = AnalyzerResult(
                        projects = sortedSetOf(
                            Project.EMPTY.copy(
                                id = Identifier("Maven:org.oss-review-toolkit:excluded:1.0"),
                                definitionFilePath = "excluded/pom.xml",
                                declaredLicenses = sortedSetOf()
                            )
                        ),
                        packages = sortedSetOf(),
                        issues = sortedMapOf(
                            Identifier("Maven:org.oss-review-toolkit:excluded:1.0") to
                                    listOf(OrtIssue(message = "Excluded issue", source = "")),
                            Identifier("Maven:org.oss-review-toolkit:included:1.0") to
                                    listOf(OrtIssue(message = "Included issue", source = ""))
                        )
                    )
                )
            )

            val openIssues = ortResult.getOpenIssues()

            openIssues should haveSize(1)
            with(openIssues[0]) {
                message shouldBe "Included issue"
            }
        }
    }

    "dependencyNavigator" should {
        "return a navigator for the dependency tree" {
            val ortResult = readOrtResult(
                "../analyzer/src/funTest/assets/projects/external/sbt-multi-project-example-expected-output.yml"
            )

            ortResult.dependencyNavigator shouldBe DependencyTreeNavigator
        }

        "return a navigator for the dependency graph" {
            val ortResult = readOrtResult("src/test/assets/sbt-multi-project-example-graph.yml")

            ortResult.dependencyNavigator should beInstanceOf<DependencyGraphNavigator>()
        }
    }

    "getRuleViolations" should {
        "return unfiltered rule violations if omitResolved is false" {
            val ortResult = OrtResult.EMPTY.copy(
                repository = Repository.EMPTY.copy(
                    config = RepositoryConfiguration(
                        resolutions = Resolutions(
                            ruleViolations = listOf(
                                RuleViolationResolution(
                                    "Rule violation message to resolve",
                                    RuleViolationResolutionReason.EXAMPLE_OF_EXCEPTION,
                                    "comment"
                                )
                            )
                        )
                    )
                ),
                evaluator = EvaluatorRun(
                    violations = listOf(
                        RuleViolation(
                            rule = "rule id",
                            pkg = Identifier("Maven", "org.ossreviewtoolkit", "resolved-violation", "0.8.15"),
                            license = null,
                            licenseSource = null,
                            severity = Severity.HINT,
                            message = "Rule violation message to resolve",
                            howToFix = ""
                        )
                    )
                )
            )

            ortResult.getRuleViolations(omitResolved = false, minSeverity = null).map { it.rule }
                .shouldContainExactly("rule id")
        }

        "drop resolved rule violations if omitResolved is true" {
            val ortResult = OrtResult.EMPTY.copy(
                repository = Repository.EMPTY.copy(
                    config = RepositoryConfiguration(
                        resolutions = Resolutions(
                            ruleViolations = listOf(
                                RuleViolationResolution(
                                    "Rule violation message to resolve",
                                    RuleViolationResolutionReason.EXAMPLE_OF_EXCEPTION,
                                    "comment"
                                )
                            )
                        )
                    )
                ),
                evaluator = EvaluatorRun(
                    violations = listOf(
                        RuleViolation(
                            rule = "Resolved rule violation",
                            pkg = Identifier("Maven", "org.ossreviewtoolkit", "resolved-violation", "0.8.15"),
                            license = null,
                            licenseSource = null,
                            severity = Severity.ERROR,
                            message = "Rule violation message to resolve",
                            howToFix = ""
                        ),
                        RuleViolation(
                            rule = "Rule violation without resolution",
                            pkg = Identifier("Maven", "com.example", "package-without-resolution", "1.0.0"),
                            license = null,
                            licenseSource = null,
                            severity = Severity.WARNING,
                            message = "Message without any resolution",
                            howToFix = ""
                        ),
                        RuleViolation(
                            rule = "Rule violation below minSeverity",
                            pkg = Identifier("Maven", "com.example", "violation-below-threshold", "3.14"),
                            license = null,
                            licenseSource = null,
                            severity = Severity.HINT,
                            message = "Message without any resolution",
                            howToFix = ""
                        )
                    )
                )
            )

            ortResult.getRuleViolations(omitResolved = true, minSeverity = Severity.WARNING).map { it.rule }
                .shouldContainExactly("Rule violation without resolution")
        }
    }
})
