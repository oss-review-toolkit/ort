/*
 * Copyright (C) 2020 Bosch.IO GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

import org.asciidoctor.Asciidoctor
import org.asciidoctor.AttributesBuilder
import org.asciidoctor.OptionsBuilder
import org.asciidoctor.SafeMode
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.reporter.utils.FreemarkerTemplateProcessor

/**
 * A [Reporter] that creates PDF files using a combination of [Apache Freemarker][1] templates and [Asciidoc][2]
 * with [AsciidoctorJ][3] as Java interface and [AsciidoctorJ PDF][4] as PDF file generator.
 * For each Freemarker template provided using the options described below a separate intermediate file is created
 * that can be processed by AsciidoctorJ. If no options are provided the "default" template is used.
 * The name of the template id or template path (without extension) is used for the intermediate file,
 * so be careful to not use two different templates with the same name.
 *
 * After the intermediate files are generated, they are processed by  AsciidoctorJ PDF.
 * A PDF theme can be handed over to AsciidoctorJ PDF in which properties like fonts or images displayed in the PDF can
 * be adjusted; see the [Theme Guide][5].
 * The path to this theme can be set in the options as described below.
 * Note that only one theme can be set that is used for all given templates. If no theme is given, a default built-in
 * theme of AsciidoctorJ PDF is used.
 *
 * This reporter supports the following options:
 * - *template.id*: A comma-separated list of IDs of templates provided by ORT. Currently only the "default"
 *                  template is available.
 * - *template.path*: A comma-separated list of paths to template files provided by the user.
 * - *pdf-theme.path*: A path to an Asciidoc PDF theme file.
 *
 * [1]: https://freemarker.apache.org
 * [2]: https://asciidoc.org/
 * [3]: https://github.com/asciidoctor/asciidoctorj
 * [4]: https://github.com/asciidoctor/asciidoctorj-pdf
 * [5]: https://github.com/asciidoctor/asciidoctor-pdf/blob/master/docs/theming-guide.adoc
 */
class AsciidocTemplateReporter : Reporter {
    companion object {
        private const val ASCIIDOC_FILE_PREFIX = "Asciidoc_"
        private const val ASCIIDOC_FILE_EXTENSION = "adoc"
        private const val ASCIIDOC_TEMPLATE_DIRECTORY = "asciidoc"

        private const val OPTION_PDF_THEME_PATH = "pdf-theme.path"
    }

    private val templateProcessor = FreemarkerTemplateProcessor(
        ASCIIDOC_FILE_PREFIX,
        ASCIIDOC_FILE_EXTENSION,
        ASCIIDOC_TEMPLATE_DIRECTORY
    )
    private val asciidoctor = Asciidoctor.Factory.create()

    override val reporterName = "AsciidocTemplate"

    override fun generateReport(input: ReporterInput, outputDir: File, options: Map<String, String>): List<File> {
        val asciidoctorAttributes = AttributesBuilder.attributes()

        options[OPTION_PDF_THEME_PATH]?.let { themePath ->
            File(themePath).also {
                require(it.isFile) { "Could not find pdf-theme file at '${it.absolutePath}'." }
            }

            asciidoctorAttributes.attribute("pdf-theme", themePath)
        }

        val asciidocFiles = templateProcessor.processTemplates(input, outputDir, options)

        val outputFiles = mutableListOf<File>()

        asciidocFiles.forEach { file ->
            val outputFile = outputDir.resolve("${file.nameWithoutExtension}.pdf")

            val asciidoctorOptions = OptionsBuilder.options()
                .backend("pdf")
                .toFile(outputFile)
                .attributes(asciidoctorAttributes)
                .safe(SafeMode.UNSAFE)

            asciidoctor.convertFile(file, asciidoctorOptions)

            outputFiles += outputFile
            file.delete()
        }

        return outputFiles
    }
}
