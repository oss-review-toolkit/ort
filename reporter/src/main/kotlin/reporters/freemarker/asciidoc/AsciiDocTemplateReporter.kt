/*
 * Copyright (C) 2020-2021 Bosch.IO GmbH
 * Copyright (C) 2021 HERE Europe B.V.
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

package org.ossreviewtoolkit.reporter.reporters.freemarker.asciidoc

import java.io.File

import org.asciidoctor.Asciidoctor
import org.asciidoctor.Attributes
import org.asciidoctor.Options
import org.asciidoctor.SafeMode

import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.reporter.reporters.freemarker.FreemarkerTemplateProcessor
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.core.createOrtTempDir

/**
 * An abstract [Reporter] that uses [Apache Freemarker][1] templates and [AsciiDoc][2] with [AsciidoctorJ][3] to create
 * reports for the supported [AsciiDoc converters][4], using the specified Asciidoctor [backend].
 *
 * [1]: https://freemarker.apache.org
 * [2]: https://asciidoc.org
 * [3]: https://github.com/asciidoctor/asciidoctorj
 * [4]: https://docs.asciidoctor.org/asciidoctor/latest/convert/available
 */
abstract class AsciiDocTemplateReporter(private val backend: String, override val reporterName: String) : Reporter {
    companion object {
        private const val ASCII_DOC_FILE_PREFIX = "AsciiDoc_"
        private const val ASCII_DOC_FILE_EXTENSION = "adoc"
        private const val ASCII_DOC_TEMPLATE_DIRECTORY = "asciidoc"

        private const val DISCLOSURE_TEMPLATE_ID = "disclosure_document"
        private const val VULNERABILITY_TEMPLATE_ID = "vulnerability_report"
        private const val DEFECT_TEMPLATE_ID = "defect_report"
    }

    private val templateProcessor = FreemarkerTemplateProcessor(
        ASCII_DOC_FILE_PREFIX,
        ASCII_DOC_FILE_EXTENSION,
        ASCII_DOC_TEMPLATE_DIRECTORY
    )
    private val asciidoctor by lazy { Asciidoctor.Factory.create() }

    protected open fun processTemplateOptions(options: MutableMap<String, String>): Attributes =
        Attributes.builder().build()

    final override fun generateReport(input: ReporterInput, outputDir: File, options: Map<String, String>): List<File> {
        val asciiDocOutputDir = createOrtTempDir("asciidoc")

        val templateOptions = options.toMutableMap()
        val asciidoctorAttributes = processTemplateOptions(templateOptions)
        val asciiDocFiles = generateAsciiDocFiles(input, asciiDocOutputDir, templateOptions)
        val reports = processAsciiDocFiles(outputDir, asciiDocFiles, asciidoctorAttributes)

        asciiDocOutputDir.safeDeleteRecursively()

        return reports
    }

    /**
     * Generate the AsciiDoc files from the templates defined in [options] in [outputDir].
     */
    private fun generateAsciiDocFiles(
        input: ReporterInput,
        outputDir: File,
        options: Map<String, String> = emptyMap()
    ): List<File> {
        val templateOptions = options.toMutableMap()

        if (FreemarkerTemplateProcessor.OPTION_TEMPLATE_PATH !in templateOptions) {
            templateOptions.putIfAbsent(
                FreemarkerTemplateProcessor.OPTION_TEMPLATE_ID,
                buildString {
                    append(DISCLOSURE_TEMPLATE_ID)

                    if (input.ortResult.getAdvisorResults().isNotEmpty()) {
                        append(",$VULNERABILITY_TEMPLATE_ID,$DEFECT_TEMPLATE_ID")
                    }
                }
            )
        }

        return templateProcessor.processTemplates(input, outputDir, templateOptions)
    }

    /**
     * Generate the reports for the [asciiDocFiles] using Asciidoctor in [outputDir] applying the
     * [asciidoctorAttributes].
     */
    protected open fun processAsciiDocFiles(
        outputDir: File,
        asciiDocFiles: List<File>,
        asciidoctorAttributes: Attributes
    ): List<File> {
        val optionsBuilder = Options.builder()
            .attributes(asciidoctorAttributes)
            .backend(backend)
            .safe(SafeMode.UNSAFE)

        val outputFiles = mutableListOf<File>()

        asciiDocFiles.forEach { file ->
            val outputFile = outputDir.resolve("${file.nameWithoutExtension}.$backend")

            asciidoctor.convertFile(file, optionsBuilder.toFile(outputFile).build())

            outputFiles += outputFile
        }

        return outputFiles
    }
}
