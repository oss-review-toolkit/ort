/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.ProcessCapture

/**
 * [Poetry](https://python-poetry.org/) package manager for Python.
 */
class Poetry(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig), CommandLineTool {
    private companion object : Logging

    class Factory : AbstractPackageManagerFactory<Poetry>("Poetry") {
        override val globsForDefinitionFiles = listOf("poetry.lock")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Poetry(type, analysisRoot, analyzerConfig, repoConfig)
    }

    override fun command(workingDir: File?) = "poetry"

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        // For an overview, dependency resolution involves the following steps:
        // 1. Generate "requirements.txt" file with `poetry` command.
        // 2. Use existing "Pip" PackageManager to do the actual dependency resolution.

        val workingDir = definitionFile.parentFile
        val requirementsFile = workingDir.resolve("requirements-from-poetry.txt")

        logger.info { "Generating '${requirementsFile.name}' file in '$workingDir' directory..." }

        val req = ProcessCapture(workingDir, command(), "export", "--without-hashes", "--format=requirements.txt")
            .requireSuccess().stdout
        requirementsFile.writeText(req)

        return Pip(managerName, analysisRoot, analyzerConfig, repoConfig)
            .resolveDependencies(requirementsFile, labels)
            .also { requirementsFile.delete() }
    }
}
