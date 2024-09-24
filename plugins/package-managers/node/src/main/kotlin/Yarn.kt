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

package org.ossreviewtoolkit.plugins.packagemanagers.node

import java.io.File

import kotlin.time.Duration.Companion.days

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.NodePackageManager
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.NpmDetection
import org.ossreviewtoolkit.utils.common.DiskCache
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.mebibytes
import org.ossreviewtoolkit.utils.ort.ortDataDirectory

import org.semver4j.RangesList
import org.semver4j.RangesListFactory

private val yarnInfoCache = DiskCache(
    directory = ortDataDirectory.resolve("cache/analyzer/yarn/info"),
    maxCacheSizeInBytes = 100.mebibytes,
    maxCacheEntryAgeInSeconds = 7.days.inWholeSeconds
)

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
        ) = Yarn(type, analysisRoot, analyzerConfig, repoConfig)
    }

    override fun hasLockfile(projectDir: File) = NodePackageManager.YARN.hasLockfile(projectDir)

    override fun command(workingDir: File?) = if (Os.isWindows) "yarn.cmd" else "yarn"

    override fun getVersionRequirement(): RangesList = RangesListFactory.create("1.3.* - 1.22.*")

    override fun mapDefinitionFiles(definitionFiles: List<File>) =
        NpmDetection(definitionFiles).filterApplicable(NodePackageManager.YARN)

    override fun beforeResolution(definitionFiles: List<File>) =
        // We do not actually depend on any features specific to a Yarn version, but we still want to stick to a
        // fixed minor version to be sure to get consistent results.
        checkVersion()

    override fun runInstall(workingDir: File) =
        run(workingDir, "install", "--ignore-scripts", "--ignore-engines", "--immutable")

    override fun getRemotePackageDetails(workingDir: File, packageName: String): PackageJson {
        yarnInfoCache.read(packageName)?.let { return parsePackageJson(it) }

        val process = run(workingDir, "info", "--json", packageName)

        val data = Json.parseToJsonElement(process.stdout).jsonObject["data"]?.also {
            yarnInfoCache.write(packageName, it.toString())
        } ?: checkNotNull(Json.parseToJsonElement(process.stderr).jsonObject["data"]).also {
            logger.warn { "Error running '${process.commandLine}' in directory $workingDir: $it" }
        }

        return parsePackageJson(data)
    }
}
