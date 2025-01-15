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
import org.ossreviewtoolkit.analyzer.parseAuthorString
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.utils.common.realFile

abstract class NodePackageManager(
    managerName: String,
    val managerType: NodePackageManagerType,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(managerName, managerType.projectType, analysisRoot, analyzerConfig, repoConfig) {
    protected abstract val graphBuilder: DependencyGraphBuilder<*>

    protected fun parseProject(packageJsonFile: File, analysisRoot: File): Project {
        logger.debug { "Parsing project info from '$packageJsonFile'." }

        val packageJson = parsePackageJson(packageJsonFile)

        val rawName = packageJson.name.orEmpty()
        val (namespace, name) = splitNamespaceAndName(rawName)

        val projectName = name.ifBlank {
            getFallbackProjectName(analysisRoot, packageJsonFile).also {
                logger.warn { "'$packageJsonFile' does not define a name, falling back to '$it'." }
            }
        }

        val version = packageJson.version.orEmpty()
        if (version.isBlank()) {
            logger.warn { "'$packageJsonFile' does not define a version." }
        }

        val declaredLicenses = packageJson.licenses.mapLicenses()
        val authors = packageJson.authors.flatMap { parseAuthorString(it.name) }
            .mapNotNullTo(mutableSetOf()) { it.name }
        val description = packageJson.description.orEmpty()
        val homepageUrl = packageJson.homepage.orEmpty()
        val projectDir = packageJsonFile.parentFile.realFile()
        val vcsFromPackage = parseVcsInfo(packageJson)

        return Project(
            id = Identifier(
                type = projectType,
                namespace = namespace,
                name = projectName,
                version = version
            ),
            definitionFilePath = VersionControlSystem.getPathInfo(packageJsonFile.realFile()).path,
            authors = authors,
            declaredLicenses = declaredLicenses,
            vcs = vcsFromPackage,
            vcsProcessed = processProjectVcs(projectDir, vcsFromPackage, homepageUrl),
            description = description,
            homepageUrl = homepageUrl
        )
    }

    override fun mapDefinitionFiles(definitionFiles: List<File>) =
        NodePackageManagerDetection(definitionFiles).filterApplicable(managerType)

    override fun createPackageManagerResult(projectResults: Map<File, List<ProjectAnalyzerResult>>) =
        PackageManagerResult(projectResults, graphBuilder.build(), graphBuilder.packages())
}
