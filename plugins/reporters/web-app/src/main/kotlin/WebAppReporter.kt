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

package org.ossreviewtoolkit.plugins.reporters.webapp

import java.io.File
import java.io.FileOutputStream
import java.util.zip.Deflater

import kotlin.io.encoding.Base64
import kotlin.io.encoding.encodingWith

import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipParameters

import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.reporters.evaluatedmodel.EvaluatedModel
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterFactory
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.div

private const val PLACEHOLDER = "ORT_REPORT_DATA_PLACEHOLDER"

data class WebAppReporterConfig(
    /**
     * If true, subtrees occurring multiple times in the dependency tree are stripped.
     */
    @OrtPluginOption(defaultValue = "false")
    val deduplicateDependencyTree: Boolean
)

/**
 * A [Reporter] that generates a web application that allows browsing an ORT result interactively.
 */
@OrtPlugin(
    displayName = "WebApp",
    description = "Generates a web application to browse an ORT result interactively.",
    factory = ReporterFactory::class
)
class WebAppReporter(
    override val descriptor: PluginDescriptor = WebAppReporterFactory.descriptor,
    private val config: WebAppReporterConfig
) : Reporter {
    private val reportFilename = "scan-report-web-app.html"

    override fun generateReport(input: ReporterInput, outputDir: File): List<Result<File>> {
        val template = javaClass.getResource("/scan-report-template.html").readText()
        val evaluatedModel = EvaluatedModel.create(input, config.deduplicateDependencyTree)

        val index = template.indexOf(PLACEHOLDER)
        val prefix = template.substring(0, index)
        val suffix = template.substring(index + PLACEHOLDER.length, template.length)

        val reportFileResult = runCatching {
            val outputFile = outputDir / reportFilename

            outputFile.writeText(prefix)

            FileOutputStream(outputFile, /* append = */ true).use { outputStream ->
                val b64OutputStream = outputStream.encodingWith(Base64.Mime)

                val gzipParameters = GzipParameters().apply {
                    compressionLevel = Deflater.BEST_COMPRESSION
                }

                GzipCompressorOutputStream(b64OutputStream, gzipParameters).bufferedWriter().use { gzipWriter ->
                    evaluatedModel.toJson(gzipWriter, prettyPrint = false)
                }
            }

            outputFile.apply { appendText(suffix) }
        }

        return listOf(reportFileResult)
    }
}
