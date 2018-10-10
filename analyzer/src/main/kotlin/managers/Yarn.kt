/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.utils.OS

import com.vdurmont.semver4j.Requirement

import java.io.File

val YARN_LOCK_FILES = listOf("yarn.lock")

/**
 * The Yarn package manager for JavaScript, see https://www.yarnpkg.com/.
 */
class Yarn(analyzerConfig: AnalyzerConfiguration, repoConfig: RepositoryConfiguration) :
        NPM(analyzerConfig, repoConfig) {
    class Factory : AbstractPackageManagerFactory<Yarn>() {
        override val globsForDefinitionFiles = listOf("package.json")

        override fun create(analyzerConfig: AnalyzerConfiguration, repoConfig: RepositoryConfiguration) =
                Yarn(analyzerConfig, repoConfig)
    }

    override val recognizedLockFiles = YARN_LOCK_FILES

    override fun command(workingDir: File?) = if (OS.isWindows) "yarn.cmd" else "yarn"

    override fun getVersionRequirement(): Requirement = Requirement.buildNPM("1.3.* - 1.9.*")

    override fun prepareResolution(definitionFiles: List<File>): List<File> {
        // Only keep those definition files that are accompanied by a Yarn lock file.
        val yarnDefinitionFiles = definitionFiles.filter { definitionFile ->
            val existingYarnLockFiles = recognizedLockFiles.mapNotNull { lockFileName ->
                definitionFile.resolveSibling(lockFileName).takeIf { it.isFile }
            }

            existingYarnLockFiles.isNotEmpty()
        }

        if (yarnDefinitionFiles.isNotEmpty()) {
            // We do not actually depend on any features specific to a Yarn version, but we still want to stick to a
            // fixed minor version to be sure to get consistent results.
            checkVersion(ignoreActualVersion = analyzerConfig.ignoreToolVersions)
        }

        return yarnDefinitionFiles
    }
}
