/*
 * Copyright (C) 2021 HERE Europe B.V.
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

package org.ossreviewtoolkit.reporter.reporters.freemarker

import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockk

import java.time.Instant

import org.ossreviewtoolkit.model.AccessStatistics
import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.LicenseSource
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanRecord
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.LicenseChoices
import org.ossreviewtoolkit.model.config.PackageLicenseChoice
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.licenses.LicenseInfoResolver
import org.ossreviewtoolkit.model.licenses.ResolvedLicense
import org.ossreviewtoolkit.model.licenses.ResolvedLicenseInfo
import org.ossreviewtoolkit.model.licenses.ResolvedOriginalExpression
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.spdx.SpdxConstants
import org.ossreviewtoolkit.spdx.SpdxSingleLicenseExpression
import org.ossreviewtoolkit.spdx.model.LicenseChoice
import org.ossreviewtoolkit.spdx.toSpdx
import org.ossreviewtoolkit.utils.Environment
import org.ossreviewtoolkit.utils.test.DEFAULT_ANALYZER_CONFIGURATION

private fun scanResults(
    vcsInfo: VcsInfo,
    findingsPaths: Collection<String>
): List<ScanResult> {
    val licenseFindings = findingsPaths.mapTo(sortedSetOf()) { LicenseFinding("MIT", TextLocation(it, 1)) }
    val copyrightFindings = findingsPaths.mapTo(sortedSetOf()) { CopyrightFinding("(c)", TextLocation(it, 1)) }

    return listOf(
        ScanResult(
            provenance = RepositoryProvenance(vcsInfo = vcsInfo, resolvedRevision = vcsInfo.revision),
            scanner = ScannerDetails(name = "scanner", version = "1.0", configuration = ""),
            summary = ScanSummary(
                startTime = Instant.EPOCH,
                endTime = Instant.EPOCH,
                packageVerificationCode = "",
                licenseFindings = licenseFindings,
                copyrightFindings = copyrightFindings,
            )
        )
    )
}

private val PROJECT_VCS_INFO = VcsInfo(
    type = VcsType.GIT_REPO,
    url = "ssh://git@host/manifests/repo",
    path = "path/to/manifest.xml",
    revision = "deadbeaf44444444333333332222222211111111"
)
private val NESTED_VCS_INFO = VcsInfo(
    type = VcsType.GIT,
    url = "ssh://git@host/project/repo",
    path = "",
    revision = "0000000000000000000000000000000000000000"
)

private val ORT_RESULT = OrtResult(
    repository = Repository(
        vcs = PROJECT_VCS_INFO,
        config = RepositoryConfiguration(),
        nestedRepositories = mapOf("nested-vcs-dir" to NESTED_VCS_INFO)
    ),
    analyzer = AnalyzerRun(
        environment = Environment(),
        config = DEFAULT_ANALYZER_CONFIGURATION,
        result = AnalyzerResult.EMPTY.copy(
            projects = sortedSetOf(
                Project.EMPTY.copy(
                    id = Identifier("NPM:@ort:project-in-root-dir:1.0"),
                    definitionFilePath = "package.json",
                    vcsProcessed = PROJECT_VCS_INFO
                ),
                Project.EMPTY.copy(
                    id = Identifier("SpdxDocumentFile:@ort:project-in-sub-dir:1.0"),
                    definitionFilePath = "sub-dir/project.spdx.yml",
                    vcsProcessed = PROJECT_VCS_INFO
                ),
                Project.EMPTY.copy(
                    id = Identifier("SpdxDocumentFile:@ort:project-in-nested-vcs:1.0"),
                    definitionFilePath = "nested-vcs-dir/project.spdx.yml",
                    vcsProcessed = NESTED_VCS_INFO
                )
            )
        )
    ),
    scanner = ScannerRun(
        environment = Environment(),
        config = ScannerConfiguration(),
        results = ScanRecord(
            scanResults = sortedMapOf(
                Identifier("NPM:@ort:project-in-root-dir:1.0") to scanResults(
                    vcsInfo = PROJECT_VCS_INFO,
                    findingsPaths = listOf(
                        "src/main.js",
                        "sub-dir/src/main.cpp",
                        "nested-vcs-dir/src/main.cpp"
                    )
                ),
                Identifier("SpdxDocumentFile:@ort:project-in-sub-dir:1.0") to scanResults(
                    vcsInfo = PROJECT_VCS_INFO,
                    findingsPaths = listOf(
                        "sub-dir/src/main.cpp"
                    )
                ),
                Identifier("SpdxDocumentFile:@ort:project-in-nested-vcs:1.0") to scanResults(
                    vcsInfo = NESTED_VCS_INFO,
                    findingsPaths = listOf(
                        "src/main.cpp"
                    )
                )
            ),
            storageStats = AccessStatistics()
        )
    )
)

/**
 * Prepare the given [mock for a LicenseInfoResolver][resolverMock] to return a [ResolvedLicenseInfo] for the given
 * [id]. The [ResolvedLicenseInfo] contains a single [ResolvedLicense], which is constructed based on the provided
 * [license] and [originalExpressions] map.
 */
private fun expectResolveLicenseInfo(
    resolverMock: LicenseInfoResolver,
    id: Identifier,
    license: String,
    originalExpressions: Set<ResolvedOriginalExpression> = emptySet()
) {
    val resolvedLicense = ResolvedLicense(
        license = license.toSpdx() as SpdxSingleLicenseExpression,
        originalDeclaredLicenses = emptySet(),
        originalExpressions = originalExpressions,
        locations = emptySet()
    )

    val info = ResolvedLicenseInfo(id, mockk(), listOf(resolvedLicense), emptyMap(), emptyMap())

    every { resolverMock.resolveLicenseInfo(id) } returns info
}

/**
 * Like [expectResolveLicenseInfo] with multiple licenses that have same originalExpression.
 */
private fun expectResolveLicenseInfo(
    resolverMock: LicenseInfoResolver,
    id: Identifier,
    licenses: List<String>,
    originalExpressions: Set<ResolvedOriginalExpression> = emptySet()
) {
    val resolvedLicenses = licenses.map { license ->
        ResolvedLicense(
            license = license.toSpdx() as SpdxSingleLicenseExpression,
            originalDeclaredLicenses = emptySet(),
            originalExpressions = originalExpressions,
            locations = emptySet()
        )
    }

    val info = ResolvedLicenseInfo(id, mockk(), resolvedLicenses, emptyMap(), emptyMap())

    every { resolverMock.resolveLicenseInfo(id) } returns info
}

/**
 * Return a list with the projects contained in the test result. The set of projects from the result is converted to
 * a list, so that single projects can be accessed by index.
 */
private fun testProjects() = ORT_RESULT.getProjects().toList()

class FreeMarkerTemplateProcessorTest : WordSpec({
    "deduplicateProjectScanResults" should {
        val targetProjects = setOf(
            Identifier("SpdxDocumentFile:@ort:project-in-sub-dir:1.0"),
            Identifier("SpdxDocumentFile:@ort:project-in-nested-vcs:1.0")
        )

        val ortResult = ORT_RESULT.deduplicateProjectScanResults(targetProjects)

        "keep the findings of all target projects" {
            targetProjects.forAll { targetProject ->
                ortResult.getScanResultsForId(targetProject) shouldBe ORT_RESULT.getScanResultsForId(targetProject)
            }
        }

        "remove the findings of all target projects from the root project" {
            val scanResult = ortResult.getScanResultsForId(Identifier("NPM:@ort:project-in-root-dir:1.0")).single()

            with(scanResult.summary) {
                copyrightFindings.map { it.location.path } shouldContainExactlyInAnyOrder listOf(
                    "src/main.js"
                )

                licenseFindings.map { it.location.path } shouldContainExactlyInAnyOrder listOf(
                    "src/main.js"
                )
            }
        }
    }

    "mergeLicenses" should {
        "merge the expressions from packages by license sources" {
            val projects = testProjects()
            val resolver = mockk<LicenseInfoResolver>()

            expectResolveLicenseInfo(
                resolver,
                projects[0].id,
                "MIT",
                setOf(ResolvedOriginalExpression("MIT".toSpdx(), LicenseSource.DECLARED))
            )
            expectResolveLicenseInfo(
                resolver,
                projects[1].id,
                "MIT",
                setOf(ResolvedOriginalExpression("GPL-2.0-only OR MIT".toSpdx(), LicenseSource.DECLARED))
            )
            expectResolveLicenseInfo(
                resolver,
                projects[2].id,
                SpdxConstants.NOASSERTION,
                setOf(ResolvedOriginalExpression(SpdxConstants.NOASSERTION.toSpdx(), LicenseSource.DECLARED))
            )

            val input = ReporterInput(ORT_RESULT, licenseInfoResolver = resolver)
            val pkg1 = FreemarkerTemplateProcessor.PackageModel(projects[0].id, input)
            val pkg2 = FreemarkerTemplateProcessor.PackageModel(projects[1].id, input)
            val pkg3 = FreemarkerTemplateProcessor.PackageModel(projects[2].id, input)

            val result = FreemarkerTemplateProcessor.TemplateHelper(input).mergeLicenses(listOf(pkg1, pkg2, pkg3))

            result should haveSize(2)
            with(result[0]) {
                license.toString() shouldBe "MIT"
                originalExpressions.map { it.expression.toString() } shouldContainExactlyInAnyOrder listOf(
                    "MIT",
                    "GPL-2.0-only OR MIT"
                )
            }

            result[1].license.toString() shouldBe SpdxConstants.NOASSERTION
        }

        "filter licenses of excluded packages" {
            val projects = testProjects()
            val resolver = mockk<LicenseInfoResolver>()

            expectResolveLicenseInfo(
                resolver,
                projects[0].id,
                "MIT",
                setOf(ResolvedOriginalExpression("MIT".toSpdx(), LicenseSource.DECLARED))
            )

            val mockResult = mockk<OrtResult>()
            every { mockResult.isExcluded(any()) } returns false
            every { mockResult.isExcluded(projects[1].id) } returns true
            every { mockResult.getPackageLicenseChoices(projects[0].id) } returns emptyList()
            every { mockResult.getPackageLicenseChoices(projects[1].id) } returns emptyList()
            every { mockResult.getRepositoryLicenseChoices() } returns emptyList()
            val input = ReporterInput(mockResult, licenseInfoResolver = resolver)
            val pkg1 = FreemarkerTemplateProcessor.PackageModel(projects[0].id, input)
            val pkg2 = FreemarkerTemplateProcessor.PackageModel(projects[1].id, input)

            val result = FreemarkerTemplateProcessor.TemplateHelper(input).mergeLicenses(listOf(pkg1, pkg2))

            result should haveSize(1)
            result.first().license.toString() shouldBe "MIT"
        }

        "filter NO_ASSERTION licenses" {
            val projects = testProjects()
            val resolver = mockk<LicenseInfoResolver>()

            expectResolveLicenseInfo(
                resolver,
                projects[0].id,
                "MIT",
                setOf(ResolvedOriginalExpression("MIT".toSpdx(), LicenseSource.DECLARED))
            )
            expectResolveLicenseInfo(
                resolver,
                projects[1].id,
                SpdxConstants.NOASSERTION,
                setOf(ResolvedOriginalExpression(SpdxConstants.NOASSERTION.toSpdx(), LicenseSource.DECLARED))
            )

            val input = ReporterInput(ORT_RESULT, licenseInfoResolver = resolver)
            val pkg1 = FreemarkerTemplateProcessor.PackageModel(projects[0].id, input)
            val pkg2 = FreemarkerTemplateProcessor.PackageModel(projects[1].id, input)

            val result = FreemarkerTemplateProcessor.TemplateHelper(input)
                .mergeLicenses(listOf(pkg1, pkg2), omitNotPresent = true)

            result should haveSize(1)
            result.first().license.toString() shouldBe "MIT"
        }

        "apply license choices" {
            val projects = testProjects()
            val resolver = mockk<LicenseInfoResolver>()

            expectResolveLicenseInfo(
                resolver,
                projects[1].id,
                listOf("MIT", "GPL-2.0-only", "Apache-2.0"),
                setOf(ResolvedOriginalExpression("GPL-2.0-only OR MIT OR Apache-2.0".toSpdx(), LicenseSource.DECLARED))
            )

            val ortResult = ORT_RESULT.copy(
                repository = ORT_RESULT.repository.copy(
                    config = RepositoryConfiguration(
                        licenseChoices = LicenseChoices(
                            repositoryLicenseChoices = listOf(
                                LicenseChoice("GPL-2.0-only OR MIT".toSpdx(), "MIT".toSpdx())
                            ),
                            packageLicenseChoices = listOf(
                                PackageLicenseChoice(
                                    projects[1].id,
                                    listOf(LicenseChoice("MIT OR Apache-2.0".toSpdx(), "MIT".toSpdx()))
                                )
                            )
                        )
                    )
                )
            )

            val input = ReporterInput(ortResult, licenseInfoResolver = resolver)
            val pkg = FreemarkerTemplateProcessor.PackageModel(projects[1].id, input)

            val result = FreemarkerTemplateProcessor.TemplateHelper(input).mergeLicenses(listOf(pkg))

            result should haveSize(1)
            with(result[0]) {
                license.toString() shouldBe "MIT"
                originalExpressions.map { it.expression.toString() } shouldContainExactlyInAnyOrder listOf(
                    "GPL-2.0-only OR MIT OR Apache-2.0"
                )
            }
        }
    }
})
