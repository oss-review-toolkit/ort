/*
 * Copyright (C) 2019 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.node

import java.io.File

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.NodePackageManager
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.NpmDetection
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.realFile

import org.semver4j.RangesList
import org.semver4j.RangesListFactory

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
        ) = Pnpm(type, analysisRoot, analyzerConfig, repoConfig)
    }

    override fun hasLockfile(projectDir: File) = NodePackageManager.PNPM.hasLockfile(projectDir)

    override fun File.isWorkspaceDir() = realFile() in findWorkspaceSubmodules(analysisRoot)

    override fun loadWorkspaceSubmodules(moduleDir: File): List<File> {
        val process = run(moduleDir, "list", "--recursive", "--depth=-1", "--parseable")

        return process.stdout.lines().filter { it.isNotEmpty() }.map { File(it) }
    }

    override fun command(workingDir: File?) = if (Os.isWindows) "pnpm.cmd" else "pnpm"

    override fun getVersionRequirement(): RangesList = RangesListFactory.create("5.* - 9.*")

    override fun mapDefinitionFiles(definitionFiles: List<File>) =
        NpmDetection(definitionFiles).filterApplicable(NodePackageManager.PNPM)

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
