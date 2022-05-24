/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.analyzer.managers

import com.vdurmont.semver4j.Requirement

import java.io.File

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.ort.log

class Pipenv(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig), CommandLineTool {
    class Factory : AbstractPackageManagerFactory<Pipenv>("Pipenv") {
        override val globsForDefinitionFiles = listOf("Pipfile.lock")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Pipenv(managerName, analysisRoot, analyzerConfig, repoConfig)
    }

    override fun command(workingDir: File?) = "pipenv"

    override fun transformVersion(output: String) =
        // The version string can be something like:
        // pipenv, version 2018.11.26
        output.removePrefix("pipenv, version ")

    override fun getVersionRequirement(): Requirement = Requirement.buildIvy("[2018.10.9,)")

    override fun beforeResolution(definitionFiles: List<File>) = checkVersion()

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        // For an overview, dependency resolution involves the following steps:
        // 1. Generate "requirements.txt" file with `pipenv` command
        // 2. Use existing "Pip" PackageManager to do the actual dependency resolution

        val workingDir = definitionFile.parentFile
        val requirementsFile = workingDir.resolve("requirements-from-pipenv.txt")

        log.info { "Generating '${requirementsFile.name}' file in '$workingDir' directory..." }

        val requirements = ProcessCapture(workingDir, command(), "lock", "--requirements").requireSuccess().stdout
        requirementsFile.writeText(requirements)

        return Pip(managerName, analysisRoot, analyzerConfig, repoConfig)
            .resolveDependencies(requirementsFile, labels)
            .also { requirementsFile.delete() }
    }
}
