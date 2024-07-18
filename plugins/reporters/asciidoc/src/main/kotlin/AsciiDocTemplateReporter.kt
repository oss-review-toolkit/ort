/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.reporters.asciidoc

import java.io.File

import org.asciidoctor.Asciidoctor
import org.asciidoctor.Attributes
import org.asciidoctor.Options
import org.asciidoctor.SafeMode

import org.ossreviewtoolkit.model.config.PluginConfiguration
import org.ossreviewtoolkit.plugins.reporters.freemarker.FreemarkerTemplateProcessor
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.ort.createOrtTempDir

/**
 * An abstract [Reporter] that uses [Apache Freemarker][1] templates and [AsciiDoc][2] with [AsciidoctorJ][3] to create
 * reports for the supported [AsciiDoc converters][4], using the specified Asciidoctor [backend].
 *
 * [1]: https://freemarker.apache.org
 * [2]: https://asciidoc.org
 * [3]: https://github.com/asciidoctor/asciidoctorj
 * [4]: https://docs.asciidoctor.org/asciidoctor/latest/convert/available
 */
open class AsciiDocTemplateReporter(private val backend: String, override val type: String) : Reporter {
    companion object {
        private const val ASCII_DOC_FILE_PREFIX = "AsciiDoc_"
        private const val ASCII_DOC_FILE_EXTENSION = "adoc"
        private const val ASCII_DOC_TEMPLATE_DIRECTORY = "asciidoc"

        private const val DISCLOSURE_TEMPLATE_ID = "disclosure_document"
        private const val VULNERABILITY_TEMPLATE_ID = "vulnerability_report"
        private const val DEFECT_TEMPLATE_ID = "defect_report"
    }

    private val templateProcessor = FreemarkerTemplateProcessor(
        ASCII_DOC_TEMPLATE_DIRECTORY,
        ASCII_DOC_FILE_PREFIX,
        ASCII_DOC_FILE_EXTENSION
    )

    internal val asciidoctor by lazy { Asciidoctor.Factory.create() }

    /**
     * Turn recognized [options] into [Attributes] and remove them from [options] afterwards to mark them as processed.
     * By default no [options] are processed and the returned [Attributes] are empty.
     */
    protected open fun processTemplateOptions(outputDir: File, options: MutableMap<String, String>): Attributes =
        Attributes.builder().build()

    final override fun generateReport(
        input: ReporterInput,
        outputDir: File,
        config: PluginConfiguration
    ): List<Result<File>> {
        val asciiDocOutputDir = createOrtTempDir("asciidoc")

        val templateOptions = config.options.toMutableMap()
        val asciidoctorAttributes = processTemplateOptions(asciiDocOutputDir, templateOptions)
        val asciiDocFileResults = generateAsciiDocFiles(input, asciiDocOutputDir, templateOptions)
        val reportFileResults = processAsciiDocFiles(input, outputDir, asciiDocFileResults, asciidoctorAttributes)

        asciiDocOutputDir.safeDeleteRecursively()

        return reportFileResults
    }

    /**
     * Generate the AsciiDoc files from the templates defined in [options] in [outputDir].
     */
    private fun generateAsciiDocFiles(
        input: ReporterInput,
        outputDir: File,
        options: MutableMap<String, String>
    ): List<Result<File>> {
        if (FreemarkerTemplateProcessor.OPTION_TEMPLATE_PATH !in options) {
            options.putIfAbsent(
                FreemarkerTemplateProcessor.OPTION_TEMPLATE_ID,
                buildString {
                    append(DISCLOSURE_TEMPLATE_ID)

                    if (input.ortResult.getAdvisorResults().isNotEmpty()) {
                        append(",$VULNERABILITY_TEMPLATE_ID,$DEFECT_TEMPLATE_ID")
                    }
                }
            )
        }

        return templateProcessor.processTemplates(input, outputDir, options)
    }

    /**
     * Generate the reports for the [asciiDocFileResults] using Asciidoctor in [outputDir] applying the
     * [asciidoctorAttributes].
     */
    protected open fun processAsciiDocFiles(
        input: ReporterInput,
        outputDir: File,
        asciiDocFileResults: List<Result<File>>,
        asciidoctorAttributes: Attributes
    ): List<Result<File>> {
        val optionsBuilder = Options.builder()
            .attributes(asciidoctorAttributes)
            .backend(backend)
            .safe(SafeMode.UNSAFE)

        return asciiDocFileResults.map { fileResult ->
            // This implicitly passes through any failure from generating the Asciidoc file.
            fileResult.mapCatching { file ->
                outputDir.resolve("${file.nameWithoutExtension}.$backend").also { outputFile ->
                    val options = optionsBuilder.toFile(outputFile).build()
                    asciidoctor.convertFile(file, options)
                }
            }
        }
    }
}
