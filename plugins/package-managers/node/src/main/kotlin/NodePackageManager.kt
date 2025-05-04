/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.analyzer.determineEnabledPackageManagers
import org.ossreviewtoolkit.analyzer.parseAuthorString
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.utils.common.realFile

abstract class NodePackageManager(val managerType: NodePackageManagerType) : PackageManager(managerType.projectType) {
    internal abstract val graphBuilder: DependencyGraphBuilder<*>

    internal fun parseProject(packageJsonFile: File, analysisRoot: File): Project {
        logger.debug { "Parsing project info from '$packageJsonFile'." }

        val packageJson = parsePackageJson(packageJsonFile)

        val (namespace, name) = splitNamespaceAndName(packageJson.name.orEmpty())

        val projectName = name.ifBlank {
            getFallbackProjectName(analysisRoot, packageJsonFile).also {
                logger.warn { "'$packageJsonFile' does not define a name, falling back to '$it'." }
            }
        }

        val vcs = parseVcsInfo(packageJson)

        return Project(
            id = Identifier(
                type = projectType,
                namespace = namespace,
                name = projectName,
                version = packageJson.version.orEmpty()
            ),
            definitionFilePath = VersionControlSystem.getPathInfo(packageJsonFile.realFile).path,
            authors = packageJson.authors.flatMap {
                parseAuthorString(it.name)
            }.mapNotNullTo(mutableSetOf()) {
                it.name
            },
            declaredLicenses = packageJson.licenses.mapLicenses(),
            vcs = vcs,
            vcsProcessed = processProjectVcs(packageJsonFile.parentFile.realFile, vcs, packageJson.homepage.orEmpty()),
            description = packageJson.description.orEmpty(),
            homepageUrl = packageJson.homepage.orEmpty()
        )
    }

    override fun mapDefinitionFiles(
        analysisRoot: File,
        definitionFiles: List<File>,
        analyzerConfig: AnalyzerConfiguration
    ): List<File> {
        val enabledIds = analyzerConfig.determineEnabledPackageManagers().map { it.descriptor.id.uppercase() }

        // Only keep those types for which a package manager is enabled.
        val enabledTypes = NodePackageManagerType.entries.filter { it.name in enabledIds }

        // Assume the first type to be the best candidate for the fallback.
        val fallbackType = enabledTypes.first()

        return NodePackageManagerDetection(definitionFiles).filterApplicable(managerType, fallbackType)
    }

    override fun createPackageManagerResult(projectResults: Map<File, List<ProjectAnalyzerResult>>) =
        PackageManagerResult(projectResults, graphBuilder.build(), graphBuilder.packages())
}
