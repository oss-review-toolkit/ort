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

package org.ossreviewtoolkit.plugins.packagemanagers.python

import java.io.File

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.div

import org.semver4j.Semver
import org.semver4j.range.RangeList
import org.semver4j.range.RangeListFactory

/**
 * The version that introduced the requirements command.
 */
private val REQUIREMENTS_COMMAND_VERSION = Semver("2022.4.8")

internal object PipenvCommand : CommandLineTool {
    override fun command(workingDir: File?) = "pipenv"

    override fun transformVersion(output: String) =
        // The version string can be something like:
        // pipenv, version 2018.11.26
        output.removePrefix("pipenv, version ")

    override fun getVersionRequirement(): RangeList = RangeListFactory.create("[2018.10.9,)")
}

@OrtPlugin(
    displayName = "Pipenv",
    description = "The Pipenv package manager for Python.",
    factory = PackageManagerFactory::class
)
class Pipenv(
    override val descriptor: PluginDescriptor = PipenvFactory.descriptor, private val config: PipConfig
) : PackageManager("Pipenv") {
    // Usually, definition files should not contain (only) lockfiles, to also support the case when no lockfile is
    // present. However, there currently is no way to distinguish a Pipenv project from a vanilla Pip project without
    // looking at the lockfile.
    override val globsForDefinitionFiles = listOf("Pipfile.lock")

    override fun beforeResolution(
        analysisRoot: File,
        definitionFiles: List<File>,
        analyzerConfig: AnalyzerConfiguration
    ) = PipenvCommand.checkVersion()

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        // For an overview, dependency resolution involves the following steps:
        // 1. Generate "requirements.txt" file with `pipenv` command
        // 2. Use existing "Pip" PackageManager to do the actual dependency resolution

        val workingDir = definitionFile.parentFile
        val requirementsFile = workingDir / "requirements-from-pipenv.txt"

        logger.info { "Generating '${requirementsFile.name}' file in '$workingDir' directory..." }

        val requirements = if (Semver(PipenvCommand.getVersion()) >= REQUIREMENTS_COMMAND_VERSION) {
            PipenvCommand.run(workingDir, "requirements")
        } else {
            PipenvCommand.run(workingDir, "lock", "--requirements")
        }.requireSuccess().stdout

        requirementsFile.writeText(requirements)

        return Pip(config = config, projectType = projectType)
            .resolveDependencies(analysisRoot, requirementsFile, excludes, analyzerConfig, labels)
            .also { requirementsFile.delete() }
    }
}
