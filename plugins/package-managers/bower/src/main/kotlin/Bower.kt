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

package org.ossreviewtoolkit.plugins.packagemanagers.bower

import java.io.File
import java.util.LinkedList

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.stashDirectories

import org.semver4j.RangesList
import org.semver4j.RangesListFactory

internal object BowerCommand : CommandLineTool {
    override fun command(workingDir: File?) = if (Os.isWindows) "bower.cmd" else "bower"

    override fun getVersionRequirement(): RangesList = RangesListFactory.create(">=1.8.8")
}

/**
 * The [Bower](https://bower.io/) package manager for JavaScript.
 */
class Bower(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, "BowerProject", analysisRoot, analyzerConfig, repoConfig) {
    class Factory : AbstractPackageManagerFactory<Bower>("Bower") {
        override val globsForDefinitionFiles = listOf("bower.json")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Bower(type, analysisRoot, analyzerConfig, repoConfig)
    }

    private val graphBuilder = DependencyGraphBuilder(BowerDependencyHandler())

    override fun beforeResolution(definitionFiles: List<File>) = BowerCommand.checkVersion()

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile

        stashDirectories(workingDir.resolve("bower_components")).use { _ ->
            val projectPackageInfo = getProjectPackageInfo(workingDir)
            val packageInfoForName = projectPackageInfo
                .getTransitiveDependencies()
                .associateBy { checkNotNull(it.pkgMeta.name) }

            SCOPE_NAMES.forEach { scopeName ->
                val dependencies = projectPackageInfo.getScopeDependencies(scopeName).map { dependencyName ->
                    // Bower leaves out a dependency entry for a child if there exists a similar entry to its parent
                    // entry with the exact same name and resolved target. This makes it necessary to retrieve the
                    // information about the subtree rooted at the parent from that other entry containing the full
                    // dependency information.
                    // See https://github.com/bower/bower/blob/6bc778d/lib/core/Manager.js#L557 and below.
                    projectPackageInfo.dependencies[dependencyName] ?: packageInfoForName.getValue(dependencyName)
                }

                graphBuilder.addDependencies(projectPackageInfo.toIdentifier(), scopeName, dependencies)
            }

            val project = projectPackageInfo.toProject(definitionFile, projectType, SCOPE_NAMES)
            return listOf(ProjectAnalyzerResult(project, emptySet()))
        }
    }

    override fun createPackageManagerResult(projectResults: Map<File, List<ProjectAnalyzerResult>>) =
        PackageManagerResult(projectResults, graphBuilder.build(), graphBuilder.packages())

    private fun getProjectPackageInfo(workingDir: File): PackageInfo {
        BowerCommand.run(workingDir, "--allow-root", "install").requireSuccess()
        val json = BowerCommand.run(workingDir, "--allow-root", "list", "--json").requireSuccess().stdout
        return parsePackageInfoJson(json)
    }
}

private const val SCOPE_NAME_DEPENDENCIES = "dependencies"
private const val SCOPE_NAME_DEV_DEPENDENCIES = "devDependencies"
private val SCOPE_NAMES = setOf(SCOPE_NAME_DEPENDENCIES, SCOPE_NAME_DEV_DEPENDENCIES)

private fun PackageInfo.getScopeDependencies(scopeName: String): Set<String> =
    when (scopeName) {
        SCOPE_NAME_DEPENDENCIES -> pkgMeta.dependencies.keys
        SCOPE_NAME_DEV_DEPENDENCIES -> pkgMeta.devDependencies.keys
        else -> error("Invalid scope name: '$scopeName'.")
    }

private fun PackageInfo.getTransitiveDependencies(): List<PackageInfo> {
    val result = LinkedList<PackageInfo>()
    val queue = LinkedList(dependencies.values)

    while (queue.isNotEmpty()) {
        val info = queue.removeFirst()
        result += info
        queue += info.dependencies.values
    }

    return result
}
