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

package org.ossreviewtoolkit.plugins.packagemanagers.ortproject

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.module.kotlin.readValue

import java.io.File

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.mapper
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor

@OrtPlugin(
    displayName = "OrtProjectFile",
    description = "The package manager that uses ORT-specific BOM file as package list source.",
    factory = PackageManagerFactory::class
)
class OrtProjectFile(override val descriptor: PluginDescriptor = OrtProjectFileFactory.descriptor) :
    PackageManager("OrtProjectFile") {

    override val globsForDefinitionFiles = listOf("ort.project.yml", "ort.project.yaml", "ort.project.json")

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        var parsedProject: OrtProjectFileDto

        try {
            parsedProject = definitionFile.mapper().copy().readValue<OrtProjectFileDto>(definitionFile)
        } catch (ex: JsonProcessingException) {
            return listOf(
                ProjectAnalyzerResult(
                    project = Project.EMPTY,
                    packages = emptySet(),
                    issues = listOf(Issue(source = "OrtProjectFile", message = ex.message.toString()))
                )
            )
        }

        val project = OrtProjectFileMapper.extractAndMapProject(
            parsedProject,
            processProjectVcs(definitionFile.parentFile)
        )

        val packagesWithIssues = OrtProjectFileMapper.extractAndMapPackages(parsedProject)
        val res = ProjectAnalyzerResult(
            project = project,
            packages = packagesWithIssues.first,
            issues = packagesWithIssues.second
        )

        return listOf(res)
    }

    override fun mapDefinitionFiles(
        analysisRoot: File,
        definitionFiles: List<File>,
        analyzerConfig: AnalyzerConfiguration
    ): List<File> {
        return analysisRoot.walk().filter { isProjectFileName(it) }.toList()
    }

    private fun isProjectFileName(fileName: File): Boolean {
        if (!fileName.isFile || fileName.isDirectory) {
            return false
        }

        globsForDefinitionFiles.forEach {
            if (fileName.name.endsWith(it)) {
                return true
            }
        }

        return false
    }
}
