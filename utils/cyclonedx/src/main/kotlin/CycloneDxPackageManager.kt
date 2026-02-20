/*
 * Copyright (C) 2025 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.utils.cyclonedx

import java.io.File

import org.cyclonedx.model.Component
import org.cyclonedx.parsers.JsonParser

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.Includes
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.model.utils.isScopeIncluded

/**
 * Base class for package managers that analyze CycloneDX SBOMs.
 *
 * This provides common functionality for parsing CycloneDX SBOMs and converting them
 * into ORT's internal data structures. Subclasses only need to specify the project type
 * and definition file patterns.
 */
abstract class CycloneDxPackageManager(
    projectType: String
) : PackageManager(projectType) {
    private val dependencyHandler = CycloneDxDependencyHandler()
    private val graphBuilder = DependencyGraphBuilder(dependencyHandler)

    protected fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        bom: ByteArray,
        excludes: Excludes,
        includes: Includes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        val issues = mutableListOf<Issue>()

        val parsedBom = JsonParser().parse(bom)

        val rootComponent = requireNotNull(parsedBom.metadata?.component) {
            "CycloneDX SBOM must contain metadata.component."
        }

        val project = createProjectFromSbom(definitionFile, rootComponent, issues)
        dependencyHandler.registerProject(project.id, parsedBom)

        dependencyHandler.dependenciesFor(rootComponent)
            .groupBy { it.scope?.name?.lowercase() ?: "required" }
            .toSortedMap()
            .filter { (scope, _) -> isScopeIncluded(scope, excludes, includes) }
            .forEach { (scope, components) -> graphBuilder.addDependencies(project.id, scope, components) }

        return listOf(
            ProjectAnalyzerResult(
                project = project.copy(scopeNames = graphBuilder.scopesFor(project.id)),
                packages = emptySet(),
                issues = issues
            )
        )
    }

    override fun createPackageManagerResult(projectResults: Map<File, List<ProjectAnalyzerResult>>) =
        PackageManagerResult(projectResults, graphBuilder.build(), graphBuilder.packages())

    /**
     * Create a Project from the CycloneDX metadata component.
     */
    private fun createProjectFromSbom(definitionFile: File, component: Component, issues: MutableList<Issue>): Project {
        val definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path
        val fallbackName = definitionFile.parentFile.name.takeIf { it.isNotBlank() }
            ?: definitionFile.nameWithoutExtension

        if (component.name.isNullOrBlank()) {
            issues += Issue(
                source = projectType,
                message = "CycloneDX SBOM metadata.component does not contain a 'name'. " +
                    "Using fallback project name from directory.",
                severity = Severity.WARNING
            )
        }

        val project = component.toProject(definitionFilePath, projectType)

        val correctedId = if (project.id.name.isBlank()) {
            project.id.copy(type = projectType, name = fallbackName)
        } else {
            project.id.copy(type = projectType)
        }

        return project.copy(
            id = correctedId,
            vcsProcessed = processProjectVcs(definitionFile.parentFile, project.vcs)
        )
    }
}
