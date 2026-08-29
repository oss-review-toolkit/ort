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

package org.ossreviewtoolkit.clihelper.commands

import com.github.ajalt.clikt.testing.test

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.clihelper.HelperMain
import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.scanner.storages.PackageBasedFileStorage
import org.ossreviewtoolkit.utils.ort.storage.LocalFileStorage
import org.ossreviewtoolkit.utils.test.scannerRunOf

class ImportScanResultsCommandFunTest : WordSpec({
    "The import scan results command" should {
        "apply a VCS path curation to stored scan results" {
            val packageId = Identifier("Maven:example:package:1.0")
            val repositoryVcs = VcsInfo(VcsType.GIT, "https://example.org/repository.git", "resolved-revision")

            val storageDir = tempdir()
            val outputDir = tempdir()
            val ortFile = tempfile(suffix = ".yml")

            val scanResult = ScanResult(
                provenance = RepositoryProvenance(repositoryVcs, "resolved-revision"),
                scanner = ScannerDetails("scanner", "1.0", "configuration"),
                summary = ScanSummary.EMPTY.copy(
                    licenseFindings = setOf(
                        LicenseFinding("MIT", TextLocation("module/src/test/ModuleTest.kt", 1)),
                        LicenseFinding("MIT", TextLocation("other/src/test/OtherTest.kt", 1))
                    )
                )
            )

            val pkg = Package.EMPTY.copy(
                id = packageId,
                vcs = repositoryVcs,
                vcsProcessed = repositoryVcs.copy(path = "module")
            )

            OrtResult.EMPTY.copy(
                analyzer = AnalyzerRun.EMPTY.copy(
                    result = AnalyzerResult.EMPTY.copy(packages = setOf(pkg))
                ),
                scanner = scannerRunOf(packageId to listOf(scanResult))
            ).also { ortFile.writeText(it.toYaml()) }

            val importResult = HelperMain().test(
                "import-scan-results",
                "--ort-file",
                ortFile.absolutePath,
                "--scan-results-storage-dir",
                storageDir.absolutePath
            )

            importResult.statusCode shouldBe 0

            val storedScanResults = PackageBasedFileStorage(LocalFileStorage(storageDir)).readForId(packageId).getOrThrow()
            storedScanResults.map { (it.provenance as RepositoryProvenance).vcsInfo.path } should containExactly("module")
            storedScanResults.flatMap { it.summary.licenseFindings }.map { it.location.path } should
                containExactly("module/src/test/ModuleTest.kt")

            val createResult = HelperMain().test(
                "package-configuration",
                "create",
                "--scan-results-storage-dir",
                storageDir.absolutePath,
                "--package-id",
                packageId.toCoordinates(),
                "--output-dir",
                outputDir.absolutePath,
                "--generate-path-excludes"
            )

            createResult.statusCode shouldBe 0
            outputDir.resolve("vcs.yml").readValue<PackageConfiguration>().pathExcludes.map { it.pattern } should
                containExactly("module/src/test/**")
        }
    }
})
