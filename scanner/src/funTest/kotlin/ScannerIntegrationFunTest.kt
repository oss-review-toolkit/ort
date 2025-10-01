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

package org.ossreviewtoolkit.scanner

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.downloader.DefaultWorkingTreeCache
import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.scanner.provenance.DefaultNestedProvenanceResolver
import org.ossreviewtoolkit.scanner.provenance.DefaultPackageProvenanceResolver
import org.ossreviewtoolkit.scanner.provenance.DefaultProvenanceDownloader
import org.ossreviewtoolkit.scanner.provenance.DummyNestedProvenanceStorage
import org.ossreviewtoolkit.scanner.provenance.DummyProvenanceStorage
import org.ossreviewtoolkit.utils.test.patchActualResult
import org.ossreviewtoolkit.utils.test.patchExpectedResult
import org.ossreviewtoolkit.utils.test.readResource

class ScannerIntegrationFunTest : WordSpec({
    val workingTreeCache = DefaultWorkingTreeCache()
    val scannerWrapper = DummyPathScannerWrapper()
    val scanner = createScanner(
        provenanceDownloader = DefaultProvenanceDownloader(DownloaderConfiguration(), workingTreeCache),
        packageProvenanceResolver = DefaultPackageProvenanceResolver(DummyProvenanceStorage(), workingTreeCache),
        nestedProvenanceResolver = DefaultNestedProvenanceResolver(DummyNestedProvenanceStorage(), workingTreeCache),
        packageScannerWrappers = listOf(scannerWrapper),
        projectScannerWrappers = listOf(scannerWrapper)
    )

    "Scanning all packages corresponding to a single VCS" should {
        val analyzerResult = createAnalyzerResult(pkg0, pkg1, pkg2, pkg3, pkg4)

        val ortResult = scanner.scan(analyzerResult, skipExcluded = false, emptyMap())

        "return the expected ORT result" {
            val expectedResult = readResource("/scanner-integration-all-pkgs-expected-ort-result.yml")

            patchActualResult(ortResult.toYaml(), patchStartAndEndTime = true) shouldBe
                patchExpectedResult(expectedResult)
        }

        "return the expected (merged) scan results" {
            val expectedResult = readResource("/scanner-integration-expected-scan-results.yml")

            val scanResults = ortResult.getScanResults().toSortedMap()

            patchActualResult(scanResults.toYaml(), patchStartAndEndTime = true) shouldBe
                patchExpectedResult(expectedResult)
        }

        "return the expected (merged) file lists" {
            val expectedResult = readResource("/scanner-integration-expected-file-lists.yml")

            val fileLists = ortResult.getFileLists().toSortedMap()

            fileLists.toYaml() shouldBe patchExpectedResult(expectedResult)
        }
    }

    "Scanning a subset of the packages corresponding to a single VCS" should {
        "return the expected ORT result" {
            val analyzerResult = createAnalyzerResult(pkg1, pkg3)
            val expectedResult = readResource("/scanner-integration-subset-pkgs-expected-ort-result.yml")

            val ortResult = scanner.scan(analyzerResult, skipExcluded = false, emptyMap())

            patchActualResult(ortResult.toYaml(), patchStartAndEndTime = true) shouldBe
                patchExpectedResult(expectedResult)
        }
    }

    "Scanning a project with the same provenance as packages" should {
        "not have duplicated scan results" {
            val analyzerResult = createAnalyzerResultWithProject(project0, pkg0)

            val ortResult = scanner.scan(analyzerResult, skipExcluded = false, emptyMap())

            ortResult.getScanResultsForId(project0.id) shouldHaveSize 1
        }
    }
})

private fun createAnalyzerResult(vararg packages: Package): OrtResult {
    val project = Project.EMPTY.copy(
        id = createId("project")
    )

    return createAnalyzerResultWithProject(project, *packages)
}

private fun createAnalyzerResultWithProject(project: Project, vararg packages: Package): OrtResult {
    val scope = Scope(
        name = "deps",
        dependencies = packages.mapTo(mutableSetOf()) { PackageReference(it.id) }
    )

    val projectWithScope = project.copy(
        scopeDependencies = setOf(scope)
    )

    val analyzerRun = AnalyzerRun.EMPTY.copy(
        result = AnalyzerResult.EMPTY.copy(
            projects = setOf(projectWithScope),
            packages = packages.toSet()
        ),
        config = AnalyzerConfiguration(enabledPackageManagers = emptyList())
    )

    return OrtResult.EMPTY.copy(
        analyzer = analyzerRun,
        repository = Repository.EMPTY.copy(
            vcsProcessed = projectWithScope.vcsProcessed,
            vcs = projectWithScope.vcs
        )
    )
}

private fun createId(name: String): Identifier = Identifier("Dummy::$name:1.0.0")

private fun createPackage(name: String, vcs: VcsInfo): Package =
    Package.EMPTY.copy(
        id = createId(name),
        vcs = vcs,
        vcsProcessed = vcs.normalize()
    )

private fun createProject(name: String, vcs: VcsInfo): Project =
    Project.EMPTY.copy(
        id = createId(name),
        vcs = vcs,
        vcsProcessed = vcs.normalize()
    )

private val project0 = createProject(
    name = "project",
    vcs = VcsInfo(
        type = VcsType.GIT,
        url = "https://github.com/oss-review-toolkit/ort-test-data-scanner.git",
        revision = "97d57bb4795bc41f496e1a8e2c7751cefc7da7ec",
        path = ""
    )
)

// A package with an empty VCS path.
private val pkg0 = createPackage(
    name = "pkg0",
    vcs = VcsInfo(
        type = VcsType.GIT,
        url = "https://github.com/oss-review-toolkit/ort-test-data-scanner.git",
        revision = "97d57bb4795bc41f496e1a8e2c7751cefc7da7ec",
        path = ""
    )
)

// A package within a VCS path.
private val pkg1 = createPackage(
    name = "pkg1",
    vcs = VcsInfo(
        type = VcsType.GIT,
        url = "https://github.com/oss-review-toolkit/ort-test-data-scanner.git",
        revision = "97d57bb4795bc41f496e1a8e2c7751cefc7da7ec",
        path = "pkg1"
    )
)

// A package within a VCS path.
private val pkg2 = createPackage(
    name = "pkg2",
    vcs = VcsInfo(
        type = VcsType.GIT,
        url = "https://github.com/oss-review-toolkit/ort-test-data-scanner.git",
        revision = "97d57bb4795bc41f496e1a8e2c7751cefc7da7ec",
        path = "pkg2"
    )
)

// A package within a VCS path, containing sub-repository.
private val pkg3 = createPackage(
    name = "pkg3",
    vcs = VcsInfo(
        type = VcsType.GIT,
        url = "https://github.com/oss-review-toolkit/ort-test-data-scanner.git",
        revision = "97d57bb4795bc41f496e1a8e2c7751cefc7da7ec",
        path = "pkg3"
    )
)

// A package within a VCS path, containing sub-repository.
private val pkg4 = createPackage(
    name = "pkg4",
    vcs = VcsInfo(
        type = VcsType.GIT,
        url = "https://github.com/oss-review-toolkit/ort-test-data-scanner.git",
        revision = "97d57bb4795bc41f496e1a8e2c7751cefc7da7ec",
        path = "pkg4"
    )
)
