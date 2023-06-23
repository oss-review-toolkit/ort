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

package org.ossreviewtoolkit.scanner.scanners

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.should

import java.io.File

import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.PackageType
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.scanner.PathScannerWrapper
import org.ossreviewtoolkit.scanner.ScanContext
import org.ossreviewtoolkit.scanner.Scanner
import org.ossreviewtoolkit.scanner.ScannerCriteria
import org.ossreviewtoolkit.scanner.provenance.DefaultNestedProvenanceResolver
import org.ossreviewtoolkit.scanner.provenance.DefaultPackageProvenanceResolver
import org.ossreviewtoolkit.scanner.provenance.DefaultProvenanceDownloader
import org.ossreviewtoolkit.scanner.provenance.DummyNestedProvenanceStorage
import org.ossreviewtoolkit.scanner.provenance.DummyProvenanceStorage
import org.ossreviewtoolkit.scanner.utils.DefaultWorkingTreeCache
import org.ossreviewtoolkit.utils.common.VCS_DIRECTORIES
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.matchExpectedResult
import org.ossreviewtoolkit.utils.test.patchActualResult

class ScannerIntegrationFunTest : WordSpec({
    "scan()" should {
        "return the expected ORT result for a given analyzer result" {
            val analyzerResult = createAnalyzerResult()
            val expectedResultFile = getAssetFile("dummy-expected-output-for-analyzer-result.yml")

            val result = createScanner().scan(analyzerResult, skipExcluded = false, emptyMap())

            patchActualResult(result.toYaml(), patchStartAndEndTime = true) should
                    matchExpectedResult(expectedResultFile)
        }

        "return the expected (merged) scan results for a given analyzer result" {
            val analyzerResult = createAnalyzerResult()
            val expectedResultFile = getAssetFile("dummy-expected-scan-results-for-analyzer-result.yml")

            val scanResults = createScanner().scan(analyzerResult, skipExcluded = false, emptyMap())
                .getScanResults().toSortedMap()

            patchActualResult(scanResults.toYaml(), patchStartAndEndTime = true) should
                    matchExpectedResult(expectedResultFile)
        }
    }
})

private fun createScanner(): Scanner {
    val downloaderConfiguration = DownloaderConfiguration()
    val workingTreeCache = DefaultWorkingTreeCache()
    val provenanceDownloader = DefaultProvenanceDownloader(downloaderConfiguration, workingTreeCache)
    val packageProvenanceStorage = DummyProvenanceStorage()
    val nestedProvenanceStorage = DummyNestedProvenanceStorage()
    val packageProvenanceResolver = DefaultPackageProvenanceResolver(packageProvenanceStorage, workingTreeCache)
    val nestedProvenanceResolver = DefaultNestedProvenanceResolver(nestedProvenanceStorage, workingTreeCache)
    val dummyScanner = DummyScanner()

    return Scanner(
        scannerConfig = ScannerConfiguration(),
        downloaderConfig = downloaderConfiguration,
        provenanceDownloader = provenanceDownloader,
        storageReaders = emptyList(),
        storageWriters = emptyList(),
        packageProvenanceResolver = packageProvenanceResolver,
        nestedProvenanceResolver = nestedProvenanceResolver,
        scannerWrappers = mapOf(
            PackageType.PROJECT to listOf(dummyScanner),
            PackageType.PACKAGE to listOf(dummyScanner)
        )
    )
}

private fun createAnalyzerResult(): OrtResult {
    val packages = setOf(pkg1, pkg2, pkg3)

    val scope = Scope(
        name = "deps",
        dependencies = packages.mapTo(mutableSetOf()) { PackageReference(it.id) }
    )

    val project = Project.EMPTY.copy(
        id = createId("project"),
        scopeDependencies = setOf(scope)
    )

    val analyzerRun = AnalyzerRun.EMPTY.copy(
        result = AnalyzerResult.EMPTY.copy(
            projects = setOf(project),
            packages = packages.toSet()
        )
    )

    return OrtResult.EMPTY.copy(analyzer = analyzerRun)
}

private fun createId(name: String): Identifier =
    Identifier("Dummy::$name:1.0.0")
private fun createPackage(name: String, vcs: VcsInfo): Package =
    Package.EMPTY.copy(
        id = createId(name),
        vcs = vcs,
        vcsProcessed = vcs.normalize()
    )

private val pkg1 = createPackage(
    name = "pkg1",
    vcs = VcsInfo(
        type = VcsType.GIT,
        url = "https://github.com/oss-review-toolkit/ort-test-data-scanner.git",
        revision = "f6d8b9b73d8dedbe5cf1212b797e09e4f8ea290c",
        path = "pkg1"
    )
)

private val pkg2 = createPackage(
    name = "pkg2",
    vcs = VcsInfo(
        type = VcsType.GIT,
        url = "https://github.com/oss-review-toolkit/ort-test-data-scanner.git",
        revision = "f6d8b9b73d8dedbe5cf1212b797e09e4f8ea290c",
        path = "pkg2"
    )
)

private val pkg3 = createPackage(
    name = "pkg3",
    vcs = VcsInfo(
        type = VcsType.GIT,
        url = "https://github.com/oss-review-toolkit/ort-test-data-scanner.git",
        revision = "f6d8b9b73d8dedbe5cf1212b797e09e4f8ea290c",
        path = "pkg3"
    )
)

private class DummyScanner : PathScannerWrapper {
    override val details = ScannerDetails(name = "Dummy", version = "1.0.0", configuration = "")
    override val criteria = ScannerCriteria.forDetails(details)

    override fun scanPath(path: File, context: ScanContext): ScanSummary {
        val relevantFiles = path.walk()
            .onEnter { it.name !in VCS_DIRECTORIES }
            .filterTo(mutableSetOf()) { it.isFile }

        val licenseFindings = relevantFiles.mapTo(mutableSetOf()) { file ->
            LicenseFinding(
                license = SpdxConstants.NONE,
                location = TextLocation(file.relativeTo(path).invariantSeparatorsPath, TextLocation.UNKNOWN_LINE)
            )
        }

        return ScanSummary.EMPTY.copy(
            licenseFindings = licenseFindings
        )
    }
}
