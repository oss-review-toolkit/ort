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

import io.kotest.core.TestConfiguration
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.clihelper.HelperMain
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.PackageCurationData
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsInfoCurationData
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.config.ProviderPluginConfiguration
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.scanner.storages.PackageBasedFileStorage
import org.ossreviewtoolkit.utils.ort.storage.LocalFileStorage

class CreateCommandFunTest : WordSpec({
    "The package-configuration create command" should {
        "apply a VCS path curation configured via --config to stored scan results" {
            val packageId = Identifier("Maven:example:package:1.0")
            val repositoryVcs = VcsInfo(VcsType.GIT, "https://example.org/repository.git", "resolved-revision")

            val storageDir = tempdir()
            val outputDir = tempdir()
            val ortConfigFile = createOrtConfig()

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

            val storage = PackageBasedFileStorage(LocalFileStorage(storageDir))
            storage.add(packageId, scanResult)

            val createResult = HelperMain().test(
                "package-configuration",
                "create",
                "--config",
                ortConfigFile.absolutePath,
                "--scan-results-storage-dir",
                storageDir.absolutePath,
                "--package-id",
                packageId.toCoordinates(),
                "--output-dir",
                outputDir.absolutePath,
                "--generate-path-excludes"
            )

            createResult.statusCode shouldBe 0

            val packageConfiguration = outputDir.resolve("vcs.yml").readValue<PackageConfiguration>()
            val excludePatterns = packageConfiguration.pathExcludes.map { it.pattern }
            excludePatterns should
                containExactly("module/src/test/**")
        }
    }
})

private val PACKAGE_CURATION = PackageCuration(
    id = Identifier("Maven:example:package:1.0"),
    data = PackageCurationData(
        comment = "Example curation.",
        vcs = VcsInfoCurationData(
            path = "module"
        )
    )
)

private fun TestConfiguration.createOrtConfig(): File {
    val packageCurationsFile = tempfile(prefix = "curations", suffix = ".yml").apply {
        writeText(listOf(PACKAGE_CURATION).toYaml())
    }

    val config = OrtConfiguration().copy(
        packageCurationProviders = listOf(
            ProviderPluginConfiguration(
                type = "File",
                options = mapOf(
                    "path" to packageCurationsFile.absolutePath,
                    "mustExist" to "true"
                )
            )
        )
    )

    val ortConfigFile = tempfile(prefix = "config", suffix = ".yml").apply {
        writeText(mapOf("ort" to config).toYaml())
    }

    return ortConfigFile
}
