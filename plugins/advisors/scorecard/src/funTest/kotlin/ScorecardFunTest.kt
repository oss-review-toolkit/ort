/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.advisors.scorecard

import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.and
import io.kotest.matchers.collections.atLeastSize
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.containOnly
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.collections.shouldBeSingle
import io.kotest.matchers.doubles.between
import io.kotest.matchers.maps.matchExactly
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.or
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldHave
import io.kotest.matchers.string.contain
import io.kotest.matchers.string.endWith
import io.kotest.matchers.string.match
import io.kotest.matchers.string.startWith

import org.ossreviewtoolkit.model.Criticality
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType

class ScorecardFunTest : StringSpec({
    val scorecard by lazy { ScorecardFactory.create() }
    val checksUrl = "https://github.com/ossf/scorecard/blob/${HashAlgorithm.SHA1.validationRegex}/docs/checks.md"

    "Return findings for a valid package" {
        val vcs = VcsInfo(
            type = VcsType.GIT,
            url = "https://github.com/oss-review-toolkit/ort.git",
            revision = "dbd50cd755093305319ca85f9aa2a3f5d20320fe"
        )
        val pkg = Package.EMPTY.copy(
            id = Identifier("Maven:org.ossreviewtoolkit:cli:99.0.0"),
            vcs = vcs,
            vcsProcessed = vcs
        )

        val findings = scorecard.retrievePackageFindings(setOf(pkg))

        findings should matchExactly(
            pkg to { result ->
                result.advisor shouldBe scorecard.details
                result.healthMetrics should haveSize(18)

                result.healthSummary shouldNotBeNull {
                    overallScore?.value shouldNotBeNull { this should between(0.0, 10.0, 0.0) }
                    analyzedAt.shouldNotBeNull()
                    analyzedRevision shouldNotBeNull { this should match("[0-9a-f]{40}") }
                    toolVersion shouldNotBeNull { this should startWith("v") }
                }

                result.healthMetrics.forAll {
                    it.minValue shouldBe 0.0
                    it.maxValue shouldBe 10.0
                    it.higherIsBetter shouldBe true
                    it.referenceValue should beNull()
                }

                with(result.healthMetrics.single { it.name == "Binary-Artifacts" }) {
                    value shouldBe 0.0
                    criticality shouldBe Criticality.CRITICAL
                    reason shouldBe "binaries present in source code"

                    details.forAll {
                        it should (
                            startWith("Warn: binary detected: ") and (
                                endWith("gradle/wrapper/gradle-wrapper.jar:1") or
                                    endWith("utils/common/src/funTest/resources/testpkg.deb:1")
                                )
                            )
                    }

                    documentation shouldBe "Determines if the project has generated executable (binary) artifacts in " +
                        "the source repository."
                    documentationLink.toString() should match("$checksUrl#${name.lowercase()}")
                }

                with(result.healthMetrics.single { it.name == "Branch-Protection" }) {
                    value shouldBe 8.0
                    criticality shouldBe Criticality.LOW
                    reason shouldBe "branch protection is not maximal on development and all release branches"
                    details should containExactlyInAnyOrder(
                        "Info: 'allow deletion' disabled on branch 'main'",
                        "Info: 'force pushes' disabled on branch 'main'",
                        "Warn: required approving review count is 1 on branch 'main'",
                        "Warn: codeowners review is not required on branch 'main'",
                        "Info: status check found to merge onto on branch 'main'",
                        "Info: PRs are required in order to make changes on branch 'main'"
                    )
                    documentation shouldBe "Determines if the default and release branches are protected with " +
                        "GitHub's branch protection settings."
                    documentationLink.toString() should match("$checksUrl#${name.lowercase()}")
                }

                with(result.healthMetrics.single { it.name == "CI-Tests" }) {
                    value shouldBe 10.0
                    criticality shouldBe Criticality.LOW
                    reason should match("\\d+ out of \\d+ merged PRs checked by a CI test -- score normalized to 10")
                    details should beEmpty()
                    documentation shouldBe "Determines if the project runs tests before pull requests are merged."
                    documentationLink.toString() should match("$checksUrl#${name.lowercase()}")
                }

                with(result.healthMetrics.single { it.name == "CII-Best-Practices" }) {
                    value shouldBe 5.0
                    criticality shouldBe Criticality.MEDIUM
                    reason shouldBe "badge detected: Passing"
                    details should beEmpty()
                    documentation shouldBe "Determines if the project has an OpenSSF (formerly CII) Best Practices " +
                        "Badge."
                    documentationLink.toString() should match("$checksUrl#${name.lowercase()}")
                }

                with(result.healthMetrics.single { it.name == "Code-Review" }) {
                    value shouldBe 10.0
                    criticality shouldBe Criticality.LOW
                    reason shouldBe "all changesets reviewed"
                    details should beEmpty()
                    documentation shouldBe "Determines if the project requires human code review before pull " +
                        "requests (aka merge requests) are merged."
                    documentationLink.toString() should match("$checksUrl#${name.lowercase()}")
                }

                with(result.healthMetrics.single { it.name == "Contributors" }) {
                    value shouldBe 10.0
                    criticality shouldBe Criticality.LOW
                    reason should match("project has \\d+ contributing companies or organizations")
                    details.shouldBeSingle() should startWith("Info: found contributions from: ")
                    documentation shouldBe "Determines if the project has a set of contributors from multiple " +
                        "organizations (e.g., companies)."
                    documentationLink.toString() should match("$checksUrl#${name.lowercase()}")
                }

                with(result.healthMetrics.single { it.name == "Dangerous-Workflow" }) {
                    value shouldBe 10.0
                    criticality shouldBe Criticality.LOW
                    reason shouldBe "no dangerous workflow patterns detected"
                    details should beEmpty()
                    documentation shouldBe "Determines if the project's GitHub Action workflows avoid dangerous " +
                        "patterns."
                    documentationLink.toString() should match("$checksUrl#${name.lowercase()}")
                }

                with(result.healthMetrics.single { it.name == "Dependency-Update-Tool" }) {
                    value shouldBe 10.0
                    criticality shouldBe Criticality.LOW
                    reason shouldBe "update tool detected"
                    details should containOnly("Info: detected update tool: RenovateBot: renovate.json:1")
                    documentation shouldBe "Determines if the project uses a dependency update tool."
                    documentationLink.toString() should match("$checksUrl#${name.lowercase()}")
                }

                with(result.healthMetrics.single { it.name == "Fuzzing" }) {
                    value shouldBe 0.0
                    criticality shouldBe Criticality.CRITICAL
                    reason shouldBe "project is not fuzzed"
                    details should containOnly("Warn: no fuzzer integrations found")
                    documentation shouldBe "Determines if the project uses fuzzing."
                    documentationLink.toString() should match("$checksUrl#${name.lowercase()}")
                }

                with(result.healthMetrics.single { it.name == "License" }) {
                    value shouldBe 10.0
                    criticality shouldBe Criticality.LOW
                    reason shouldBe "license file detected"
                    details should containOnly(
                        "Info: project has a license file: LICENSE:0",
                        "Info: FSF or OSI recognized license: Apache License 2.0: LICENSE:0"
                    )
                    documentation shouldBe "Determines if the project has defined a license."
                    documentationLink.toString() should match("$checksUrl#${name.lowercase()}")
                }

                with(result.healthMetrics.single { it.name == "Maintained" }) {
                    value shouldBe 10.0
                    criticality shouldBe Criticality.LOW
                    reason should match(
                        "\\d+ commit\\(s\\) and \\d+ issue activity found in the last 90 days -- score normalized to 10"
                    )
                    details should beEmpty()
                    documentation shouldBe "Determines if the project is \"actively maintained\"."
                    documentationLink.toString() should match("$checksUrl#${name.lowercase()}")
                }

                with(result.healthMetrics.single { it.name == "Packaging" }) {
                    value shouldBe 10.0
                    criticality shouldBe Criticality.LOW
                    reason shouldBe "packaging workflow detected"
                    details should containOnly(
                        "Info: Project packages its releases by way of GitHub Actions.: " +
                            ".github/workflows/build-and-test.yml:117"
                    )
                    documentation shouldBe "Determines if the project is published as a package that others can " +
                        "easily download, install, easily update, and uninstall."
                    documentationLink.toString() should match("$checksUrl#${name.lowercase()}")
                }

                with(result.healthMetrics.single { it.name == "Pinned-Dependencies" }) {
                    value shouldBe 5.0
                    criticality shouldBe Criticality.MEDIUM
                    reason shouldBe "dependency not pinned by hash detected -- score normalized to 5"
                    // Do not explicitly assert all reported findings as there are currently tens of them.
                    details shouldHave atLeastSize(40)
                    documentation shouldBe "Determines if the project has declared and pinned the dependencies of " +
                        "its build process."
                    documentationLink.toString() should match("$checksUrl#${name.lowercase()}")
                }

                with(result.healthMetrics.single { it.name == "SAST" }) {
                    value shouldBe 10.0
                    criticality shouldBe Criticality.LOW
                    reason shouldBe "SAST tool is run on all commits"
                    details should containOnly(
                        "Info: SAST configuration detected: CodeQL",
                        "Info: all commits (30) are checked with a SAST tool"
                    )
                    documentation shouldBe "Determines if the project uses static code analysis."
                    documentationLink.toString() should match("$checksUrl#${name.lowercase()}")
                }

                with(result.healthMetrics.single { it.name == "Security-Policy" }) {
                    value shouldBe 0.0
                    criticality shouldBe Criticality.CRITICAL
                    reason shouldBe "security policy file not detected"
                    details should containOnly(
                        "Warn: no security policy file detected",
                        "Warn: no security file to analyze"
                    )
                    documentation shouldBe "Determines if the project has published a security policy."
                    documentationLink.toString() should match("$checksUrl#${name.lowercase()}")
                }

                with(result.healthMetrics.single { it.name == "Signed-Releases" }) {
                    value shouldBe 8.0
                    criticality shouldBe Criticality.LOW
                    reason should match("\\d+ out of the last \\d+ releases have a total of \\d+ signed artifacts.")

                    details.forAll {
                        it should (
                            startWith("Info: signed release artifact: ") or (
                                startWith("Warn: release artifact ") and contain(" does not have provenance: ")
                                )
                            )
                    }

                    documentation shouldBe "Determines if the project cryptographically signs release artifacts."
                    documentationLink.toString() should match("$checksUrl#${name.lowercase()}")
                }

                with(result.healthMetrics.single { it.name == "Token-Permissions" }) {
                    value shouldBe 0.0
                    criticality shouldBe Criticality.CRITICAL
                    reason shouldBe "detected GitHub workflow tokens with excessive permissions"
                    details should containOnly(
                        "Warn: jobLevel 'contents' permission set to 'write': .github/workflows/release.yml:23",
                        "Warn: jobLevel 'checks' permission set to 'write': .github/workflows/static-analysis.yml:95",
                        "Info: jobLevel 'contents' permission set to 'read': .github/workflows/static-analysis.yml:96",
                        "Warn: no topLevel permission defined: .github/workflows/build-and-test.yml:1",
                        "Warn: no topLevel permission defined: .github/workflows/docker-build.yml:1",
                        "Info: topLevel permissions set to 'read-all': .github/workflows/native-build.yml:9",
                        "Warn: no topLevel permission defined: .github/workflows/release.yml:1",
                        "Info: topLevel permissions set to 'read-all': .github/workflows/scorecard-analysis.yml:11",
                        "Warn: no topLevel permission defined: .github/workflows/static-analysis.yml:1",
                        "Warn: no topLevel permission defined: .github/workflows/website-deploy.yml:1",
                        "Warn: no topLevel permission defined: .github/workflows/website-test.yml:1"
                    )
                    documentation shouldBe "Determines if the project's workflows follow the principle of least " +
                        "privilege."
                    documentationLink.toString() should match("$checksUrl#${name.lowercase()}")
                }

                with(result.healthMetrics.single { it.name == "Vulnerabilities" }) {
                    value shouldBe 0.0
                    criticality shouldBe Criticality.CRITICAL
                    reason should match("\\d+ existing vulnerabilities detected")
                    // Do not explicitly assert all reported findings as there are currently hundrets of them.
                    details shouldHave atLeastSize(100)
                    documentation shouldBe "Determines if the project has open, known unfixed vulnerabilities."
                    documentationLink.toString() should match("$checksUrl#${name.lowercase()}")
                }
            }
        )
    }

    "Return findings without a value for checks that could not be computed" {
        val vcs = VcsInfo(
            type = VcsType.GIT,
            url = "https://github.com/jonschlinkert/is-number.git",
            revision = ""
        )
        val pkg = Package.EMPTY.copy(
            id = Identifier("NPM::is-number:7.0.0"),
            vcs = vcs,
            vcsProcessed = vcs
        )

        val findings = scorecard.retrievePackageFindings(setOf(pkg))

        findings should matchExactly(
            pkg to { result ->
                result.advisor shouldBe scorecard.details

                // The repository has no GitHub releases, so Scorecard cannot check whether they are signed.
                with(result.healthMetrics.single { it.name == "Signed-Releases" }) {
                    value should beNull()
                    criticality should beNull()
                    reason shouldBe "no releases found"
                }
            }
        )
    }

    "Return empty health metrics for a package with a non-existing VCS URL" {
        val nonExistingVcs = VcsInfo(
            type = VcsType.GIT,
            // This repository does not exist and never will, because its URL contains disallowed characters.
            url = "https://github.com/täßt/this-will-never-exist.git",
            revision = "dbd50cd755093305319ca85f9aa2a3f5d20320fe"
        )
        val pkg = Package.EMPTY.copy(
            id = Identifier("Maven:org.ossreviewtoolkit:cli:99.0.0"),
            vcs = nonExistingVcs,
            vcsProcessed = nonExistingVcs
        )

        val findings = scorecard.retrievePackageFindings(setOf(pkg))

        findings should matchExactly(
            pkg to { result ->
                result.advisor shouldBe scorecard.details
                result.healthMetrics should beEmpty()
                result.healthSummary should beNull()
            }
        )
    }

    "Return empty health metrics for a package with empty VCS info" {
        val pkg = Package.EMPTY.copy(
            id = Identifier("Maven:org.apache.logging.log4j:log4j-api:2.14.1")
        )

        val findings = scorecard.retrievePackageFindings(setOf(pkg))

        findings should matchExactly(
            pkg to { result ->
                result.advisor shouldBe scorecard.details
                result.healthMetrics should beEmpty()
                result.healthSummary should beNull()
            }
        )
    }

    "Return empty health metrics for a package with a malformed VCS URL" {
        val invalidVcs = VcsInfo(
            type = VcsType.GIT,
            url = "https://invalid-url",
            revision = HashAlgorithm.SHA1.emptyValue
        )
        val pkg = Package.EMPTY.copy(
            id = Identifier("Maven:org.apache.logging.log4j:log4j-api:2.14.1"),
            vcs = invalidVcs,
            vcsProcessed = invalidVcs
        )

        val findings = scorecard.retrievePackageFindings(setOf(pkg))

        findings should matchExactly(
            pkg to { result ->
                result.advisor shouldBe scorecard.details
                result.healthMetrics should beEmpty()
                result.healthSummary should beNull()
            }
        )
    }
})
