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

package org.ossreviewtoolkit.clihelper.commands.packageconfig

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

class CreateCommandFunTest : WordSpec({
    "The package configuration create command" should {
        "apply a VCS path curation from an ORT result to a stored scan result" {
            val packageId = Identifier("Maven:example:package:1.0")
            val repositoryVcs = VcsInfo(VcsType.GIT, "https://example.org/repository.git", "main")

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

            PackageBasedFileStorage(LocalFileStorage(storageDir)).add(packageId, scanResult).getOrThrow()

            val pkg = Package.EMPTY.copy(
                id = packageId,
                vcs = repositoryVcs,
                vcsProcessed = repositoryVcs.copy(path = "module")
            )

            OrtResult.EMPTY.copy(
                analyzer = AnalyzerRun.EMPTY.copy(
                    result = AnalyzerResult.EMPTY.copy(packages = setOf(pkg))
                )
            ).also { ortFile.writeText(it.toYaml()) }

            val result = HelperMain().test(
                "package-configuration",
                "create",
                "--scan-results-storage-dir",
                storageDir.absolutePath,
                "--package-id",
                packageId.toCoordinates(),
                "--ort-file",
                ortFile.absolutePath,
                "--output-dir",
                outputDir.absolutePath,
                "--generate-path-excludes"
            )

            result.statusCode shouldBe 0
            outputDir.resolve("vcs.yml").readValue<PackageConfiguration>().pathExcludes.map { it.pattern } should
                containExactly("module/src/test/**")
        }
    }
})
