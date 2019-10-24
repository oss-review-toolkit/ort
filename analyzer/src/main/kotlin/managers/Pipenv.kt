/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.analyzer.managers

import com.here.ort.analyzer.AbstractPackageManagerFactory
import com.here.ort.analyzer.PackageManager
import com.here.ort.model.ProjectAnalyzerResult
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.utils.CommandLineTool
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.log

import com.vdurmont.semver4j.Requirement

import java.io.File

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

    override fun getVersionRequirement(): Requirement = Requirement.buildIvy("[2018.10.9,)")

    override fun getVersion(versionArguments: String, workingDir: File?, transform: (String) -> String) =
        super.getVersion(versionArguments, workingDir) {
            // "pipenv --version" returns a string like "pipenv, version 2018.11.26", so simply remove the prefix.
            it.substringAfter("pipenv, version ")
        }

    override fun beforeResolution(definitionFiles: List<File>) =
        checkVersion(ignoreActualVersion = analyzerConfig.ignoreToolVersions)

    override fun resolveDependencies(definitionFile: File): ProjectAnalyzerResult? {
        // For an overview, dependency resolution involves the following steps:
        // 1. Generate "requirements.txt" file with `pipenv` command
        // 2. Use existing "Pip" PackageManager to do the actual dependency resolution

        val workingDir = definitionFile.parentFile
        val requirementsFile = File(workingDir, "requirements-from-pipenv.txt").apply { deleteOnExit() }

        log.info { "Generating '${requirementsFile.name}' file in '$workingDir' directory..." }

        ProcessCapture(workingDir, command(), "lock", "--requirements")
            .requireSuccess()
            .stdoutFile
            .copyTo(requirementsFile)

        return Pip(managerName, analysisRoot, analyzerConfig, repoConfig).resolveDependencies(requirementsFile)
    }
}
