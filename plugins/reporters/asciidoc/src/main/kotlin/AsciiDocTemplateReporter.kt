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

import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.reporters.freemarker.FreemarkerTemplateProcessor
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.ort.createOrtTempDir

data class AsciiDocTemplateReporterConfig(
    /**
     * A comma-separated list of IDs of templates provided by ORT.
     * If no template id or path is provided, the "disclosure_document" template is used, and if the ORT result contains
     * an advisor run, the "vulnerability_report" and "defect_report" templates are used as well.
     */
    @OrtPluginOption(aliases = ["template.id"])
    val templateIds: List<String>?,

    /**
     * A comma-separated list of paths to template files provided by the user.
     */
    @OrtPluginOption(aliases = ["template.path"])
    val templatePaths: List<String>?
)

/**
 * An abstract [Reporter] that uses [Apache Freemarker][1] templates and [AsciiDoc][2] with [AsciidoctorJ][3] to create
 * reports for the supported [AsciiDoc converters][4], using the specified Asciidoctor [backend].
 *
 * [1]: https://freemarker.apache.org
 * [2]: https://asciidoc.org
 * [3]: https://github.com/asciidoctor/asciidoctorj
 * [4]: https://docs.asciidoctor.org/asciidoctor/latest/convert/available
 */
abstract class AsciiDocTemplateReporter(private val config: AsciiDocTemplateReporterConfig) : Reporter {
    companion object {
        private const val ASCII_DOC_FILE_PREFIX = "AsciiDoc_"
        private const val ASCII_DOC_FILE_EXTENSION = "adoc"
        private const val ASCII_DOC_TEMPLATE_DIRECTORY = "asciidoc"

        private const val DISCLOSURE_TEMPLATE_ID = "disclosure_document"
        private const val VULNERABILITY_TEMPLATE_ID = "vulnerability_report"
        private const val DEFECT_TEMPLATE_ID = "defect_report"

        internal val ASCIIDOCTOR by lazy { Asciidoctor.Factory.create() }
    }

    protected abstract val backend: String

    private val templateProcessor = FreemarkerTemplateProcessor(
        ASCII_DOC_TEMPLATE_DIRECTORY,
        ASCII_DOC_FILE_PREFIX,
        ASCII_DOC_FILE_EXTENSION
    )

    /**
     * Subclasses can override this function to add additional AsciiDoc attributes. By default, no attributes are added.
     */
    protected open fun generateAsciiDocAttributes(outputDir: File): Attributes = Attributes.builder().build()

    final override fun generateReport(input: ReporterInput, outputDir: File): List<Result<File>> {
        val asciiDocOutputDir = createOrtTempDir("asciidoc")

        val asciidoctorAttributes = generateAsciiDocAttributes(asciiDocOutputDir)
        val asciiDocFileResults = generateAsciiDocFiles(input, asciiDocOutputDir)
        val reportFileResults = processAsciiDocFiles(input, outputDir, asciiDocFileResults, asciidoctorAttributes)

        asciiDocOutputDir.safeDeleteRecursively()

        return reportFileResults
    }

    /**
     * Generate the AsciiDoc files from the templates defined in [config] in [outputDir].
     */
    private fun generateAsciiDocFiles(input: ReporterInput, outputDir: File): List<Result<File>> {
        val actualConfig = config.takeIf {
            it.templateIds?.isNotEmpty() == true || it.templatePaths?.isNotEmpty() == true
        } ?: AsciiDocTemplateReporterConfig(
            templateIds = buildList {
                add(DISCLOSURE_TEMPLATE_ID)

                if (input.ortResult.getAdvisorResults().isNotEmpty()) {
                    add(VULNERABILITY_TEMPLATE_ID)
                    add(DEFECT_TEMPLATE_ID)
                }
            },
            templatePaths = null
        )

        return templateProcessor.processTemplates(
            input,
            outputDir,
            actualConfig.templateIds.orEmpty(),
            actualConfig.templatePaths.orEmpty()
        )
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
                    ASCIIDOCTOR.convertFile(file, options)
                }
            }
        }
    }
}
