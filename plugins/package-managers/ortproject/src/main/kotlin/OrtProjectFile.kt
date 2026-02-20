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

import com.charleskorn.kaml.Yaml

import java.io.File

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.Includes
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor

@OrtPlugin(
    displayName = "OrtProjectFile",
    description = "A package manager that uses an ORT-specific file format as package list source.",
    factory = PackageManagerFactory::class
)
class OrtProjectFile(override val descriptor: PluginDescriptor = OrtProjectFileFactory.descriptor) :
    PackageManager("OrtProjectFile") {
    override val globsForDefinitionFiles = listOf(
        "ortproject.yml",
        "ortproject.yaml",
        "ortproject.json",
        "*.ortproject.yml",
        "*.ortproject.yaml",
        "*.ortproject.json"
    )

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        includes: Includes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        val parsedProject: OrtProjectFileDto = try {
            when (definitionFile.extension) {
                "json" -> Json.decodeFromString<OrtProjectFileDto>(definitionFile.readText())
                "yml", "yaml" -> Yaml.default.decodeFromString<OrtProjectFileDto>(definitionFile.readText())
                else -> error("Unknown file format for file '${definitionFile.absolutePath}'")
            }
        } catch (ex: SerializationException) {
            logger.error(
                "Could not parse the ORT project file at '${definitionFile.absolutePath}': ${ex.message}"
            )

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
            processProjectVcs(definitionFile.parentFile),
            definitionFile
        )

        val packagesWithIssues = OrtProjectFileMapper.extractAndMapPackages(parsedProject)
        val res = ProjectAnalyzerResult(
            project = project,
            packages = packagesWithIssues.first,
            issues = packagesWithIssues.second
        )

        return listOf(res)
    }
}
