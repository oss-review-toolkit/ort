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

package org.ossreviewtoolkit.plugins.reporters.evaluatedmodel

import java.io.File

import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterFactory
import org.ossreviewtoolkit.reporter.ReporterInput

data class EvaluatedModelReporterConfig(
    /**
     * Controls whether subtrees occurring multiple times in the dependency tree are stripped.
     */
    @OrtPluginOption(
        defaultValue = "false"
    )
    val deduplicateDependencyTree: Boolean,

    /**
     * The list of file formats to generate, defaults to JSON. Supported formats are JSON and YAML.
     */
    @OrtPluginOption(
        defaultValue = "JSON",
        aliases = ["output.file.formats"]
    )
    val outputFileFormats: List<String>
)

/**
 * A [Reporter] that generates an [EvaluatedModel].
 */
@OrtPlugin(
    displayName = "Evaluated Model",
    description = "Generates an evaluated model of the ORT result.",
    factory = ReporterFactory::class
)
class EvaluatedModelReporter(
    override val descriptor: PluginDescriptor = EvaluatedModelReporterFactory.descriptor,
    private val config: EvaluatedModelReporterConfig
) : Reporter {
    override fun generateReport(input: ReporterInput, outputDir: File): List<Result<File>> {
        val evaluatedModel = EvaluatedModel.create(input, config.deduplicateDependencyTree)

        val outputFileFormats = config.outputFileFormats.map { FileFormat.forExtension(it) }

        return outputFileFormats.map { fileFormat ->
            runCatching {
                outputDir.resolve("evaluated-model.${fileFormat.fileExtension}").apply {
                    bufferedWriter().use {
                        when (fileFormat) {
                            FileFormat.JSON -> evaluatedModel.toJson(it)
                            FileFormat.YAML -> evaluatedModel.toYaml(it)
                        }
                    }
                }
            }
        }
    }
}
