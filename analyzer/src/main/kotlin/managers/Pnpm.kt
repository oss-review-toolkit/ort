/*
 * Copyright (C) 2019-2020 HERE Europe B.V.
 * Copyright (C) 2022 Bosch.IO GmbH
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
import org.ossreviewtoolkit.analyzer.managers.utils.hasPnpmLockFile
import org.ossreviewtoolkit.analyzer.managers.utils.mapDefinitionFilesForPnpm
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.realFile

/**
 * The [fast, disk space efficient package manager](https://pnpm.io/).
 */
class Pnpm(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : Npm(name, analysisRoot, analyzerConfig, repoConfig) {
    class Factory : AbstractPackageManagerFactory<Pnpm>("PNPM") {
        override val globsForDefinitionFiles = listOf("package.json", "pnpm-lock.yaml")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Pnpm(managerName, analysisRoot, analyzerConfig, repoConfig)
    }

    /**
     * PNPM symlinks workspace modules in the `node_modules` directory, which will result in cyclic symlinks of
     * `node_module` directories. Limit the search depth to '2' in this case as all packages are in a direct
     * subdirectory of the `node_modules` directory, thanks to the `--shamefully-hoist` install option.
     */
    override val modulesSearchDepth = 2

    override fun hasLockFile(projectDir: File) = hasPnpmLockFile(projectDir)

    override fun File.isWorkspaceDir() = realFile() in findWorkspaceSubmodules(analysisRoot)

    override fun loadWorkspaceSubmodules(moduleDir: File): List<File> {
        val process = run(moduleDir, "list", "--recursive", "--depth=-1", "--parseable")

        return process.stdout.lines().filter { it.isNotEmpty() }.map { File(it) }
    }

    override fun command(workingDir: File?) = if (Os.isWindows) "pnpm.cmd" else "pnpm"

    override fun getVersionRequirement(): Requirement = Requirement.buildNPM("5.* - 7.*")

    override fun mapDefinitionFiles(definitionFiles: List<File>) = mapDefinitionFilesForPnpm(definitionFiles).toList()

    override fun runInstall(workingDir: File) =
        run(
            "install",
            "--ignore-pnpmfile",
            "--ignore-scripts",
            "--frozen-lockfile", // Use the existing lockfile instead of updating an outdated one.
            "--shamefully-hoist", // Build a similar node_modules structure as NPM and Yarn does.
            workingDir = workingDir
        )

    override fun beforeResolution(definitionFiles: List<File>) =
        // We do not actually depend on any features specific to a PNPM version, but we still want to stick to a
        // fixed major version to be sure to get consistent results.
        checkVersion()
}
