/*
 * Copyright (C) 2021 HERE Europe B.V.
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

package org.ossreviewtoolkit.reporter.utils

import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

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
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.ScanRecord
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanResultContainer
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.licenses.LicenseClassifications
import org.ossreviewtoolkit.model.licenses.ResolvedLicense
import org.ossreviewtoolkit.model.utils.DefaultResolutionProvider
import org.ossreviewtoolkit.spdx.SpdxSingleLicenseExpression
import org.ossreviewtoolkit.spdx.toSpdx
import org.ossreviewtoolkit.utils.Environment
import org.ossreviewtoolkit.utils.test.DEFAULT_ANALYZER_CONFIGURATION

private fun scanResultContainer(
    id: String,
    vcsInfo: VcsInfo,
    findingsPaths: Collection<String>
): ScanResultContainer {
    val licenseFindings = findingsPaths.mapTo(sortedSetOf()) { LicenseFinding("MIT", TextLocation(it, 1)) }
    val copyrightFindings = findingsPaths.mapTo(sortedSetOf()) { CopyrightFinding("(c)", TextLocation(it, 1)) }

    return ScanResultContainer(
        id = Identifier(id),
        results = listOf(
            ScanResult(
                provenance = Provenance(vcsInfo = vcsInfo),
                scanner = ScannerDetails(name = "scanner", version = "1.0", configuration = ""),
                summary = ScanSummary(
                    startTime = Instant.EPOCH,
                    endTime = Instant.EPOCH,
                    fileCount = 0,
                    packageVerificationCode = "",
                    licenseFindings = licenseFindings,
                    copyrightFindings = copyrightFindings,
                )
            )
        )
    )
}

private val PROJECT_VCS_INFO = VcsInfo(
    type = VcsType.GIT_REPO,
    url = "ssh://git@host/manifests/repo",
    path = "path/to/manifest.xml",
    revision = "deadbeaf44444444333333332222222211111111",
    resolvedRevision = "deadbeaf44444444333333332222222211111111"
)
private val NESTED_VCS_INFO = VcsInfo(
    type = VcsType.GIT,
    url = "ssh://git@host/project/repo",
    path = "",
    revision = "0000000000000000000000000000000000000000",
    resolvedRevision = "0000000000000000000000000000000000000000"
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
            scanResults = sortedSetOf(
                scanResultContainer(
                    id = "NPM:@ort:project-in-root-dir:1.0",
                    vcsInfo = PROJECT_VCS_INFO,
                    findingsPaths = listOf(
                        "src/main.js",
                        "sub-dir/src/main.cpp",
                        "nested-vcs-dir/src/main.cpp"
                    )
                ),
                scanResultContainer(
                    id = "SpdxDocumentFile:@ort:project-in-sub-dir:1.0",
                    vcsInfo = PROJECT_VCS_INFO,
                    findingsPaths = listOf(
                        "sub-dir/src/main.cpp"
                    )
                ),
                scanResultContainer(
                    id = "SpdxDocumentFile:@ort:project-in-nested-vcs:1.0",
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

    "mergeResolvedLicenses" should {
        "merge the original expressions by license sources" {
            val resolvedLicenses = listOf(
                ResolvedLicense(
                    license = "MIT".toSpdx() as SpdxSingleLicenseExpression,
                    originalDeclaredLicenses = emptySet(),
                    originalExpressions = mapOf(LicenseSource.DECLARED to setOf("MIT".toSpdx())),
                    locations = emptySet()
                ),
                ResolvedLicense(
                    license = "MIT".toSpdx() as SpdxSingleLicenseExpression,
                    originalDeclaredLicenses = emptySet(),
                    originalExpressions = mapOf(LicenseSource.DECLARED to setOf("GPL-2.0-only OR MIT".toSpdx())),
                    locations = emptySet()
                )
            )

            val result = FreemarkerTemplateProcessor.TemplateHelper(
                OrtResult.EMPTY,
                LicenseClassifications(),
                DefaultResolutionProvider()
            ).mergeResolvedLicenses(resolvedLicenses)

            with(result[0]) {
                originalExpressions[LicenseSource.DECLARED] shouldBe setOf(
                    "MIT".toSpdx(),
                    "GPL-2.0-only OR MIT".toSpdx()
                )
            }
        }
    }
})
