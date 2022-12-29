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

package org.ossreviewtoolkit.reporter.reporters.freemarker.asciidoc

import java.io.File

import org.asciidoctor.Attributes

import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.utils.common.safeMkdirs

/**
 * A [Reporter] that creates PDF files using a combination of [Apache Freemarker][1] templates and [AsciiDoc][2]
 * with [AsciidoctorJ][3] as Java interface and [AsciidoctorJ PDF][4] as PDF file generator.
 * For each Freemarker template provided using the options described below a separate intermediate file is created
 * that can be processed by AsciidoctorJ. If no options are provided, the "disclosure_document" template is used, and if
 * security vulnerability information is available also the "vulnerability_report" template.
 *
 * After the intermediate files are generated, they are processed by  AsciidoctorJ PDF.
 * A PDF theme can be handed over to AsciidoctorJ PDF in which properties like fonts or images displayed in the PDF can
 * be adjusted; see the [Theme Guide][5].
 * The path to this theme can be set in the options as described below.
 * Note that only one theme can be set that is used for all given templates. If no theme is given, a default built-in
 * theme of AsciidoctorJ PDF is used.
 *
 * This reporter supports the following options:
 * - *template.id*: A comma-separated list of IDs of templates provided by ORT. Currently, the "disclosure_document" and
 *                  "vulnerability_report" templates are available.
 * - *template.path*: A comma-separated list of paths to template files provided by the user.
 * - *pdf.theme.file*: A path to an AsciiDoc PDF theme file. Only used with the "pdf" backend.
 * - *pdf.fonts.dir*: A path to a directory containing custom fonts. Only used with the "pdf" backend.
 * - *project-types-as-packages: A comma-separated list of project types to be handled as packages.
 *
 * [1]: https://freemarker.apache.org
 * [2]: https://asciidoc.org/
 * [3]: https://github.com/asciidoctor/asciidoctorj
 * [4]: https://github.com/asciidoctor/asciidoctorj-pdf
 * [5]: https://docs.asciidoctor.org/pdf-converter/latest/theme/
 */
class PdfTemplateReporter : AsciiDocTemplateReporter("pdf", "PdfTemplate") {
    companion object {
        private const val OPTION_PDF_THEME_FILE = "pdf.theme.file"
        private const val OPTION_PDF_FONTS_DIR = "pdf.fonts.dir"
    }

    override fun processTemplateOptions(outputDir: File, options: MutableMap<String, String>): Attributes =
        Attributes.builder().apply {
            val pdfThemeAttribute = options.remove(OPTION_PDF_THEME_FILE)?.let {
                val pdfThemeFile = File(it).absoluteFile

                require(pdfThemeFile.isFile) { "Could not find PDF theme file at '$pdfThemeFile'." }

                pdfThemeFile.path
            } ?: run {
                // Images are being looked up relative to the themes directory. As images currently are the only use for
                // the themes directory, point it at the images directory. However, the themes directory does not
                // support the "uri:classloader:" syntax and can only refer to local paths, see
                // https://github.com/asciidoctor/asciidoctor-pdf/issues/2383. So extract the images resource to the
                // temporary directory and point to there.
                val imagesDir = outputDir.resolve("images").safeMkdirs()
                extractImageResources(imagesDir)
                attribute("pdf-themesdir", imagesDir.absolutePath)

                "uri:classloader:/templates/asciidoc/pdf-theme.yml"
            }

            attribute("pdf-theme", pdfThemeAttribute)

            val pdfFontsDirAttribute = options.remove(OPTION_PDF_FONTS_DIR)?.let {
                val pdfFontsDir = File(it).absoluteFile

                require(pdfFontsDir.isDirectory) { "Could not find PDF fonts directory at '$pdfFontsDir'." }

                pdfFontsDir.path
            } ?: "uri:classloader:/fonts"

            attribute("pdf-fontsdir", "$pdfFontsDirAttribute,GEM_FONTS_DIR")
        }.build()

    private fun extractImageResources(targetDir: File) {
        val imagesResourceDir = "/images"
        val imageNames = listOf("ort.png")

        imageNames.forEach { imageName ->
            javaClass.getResourceAsStream("$imagesResourceDir/$imageName").use { inputStream ->
                targetDir.resolve(imageName).outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
    }
}
