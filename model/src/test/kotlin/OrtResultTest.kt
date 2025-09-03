/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.types.beInstanceOf

import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.Includes
import org.ossreviewtoolkit.model.config.IssueResolution
import org.ossreviewtoolkit.model.config.IssueResolutionReason
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.PathExcludeReason
import org.ossreviewtoolkit.model.config.PathInclude
import org.ossreviewtoolkit.model.config.PathIncludeReason
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.model.config.RuleViolationResolution
import org.ossreviewtoolkit.model.config.RuleViolationResolutionReason
import org.ossreviewtoolkit.utils.ort.ProcessedDeclaredLicense
import org.ossreviewtoolkit.utils.spdx.toSpdx
import org.ossreviewtoolkit.utils.test.readOrtResult

class OrtResultTest : WordSpec({
    "applyPackageCurations()" should {
        "apply a single package curation with a declared license mapping" {
            val licenseUrl = "https://www.nuget.org/packages/CommandLineParser/2.9.1/license"

            val pkg = Package.EMPTY.copy(
                declaredLicenses = setOf(licenseUrl)
            )

            val curation = PackageCuration(
                id = pkg.id,
                data = PackageCurationData(
                    declaredLicenseMapping = mapOf(
                        licenseUrl to "MIT".toSpdx()
                    )
                )
            )

            applyPackageCurations(setOf(pkg), listOf(curation)).shouldContainExactly(
                CuratedPackage(
                    metadata = Package.EMPTY.copy(
                        declaredLicenses = setOf(licenseUrl),
                        declaredLicensesProcessed = ProcessedDeclaredLicense(
                            spdxExpression = "MIT".toSpdx(),
                            mapped = mapOf(licenseUrl to "MIT".toSpdx()),
                            unmapped = emptySet()
                        )
                    ),
                    curations = listOf(curation.data)
                )
            )
        }
    }

    "getDependencies()" should {
        "be able to get all direct dependencies of a package" {
            val ortResult = readOrtResult("/sbt-multi-project-example-expected-output.yml")
            val id = Identifier("Maven:com.typesafe.akka:akka-stream_2.12:2.5.6")

            val dependencies = ortResult.getDependencies(id, 1).map { it.toCoordinates() }

            dependencies should containExactlyInAnyOrder(
                "Maven:com.typesafe.akka:akka-actor_2.12:2.5.6",
                "Maven:com.typesafe:ssl-config-core_2.12:0.2.2",
                "Maven:org.reactivestreams:reactive-streams:1.0.1"
            )
        }
    }

    "getPackageConfigurations()" should {
        val id = Identifier("Maven:org.ossreviewtoolkit:model:1.0.0")
        val provenance = ArtifactProvenance(
            sourceArtifact = RemoteArtifact(url = "https://example.org/artifact.zip", hash = Hash.NONE)
        )

        "return package configurations exactly matching the identifier" {
            val packageConfig1 = PackageConfiguration(id = id, sourceCodeOrigin = SourceCodeOrigin.ARTIFACT)
            val packageConfig2 = PackageConfiguration(id = id, sourceArtifactUrl = provenance.sourceArtifact.url)
            val packageConfig3 =
                PackageConfiguration(id = id.copy(version = "2.0.0"), sourceCodeOrigin = SourceCodeOrigin.ARTIFACT)

            val ortResult = OrtResult.EMPTY.copy(
                resolvedConfiguration = ResolvedConfiguration(
                    packageConfigurations = listOf(packageConfig1, packageConfig2, packageConfig3)
                )
            )

            ortResult.getPackageConfigurations(id, provenance) should
                containExactlyInAnyOrder(packageConfig1, packageConfig2)
        }

        "return package configurations with matching version ranges" {
            val packageConfig1 =
                PackageConfiguration(id = id.copy(version = "[1.0,2.0)"), sourceCodeOrigin = SourceCodeOrigin.ARTIFACT)
            val packageConfig2 =
                PackageConfiguration(id = id.copy(version = "[0.1,)"), sourceCodeOrigin = SourceCodeOrigin.ARTIFACT)
            val packageConfig3 =
                PackageConfiguration(id = id.copy(version = "]1.0.0,)"), sourceCodeOrigin = SourceCodeOrigin.ARTIFACT)

            val ortResult = OrtResult.EMPTY.copy(
                resolvedConfiguration = ResolvedConfiguration(
                    packageConfigurations = listOf(packageConfig1, packageConfig2, packageConfig3)
                )
            )

            ortResult.getPackageConfigurations(id, provenance) should
                containExactlyInAnyOrder(packageConfig1, packageConfig2)
        }
    }

    "getProjectsAndPackages()" should {
        val ortResult = readOrtResult("/gradle-all-dependencies-expected-result.yml")
        val subProjectId = Identifier("Gradle:org.ossreviewtoolkit.gradle.example:lib:1.0.0")

        "be able to get all ids including sub-projects" {
            val ids = ortResult.getProjectsAndPackages()

            ids should haveSize(9)
            ids shouldContain subProjectId
        }

        "be able to get all ids excluding sub-projects" {
            val ids = ortResult.getProjectsAndPackages(includeSubProjects = false)

            ids should haveSize(8)
            ids shouldNotContain(subProjectId)
        }
    }

    "getDefinitionFilePathRelativeToAnalyzerRoot()" should {
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
                AnalyzerRun.EMPTY.copy(
                    result = AnalyzerResult.EMPTY.copy(projects = setOf(project1, project2, project3))
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
                AnalyzerRun.EMPTY.copy(
                    result = AnalyzerResult.EMPTY.copy(projects = setOf(project))
                )
            )

            val e = shouldThrow<IllegalArgumentException> {
                ortResult.getDefinitionFilePathRelativeToAnalyzerRoot(project) shouldBe "project1/build.gradle"
            }

            e.message shouldMatch "The .* of project .* cannot be found in .*"
        }
    }

    "getOpenIssues()" should {
        "omit resolved issues" {
            val ortResult = OrtResult.EMPTY.copy(
                analyzer = AnalyzerRun.EMPTY.copy(
                    result = AnalyzerResult.EMPTY.copy(
                        issues = mapOf(
                            Identifier("Maven:org.oss-review-toolkit:example:1.0") to listOf(
                                Issue(message = "Issue message to resolve", source = ""),
                                Issue(message = "Non-resolved issue", source = "")
                            )
                        )
                    )
                ),
                resolvedConfiguration = ResolvedConfiguration(
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
            )

            val openIssues = ortResult.getOpenIssues(Severity.WARNING)

            openIssues.map { it.message } shouldHaveSingleElement "Non-resolved issue"
        }

        "omit issues with violation below threshold" {
            val ortResult = OrtResult.EMPTY.copy(
                analyzer = AnalyzerRun.EMPTY.copy(
                    result = AnalyzerResult.EMPTY.copy(
                        issues = mapOf(
                            Identifier("Maven:org.oss-review-toolkit:example:1.0") to
                                listOf(
                                    Issue(
                                        message = "Issue with severity 'warning'",
                                        source = "",
                                        severity = Severity.WARNING
                                    ),
                                    Issue(
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

            openIssues.map { it.message } shouldHaveSingleElement "Issue with severity 'warning'"
        }

        "omit issues of excluded projects" {
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
                analyzer = AnalyzerRun.EMPTY.copy(
                    result = AnalyzerResult.EMPTY.copy(
                        projects = setOf(
                            Project.EMPTY.copy(
                                id = Identifier("Maven:org.oss-review-toolkit:excluded:1.0"),
                                definitionFilePath = "excluded/pom.xml",
                                declaredLicenses = emptySet()
                            )
                        ),
                        issues = mapOf(
                            Identifier("Maven:org.oss-review-toolkit:excluded:1.0") to
                                listOf(Issue(message = "Excluded issue", source = "")),
                            Identifier("Maven:org.oss-review-toolkit:included:1.0") to
                                listOf(Issue(message = "Included issue", source = ""))
                        )
                    )
                )
            )

            val openIssues = ortResult.getOpenIssues()

            openIssues.map { it.message } shouldHaveSingleElement "Included issue"
        }

        "omit issues of non-included projects" {
            val ortResult = OrtResult.EMPTY.copy(
                repository = Repository.EMPTY.copy(
                    config = RepositoryConfiguration(
                        includes = Includes(
                            paths = listOf(
                                PathInclude(
                                    pattern = "included/pom.xml",
                                    reason = PathIncludeReason.SOURCE_OF
                                )
                            )
                        )
                    )
                ),
                analyzer = AnalyzerRun.EMPTY.copy(
                    result = AnalyzerResult.EMPTY.copy(
                        projects = setOf(
                            Project.EMPTY.copy(
                                id = Identifier("Maven:org.oss-review-toolkit:excluded:1.0"),
                                definitionFilePath = "excluded/pom.xml",
                                declaredLicenses = emptySet()
                            )
                        ),
                        issues = mapOf(
                            Identifier("Maven:org.oss-review-toolkit:excluded:1.0") to
                                listOf(Issue(message = "Excluded issue", source = "")),
                            Identifier("Maven:org.oss-review-toolkit:included:1.0") to
                                listOf(Issue(message = "Included issue", source = ""))
                        )
                    )
                )
            )

            val openIssues = ortResult.getOpenIssues()

            openIssues.map { it.message } shouldHaveSingleElement "Included issue"
        }

        "include issues of included projects" {
            val ortResult = OrtResult.EMPTY.copy(
                repository = Repository.EMPTY.copy(
                    config = RepositoryConfiguration(
                        includes = Includes(
                            paths = listOf(
                                PathInclude(
                                    pattern = "included/pom.xml",
                                    reason = PathIncludeReason.SOURCE_OF
                                )
                            )
                        )
                    )
                ),
                analyzer = AnalyzerRun.EMPTY.copy(
                    result = AnalyzerResult.EMPTY.copy(
                        projects = setOf(
                            Project.EMPTY.copy(
                                id = Identifier("Maven:org.oss-review-toolkit:included:1.0"),
                                definitionFilePath = "included/pom.xml",
                                declaredLicenses = emptySet()
                            )
                        ),
                        issues = mapOf(
                            Identifier("Maven:org.oss-review-toolkit:included:1.0") to
                                listOf(Issue(message = "Included issue", source = ""))
                        )
                    )
                )
            )

            val openIssues = ortResult.getOpenIssues()

            openIssues.map { it.message } shouldHaveSingleElement "Included issue"
        }

        "omit scan issues with excluded affected path" {
            val projectId = Identifier("Maven:org.oss-review-toolkit:example-project:1.0")
            val vcs = VcsInfo(
                type = VcsType.GIT,
                url = "https:/github.com/example.project.git",
                revision = "0000000000000000000000000000000000000000",
                path = ""
            )

            val ortResult = OrtResult.EMPTY.copy(
                repository = Repository.EMPTY.copy(
                    vcs = vcs,
                    vcsProcessed = vcs,
                    config = RepositoryConfiguration(
                        excludes = Excludes(
                            paths = listOf(
                                PathExclude(
                                    pattern = "path/**",
                                    reason = PathExcludeReason.TEST_OF
                                )
                            )
                        )
                    )
                ),
                analyzer = AnalyzerRun.EMPTY.copy(
                    result = AnalyzerResult.EMPTY.copy(
                        projects = setOf(
                            Project.EMPTY.copy(
                                id = projectId,
                                definitionFilePath = "pom.xml",
                                declaredLicenses = emptySet(),
                                vcsProcessed = vcs
                            )
                        )
                    )
                ),
                scanner = ScannerRun.EMPTY.copy(
                    scanners = mapOf(projectId to setOf("ScanCode")),
                    provenances = setOf(
                        ProvenanceResolutionResult(
                            id = projectId,
                            packageProvenance = RepositoryProvenance(
                                vcsInfo = vcs,
                                resolvedRevision = vcs.revision
                            )
                        )
                    ),
                    scanResults = setOf(
                        ScanResult(
                            provenance = RepositoryProvenance(
                                vcsInfo = vcs,
                                resolvedRevision = vcs.revision
                            ),
                            scanner = ScannerDetails.EMPTY.copy(name = "ScanCode"),
                            summary = ScanSummary.EMPTY.copy(
                                issues = listOf(
                                    Issue(
                                        message = "Included issue",
                                        source = "ScanCode",
                                        affectedPath = "path/that/is/affected"
                                    )
                                )
                            )
                        )
                    )
                )
            )

            val issues = ortResult.getOpenIssues()

            issues should beEmpty()
        }
    }

    "dependencyNavigator" should {
        "return a navigator for the dependency tree" {
            val ortResult = readOrtResult("/sbt-multi-project-example-expected-output.yml")

            ortResult.dependencyNavigator shouldBe DependencyTreeNavigator
        }

        "return a navigator for the dependency graph" {
            val ortResult = readOrtResult("/sbt-multi-project-example-graph.yml")

            ortResult.dependencyNavigator should beInstanceOf<DependencyGraphNavigator>()
        }
    }

    "getRuleViolations()" should {
        "return unfiltered rule violations if omitResolved is false and minSeverity is HINT" {
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
                evaluator = EvaluatorRun.EMPTY.copy(
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

            val ruleViolations = ortResult.getRuleViolations(omitResolved = false, minSeverity = Severity.entries.min())

            ruleViolations.map { it.rule } should containExactly("rule id")
        }

        "drop violations which are resolved or below minSeverity if omitResolved is true and minSeverity is WARNING" {
            val ortResult = OrtResult.EMPTY.copy(
                evaluator = EvaluatorRun.EMPTY.copy(
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
                ),
                resolvedConfiguration = ResolvedConfiguration(
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
            )

            val ruleViolations = ortResult.getRuleViolations(omitResolved = true, minSeverity = Severity.WARNING)

            ruleViolations.map { it.rule } should containExactly("Rule violation without resolution")
        }
    }
})
