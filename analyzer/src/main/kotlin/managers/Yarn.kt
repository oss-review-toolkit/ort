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

import com.fasterxml.jackson.databind.JsonNode

import com.vdurmont.semver4j.Requirement

import java.io.File

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.managers.utils.hasYarnLockFile
import org.ossreviewtoolkit.analyzer.managers.utils.mapDefinitionFilesForYarn
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.utils.common.Os

/**
 * The [Yarn](https://classic.yarnpkg.com/) package manager for JavaScript.
 */
class Yarn(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : Npm(name, analysisRoot, analyzerConfig, repoConfig) {
    class Factory : AbstractPackageManagerFactory<Yarn>("Yarn") {
        override val globsForDefinitionFiles = listOf("package.json")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Yarn(managerName, analysisRoot, analyzerConfig, repoConfig)
    }

    override fun hasLockFile(projectDir: File) = hasYarnLockFile(projectDir)

    override fun command(workingDir: File?) = if (Os.isWindows) "yarn.cmd" else "yarn"

    override fun getVersionRequirement(): Requirement = Requirement.buildNPM("1.3.* - 1.22.*")

    override fun mapDefinitionFiles(definitionFiles: List<File>) = mapDefinitionFilesForYarn(definitionFiles).toList()

    override fun beforeResolution(definitionFiles: List<File>) =
        // We do not actually depend on any features specific to a Yarn version, but we still want to stick to a
        // fixed minor version to be sure to get consistent results.
        checkVersion()

    override fun runInstall(workingDir: File) = run(workingDir, "install", "--ignore-scripts", "--ignore-engines")

    override fun getRemotePackageDetails(workingDir: File, packageName: String): JsonNode {
        val process = run(workingDir, "info", "--json", packageName)
        return jsonMapper.readTree(process.stdoutFile)["data"]
    }
}
