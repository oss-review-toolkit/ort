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

package org.ossreviewtoolkit.plugins.packagemanagers.ortbom

import com.fasterxml.jackson.module.kotlin.readValue

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.mapper
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor

import java.io.File
import kotlin.collections.mapTo



@OrtPlugin(
    displayName = "OrtBomFile",
    description = "The package manager that uses ORT-specific BOM file as package list source.",
    factory = PackageManagerFactory::class
)
class OrtBomFile(override val descriptor: PluginDescriptor = OrtBomFileFactory.descriptor) :
    PackageManager("OrtBomFile") {
    override val globsForDefinitionFiles = listOf("ort-bom.yml", "ort-bom.yaml", "ort-bom.json")

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        val parsedProject = definitionFile.mapper().copy().readValue<OrtBomProjectDto>(definitionFile)
        val res = ProjectAnalyzerResult(
            project = parsedProject.extractProject(),
            packages = parsedProject.extractPackages(),
            issues = emptyList()
        )

        return listOf(res)
    }

    override fun mapDefinitionFiles(
        analysisRoot: File,
        definitionFiles: List<File>,
        analyzerConfig: AnalyzerConfiguration
    ): List<File> {
        require(definitionFiles.isNotEmpty()) {
            "Definition files must contain at least one definition file."
        }
        require(definitionFiles.size == 1) {
            "Only one definition file is allowed."
        }
        return definitionFiles
    }
}
