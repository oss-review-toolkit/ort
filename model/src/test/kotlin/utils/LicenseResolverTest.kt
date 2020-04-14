/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.model.utils

import io.kotest.core.spec.style.WordSpec
import io.kotest.core.test.TestCase
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should

import org.ossreviewtoolkit.model.AccessStatistics
import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.Environment
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.ScanRecord
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanResultContainer
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration

import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

private fun OrtResult.addPackage(pkg: Package): OrtResult =
    copy(
        analyzer = analyzer!!.copy(
            result = analyzer!!.result.copy(
                packages = (analyzer!!.result.packages + pkg.toCuratedPackage()).toSortedSet()
            )
        )
    )

private fun OrtResult.addProject(project: Project): OrtResult =
    copy(
        analyzer = analyzer!!.copy(
            result = analyzer!!.result.copy(
                projects = (analyzer!!.result.projects + project).toSortedSet()
            )
        )
    )

private fun OrtResult.addScanResult(id: Identifier, scanResult: ScanResult): OrtResult {
    val scanResults = scanner!!.results.scanResults
    val scanResultsForId = scanResults.find { it.id == id }?.let {
        scanResults.remove(it)
        it.results
    }.orEmpty()

    scanResults += ScanResultContainer(id, scanResultsForId + scanResult)

    return copy(
        scanner = scanner!!.copy(
            results = scanner!!.results.copy(
                scanResults = scanResults
            )
        )
    )
}

private fun OrtResult.getVcsProvenance(id: Identifier): Provenance? =
    getProject(id)?.let { Provenance(vcsInfo = it.vcsProcessed) }
        ?: getPackage(id)?.let { Provenance(vcsInfo = it.pkg.vcsProcessed) }

private fun OrtResult.getSourceArtifactProvenance(id: Identifier): Provenance? =
    getPackage(id)?.let { Provenance(sourceArtifact = it.pkg.sourceArtifact) }

private fun createEmptyOrtResult() =
    OrtResult(
        Repository.EMPTY,
        AnalyzerRun(
            environment = Environment(),
            config = AnalyzerConfiguration(ignoreToolVersions = true, allowDynamicVersions = true),
            result = AnalyzerResult.EMPTY
        ),
        ScannerRun(
            environment = Environment(),
            config = ScannerConfiguration(),
            results = ScanRecord(
                scanResults = sortedSetOf(),
                storageStats = AccessStatistics()
            )
        )
    )

class LicenseResolverTest : WordSpec() {
    private val nextId = AtomicInteger(1)
    private lateinit var ortResult: OrtResult
    private val packageConfigurations = mutableListOf<PackageConfiguration>()

    private fun createResolver() = LicenseResolver(ortResult, SimplePackageConfigurationProvider(packageConfigurations))

    private enum class ProvenanceType {
        VCS,
        SOURCE_ARTIFACT
    }

    private fun setupScanResult(id: Identifier, type: ProvenanceType, licenseFindings: Collection<LicenseFinding>) {
        val scanResult = ScanResult(
            provenance = when (type) {
                ProvenanceType.SOURCE_ARTIFACT -> ortResult.getSourceArtifactProvenance(id)
                else -> ortResult.getVcsProvenance(id)
            }!!,
            scanner = ScannerDetails.EMPTY,
            summary = ScanSummary(
                startTime = Instant.MIN,
                endTime = Instant.MAX,
                fileCount = 0,
                licenseFindings = licenseFindings.toSortedSet(),
                copyrightFindings = sortedSetOf<CopyrightFinding>(),
                packageVerificationCode = "",
                issues = emptyList()
            )
        )

        ortResult = ortResult.addScanResult(id, scanResult)
    }

    private fun setupPackage(): Package {
        val id = nextId.getAndIncrement().toString()
        val pkg = Package.EMPTY.copy(
            id = Identifier(id),
            sourceArtifact = RemoteArtifact("http://some-host/$id", Hash.NONE),
            vcsProcessed = VcsInfo.EMPTY.copy(url = id, revision = id, path = id)
        )

        ortResult = ortResult.addPackage(pkg)

        return pkg
    }

    private fun setupProject(): Project {
        val id = nextId.getAndIncrement().toString()
        val project = Project.EMPTY.copy(
            id = Identifier(id),
            vcsProcessed = ortResult.repository.vcsProcessed
        )

        ortResult = ortResult.addProject(project)

        return project
    }

    override fun beforeTest(testCase: TestCase) {
        ortResult = createEmptyOrtResult()
        packageConfigurations.clear()
    }

    init {
        "getDetectedLicensesForId()" should {
            "return all detected licenses for a package with a VCS scan result" {
                val id = setupPackage().id
                setupScanResult(
                    id, ProvenanceType.VCS, listOf(
                        LicenseFinding(
                            license = "Apache-2.0",
                            location = TextLocation("a.txt", startLine = 1, endLine = 1)
                        ),
                        LicenseFinding(
                            license = "MIT",
                            location = TextLocation("m.txt", startLine = 1, endLine = 1)
                        )
                    )
                )

                val result = createResolver().getDetectedLicensesForId(id)

                result should containExactlyInAnyOrder("Apache-2.0", "MIT")
            }

            "return all detected licenses for a package with a source artifact scan result" {
                val id = setupPackage().id
                setupScanResult(
                    id, ProvenanceType.SOURCE_ARTIFACT, listOf(
                        LicenseFinding(
                            license = "Apache-2.0",
                            location = TextLocation("some/path", startLine = 1, endLine = 1)
                        ),
                        LicenseFinding(
                            license = "MIT",
                            location = TextLocation("some/path", startLine = 1, endLine = 1)
                        )
                    )
                )

                val result = createResolver().getDetectedLicensesForId(id)

                result should containExactlyInAnyOrder("Apache-2.0", "MIT")
            }

            "return all detected licenses for a package with a VCS and a source artifact scan result" {
                val id = setupPackage().id
                setupScanResult(
                    id, ProvenanceType.VCS, listOf(
                        LicenseFinding(
                            license = "Apache-2.0",
                            location = TextLocation("some/path", startLine = 1, endLine = 1)
                        )
                    )
                )
                setupScanResult(
                    id, ProvenanceType.SOURCE_ARTIFACT, listOf(
                        LicenseFinding(
                            license = "MIT",
                            location = TextLocation("some/path", startLine = 1, endLine = 1)
                        )
                    )
                )

                val result = createResolver().getDetectedLicensesForId(id)

                result should containExactlyInAnyOrder("Apache-2.0", "MIT")
            }

            "return all detected licenses for a package with multiple VCS and source artifact scan results" {
                val id = setupPackage().id
                setupScanResult(
                    id, ProvenanceType.VCS, listOf(
                        LicenseFinding(
                            license = "BSD-2-Clause",
                            location = TextLocation("some/path", startLine = 1, endLine = 1)
                        )
                    )
                )
                setupScanResult(
                    id, ProvenanceType.VCS, listOf(
                        LicenseFinding(
                            license = "BSD-3-Clause",
                            location = TextLocation("some/path", startLine = 1, endLine = 1)
                        )
                    )
                )
                setupScanResult(
                    id, ProvenanceType.SOURCE_ARTIFACT, listOf(
                        LicenseFinding(
                            license = "Apache-2.0",
                            location = TextLocation("some/path", startLine = 1, endLine = 1)
                        )
                    )
                )
                setupScanResult(
                    id, ProvenanceType.SOURCE_ARTIFACT, listOf(
                        LicenseFinding(
                            license = "MIT",
                            location = TextLocation("some/path", startLine = 1, endLine = 1)
                        )
                    )
                )

                val result = createResolver().getDetectedLicensesForId(id)

                result should containExactlyInAnyOrder("BSD-2-Clause", "BSD-3-Clause", "Apache-2.0", "MIT")
            }

            "return no detected license for a package without scan result and unrelated other scan results" {
                val id = setupPackage().id
                setupScanResult(
                    id, ProvenanceType.VCS, listOf(
                        LicenseFinding(
                            license = "BSD-2-Clause",
                            location = TextLocation("some/path", startLine = 1, endLine = 1)
                        )
                    )
                )
                setupScanResult(
                    id, ProvenanceType.SOURCE_ARTIFACT, listOf(
                        LicenseFinding(
                            license = "BSD-3-Clause",
                            location = TextLocation("some/path", startLine = 1, endLine = 1)
                        )
                    )
                )
                val idForPackageWithoutScanResult = setupPackage().id

                val result = createResolver().getDetectedLicensesForId(idForPackageWithoutScanResult)

                result should beEmpty()
            }

            "return all detected licenses for a project with multiple scan results" {
                val id = setupPackage().id
                setupScanResult(
                    id, ProvenanceType.VCS, listOf(
                        LicenseFinding(
                            license = "BSD-2-Clause",
                            location = TextLocation("some/path", startLine = 1, endLine = 1)
                        )
                    )
                )
                setupScanResult(
                    id, ProvenanceType.VCS, listOf(
                        LicenseFinding(
                            license = "BSD-3-Clause",
                            location = TextLocation("some/path", startLine = 1, endLine = 1)
                        )
                    )
                )

                val result = createResolver().getDetectedLicensesForId(id)

                result should containExactlyInAnyOrder("BSD-2-Clause", "BSD-3-Clause")
            }

            "return no detected license for a project without scan result and unrelated other scan results" {
                val id = setupProject().id
                setupScanResult(
                    id, ProvenanceType.VCS, listOf(
                        LicenseFinding(
                            license = "BSD-2-Clause",
                            location = TextLocation("some/path", startLine = 1, endLine = 1)
                        )
                    )
                )
                val idForProjectWithoutScanResult = setupProject().id

                val result = createResolver().getDetectedLicensesForId(idForProjectWithoutScanResult)

                result should beEmpty()
            }
        }
    }
}
