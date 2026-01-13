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

package org.ossreviewtoolkit.plugins.packagemanagers.mix

import java.io.File

import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.SourceCodeOrigin
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.Includes
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.cyclonedx.CycloneDxPackageManager

private const val PROJECT_TYPE = "otp"

data class MixConfig(
    /** Whether to include OTP system dependencies (elixir, kernel, logger, stdlib) in the analysis. */
    @OrtPluginOption(defaultValue = "false")
    val includeSystemDependencies: Boolean
)

@OrtPlugin(
    displayName = "Mix",
    description = "The Mix package manager for Elixir, using mix_sbom for SBOM generation.",
    factory = PackageManagerFactory::class
)
class Mix(
    override val descriptor: PluginDescriptor = MixFactory.descriptor,
    private val config: MixConfig
) : CycloneDxPackageManager(PROJECT_TYPE) {
    override val globsForDefinitionFiles = listOf("mix.exs")

    override fun mapDefinitionFiles(
        analysisRoot: File,
        definitionFiles: List<File>,
        analyzerConfig: AnalyzerConfiguration
    ): List<File> =
        // Sort by path depth descending so children are analyzed before parents.
        // This ensures child project IDs are registered before parent dependencies are resolved.
        definitionFiles.sortedByDescending { it.canonicalPath.count { c -> c == File.separatorChar } }

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
            (workingDir / "mix.lock").isFile
        }

        val args = buildList {
            add("cyclonedx")
            add("--format")
            add("json")
            add("--output")
            add("-")
            if (!config.includeSystemDependencies) add("--exclude-system-dependencies")
        }

        val result = MixSbomCommand.run(workingDir, *args.toTypedArray()).requireSuccess()

        return resolveDependencies(
            analysisRoot,
            definitionFile,
            result.stdout.toByteArray(),
            excludes,
            includes,
            analyzerConfig,
            labels
        )
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
