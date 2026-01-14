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

package org.ossreviewtoolkit.plugins.packagemanagers.rebar3

import java.io.File

import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.SourceCodeOrigin
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.Includes
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.cyclonedx.CycloneDxPackageManager
import org.ossreviewtoolkit.utils.ort.createOrtTempFile

private const val PROJECT_TYPE = "otp"

@OrtPlugin(
    displayName = "Rebar3",
    description = "The Rebar3 build tool for Erlang, using bombom for SBOM generation.",
    factory = PackageManagerFactory::class
)
class Rebar3(
    override val descriptor: PluginDescriptor = Rebar3Factory.descriptor
) : CycloneDxPackageManager(PROJECT_TYPE) {
    override val globsForDefinitionFiles = listOf("rebar.config")

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        includes: Includes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile

        requireLockfile(analysisRoot, workingDir, analyzerConfig.allowDynamicVersions) {
            (workingDir / "rebar.lock").isFile
        }

        // bombom doesn't support stdout output, so use a temp file.
        val tempFile = createOrtTempFile("bombom", ".json")

        return try {
            // Use --force because createTempFile creates an empty file and bombom prompts for overwrite confirmation.
            BombomCommand.run(workingDir, "--output", tempFile.absolutePath, "--format", "json", "--force")
                .requireSuccess()

            resolveDependencies(
                analysisRoot,
                definitionFile,
                tempFile.readBytes(),
                excludes,
                includes,
                analyzerConfig,
                labels
            )
        } finally {
            tempFile.delete()
        }
    }

    override fun createPackageManagerResult(
        projectResults: Map<File, List<ProjectAnalyzerResult>>
    ): PackageManagerResult {
        val result = super.createPackageManagerResult(projectResults)

        val packagesWithSourceCodeOrigins = result.sharedPackages.mapTo(mutableSetOf()) { pkg ->
            if (pkg.id.type == "hex") {
                pkg.copy(sourceCodeOrigins = listOf(SourceCodeOrigin.ARTIFACT, SourceCodeOrigin.VCS))
            } else {
                pkg
            }
        }

        return result.copy(sharedPackages = packagesWithSourceCodeOrigins)
    }
}
