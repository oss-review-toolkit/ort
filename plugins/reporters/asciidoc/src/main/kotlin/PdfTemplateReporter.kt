/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import org.asciidoctor.Attributes

import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterFactory

data class PdfTemplateReporterConfig(
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
    val templatePaths: List<String>?,

    /**
     * The path to an AsciiDoc PDF theme file.
     */
    @OrtPluginOption(aliases = ["pdf.theme.file"], defaultValue = "uri:classloader:/pdf-theme/pdf-theme.yml")
    val pdfThemeFile: String,

    /**
     * The path to a directory containing custom fonts.
     */
    @OrtPluginOption(aliases = ["pdf.fonts.dir"], defaultValue = "uri:classloader:/fonts")
    val pdfFontsDir: String
)

/**
 * A [Reporter] that creates PDF files using a combination of [Apache Freemarker][1] templates and [AsciiDoc][2]
 * with [AsciidoctorJ][3] as Java interface and [AsciidoctorJ PDF][4] as PDF file generator.
 * For each Freemarker template provided using the options described below, a separate intermediate file is created
 * that can be processed by AsciidoctorJ. If no options are provided, the "disclosure_document" template is used, and if
 * security vulnerability information is available also the "vulnerability_report" template.
 *
 * After the intermediate files are generated, they are processed by AsciidoctorJ PDF.
 * A PDF theme can be handed over to AsciidoctorJ PDF in which properties like fonts or images displayed in the PDF can
 * be adjusted; see the [Theme Guide][5].
 * The path to this theme can be set in the options as described below.
 * Note that only one theme can be set that is used for all given templates. If no theme is given, a default built-in
 * theme of AsciidoctorJ PDF is used.
 *
 * [1]: https://freemarker.apache.org
 * [2]: https://asciidoc.org/
 * [3]: https://github.com/asciidoctor/asciidoctorj
 * [4]: https://github.com/asciidoctor/asciidoctorj-pdf
 * [5]: https://docs.asciidoctor.org/pdf-converter/latest/theme/
 */
@OrtPlugin(
    displayName = "PDF Template",
    description = "Generates PDF from AsciiDoc files from Apache Freemarker templates.",
    factory = ReporterFactory::class
)
class PdfTemplateReporter(
    override val descriptor: PluginDescriptor = PdfTemplateReporterFactory.descriptor,
    private val config: PdfTemplateReporterConfig
) : AsciiDocTemplateReporter(AsciiDocTemplateReporterConfig(config.templateIds, config.templatePaths)) {
    override val backend = "pdf"

    override fun generateAsciiDocAttributes(outputDir: File): Attributes =
        Attributes.builder().apply {
            val pdfTheme = if (config.pdfThemeFile.startsWith("uri:")) {
                config.pdfThemeFile
            } else {
                File(config.pdfThemeFile).absoluteFile.also {
                    require(it.isFile) { "Could not find PDF theme file at '$it'." }
                }.path
            }

            attribute("pdf-theme", pdfTheme)

            val pdfFontsDir = if (config.pdfFontsDir.startsWith("uri:")) {
                config.pdfFontsDir
            } else {
                File(config.pdfFontsDir).absoluteFile.also {
                    require(it.isDirectory) { "Could not find PDF fonts directory at '$it'." }
                }.path
            }

            attribute("pdf-fontsdir", "$pdfFontsDir,GEM_FONTS_DIR")
        }.build()
}
