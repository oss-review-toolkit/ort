/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.reporter.reporters

import java.io.File
import java.io.FileOutputStream
import java.util.Base64
import java.util.zip.Deflater

import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipParameters

import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.reporter.reporters.evaluatedmodel.EvaluatedModel

private const val PLACEHOLDER = "ORT_REPORT_DATA_PLACEHOLDER"

/**
 * A [Reporter] that generates a web application that allows browsing an ORT result interactively.
 *
 * This reporter supports the following options:
 * - *deduplicateDependencyTree*: Controls whether subtrees occurring multiple times in the dependency tree are
 *   stripped.
 */
class WebAppReporter : Reporter {
    companion object {
        const val OPTION_DEDUPLICATE_DEPENDENCY_TREE = "deduplicateDependencyTree"
    }

    override val reporterName = "WebApp"

    private val reportFilename = "scan-report-web-app.html"

    override fun generateReport(
        input: ReporterInput,
        outputDir: File,
        options: Map<String, String>
    ): List<File> {
        val template = javaClass.getResource("/scan-report-template.html").readText()
        val evaluatedModel = EvaluatedModel.create(input, options[OPTION_DEDUPLICATE_DEPENDENCY_TREE].toBoolean())

        val index = template.indexOf(PLACEHOLDER)
        val prefix = template.substring(0, index)
        val suffix = template.substring(index + PLACEHOLDER.length, template.length)

        val outputFile = outputDir.resolve(reportFilename)

        outputFile.writeText(prefix)

        FileOutputStream(outputFile, /* append = */ true).use { outputStream ->
            val b64OutputStream = Base64.getEncoder().wrap(outputStream)

            val gzipParameters = GzipParameters().apply {
                compressionLevel = Deflater.BEST_COMPRESSION
            }
            GzipCompressorOutputStream(b64OutputStream, gzipParameters).bufferedWriter().use { gzipWriter ->
                evaluatedModel.toJson(gzipWriter, prettyPrint = false)
            }
        }

        outputFile.appendText(suffix)

        return listOf(outputFile)
    }
}
