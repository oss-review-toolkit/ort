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
import java.net.URL
import java.net.URLClassLoader
import java.util.SortedSet

import org.eclipse.sw360.antenna.attribution.document.core.AttributionDocumentGeneratorImpl
import org.eclipse.sw360.antenna.attribution.document.core.DocumentValues
import org.eclipse.sw360.antenna.attribution.document.core.model.LicenseInfo

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseFindings
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.utils.collectLicenseFindings
import org.ossreviewtoolkit.model.utils.getDetectedLicensesForId
import org.ossreviewtoolkit.reporter.LicenseTextProvider
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.reporter.utils.AttributionDocumentPdfModel
import org.ossreviewtoolkit.spdx.SpdxLicense
import org.ossreviewtoolkit.utils.safeDeleteRecursively
import org.ossreviewtoolkit.utils.toHexString

private const val DEFAULT_TEMPLATE_ID = "basic-pdf-template"
private const val TEMPLATE_ID = "template.id"
private const val TEMPLATE_PATH = "template.path"

private const val TEMPLATE_COVER_PDF = "cover.pdf"
private const val TEMPLATE_COPYRIGHT_PDF = "copyright.pdf"
private const val TEMPLATE_CONTENT_PDF = "content.pdf"
private const val TEMPLATE_BACK_PDF = "back.pdf"

private const val REGULAR_FONT = "regular.ttf"
private const val BOLD_FONT = "bold.ttf"
private const val BOLD_ITALIC_FONT = "bold-italic.ttf"
private const val ITALIC_FONT = "italic.ttf"

private val templateFileNames = listOf(
    TEMPLATE_COVER_PDF, TEMPLATE_COPYRIGHT_PDF, TEMPLATE_CONTENT_PDF, TEMPLATE_BACK_PDF
)
private val fontFileNames = listOf(
    REGULAR_FONT, BOLD_FONT, BOLD_ITALIC_FONT, ITALIC_FONT
)

private const val REPORT_BASE_FILENAME = "attribution-document"
private const val REPORT_EXTENSION = "pdf"

private val GLYPH_REPLACEMENTS = mapOf(
    "\u0009" to "    ",
    "\u0092" to "",
    "\u009d" to "",
    "\u00a0" to " ",
    "\u00ad" to "-",
    "\u0159" to "r",
    "\u037e" to ";",
    "\u200b" to "",
    "\u2010" to "-",
    "\u2011" to "-",
    "\u2028" to "\n",
    "\u2212" to "-",
    "\u221e" to "(infinity)",
    "\u25aa" to "[]",
    "\u2661" to "(heart)",
    "\udbff\udc00" to "", // See http://www.fileformat.info/info/unicode/char/10fc00/index.htm.
    "\uf0b7" to "",
    "\ufeff" to ""
)

private val REPLACEMENT_REGEX = GLYPH_REPLACEMENTS.keys.joinToString("|", "(", ")").toRegex()

fun replaceGlyphs(text: String) = REPLACEMENT_REGEX.replace(text) { GLYPH_REPLACEMENTS.getValue(it.value) }

class AntennaAttributionDocumentReporter : Reporter {
    override val reporterName = "AntennaAttributionDocument"

    private val reportFilename = "$REPORT_BASE_FILENAME.$REPORT_EXTENSION"
    private val originalClassLoader = Thread.currentThread().contextClassLoader

    override fun generateReport(
        input: ReporterInput,
        outputDir: File,
        options: Map<String, String>
    ): List<File> {
        val licenseFindings = input.ortResult.collectLicenseFindings(
            input.packageConfigurationProvider,
            omitExcluded = true
        )

        // Use the default template unless a custom template is provided via the options.
        var templateId = DEFAULT_TEMPLATE_ID
        val templateFiles = mutableMapOf<String, File>()
        val fontFiles = mutableMapOf<String, File>()

        val providedTemplateId = options[TEMPLATE_ID]
        val providedTemplatePath = options[TEMPLATE_PATH]
        if (providedTemplatePath != null) {
            val templatePath = File(providedTemplatePath)

            if (templatePath.isFile) {
                require(templatePath.extension == "jar" && templatePath.length() > 0) {
                    "The template path does not point to a valid template bundle file."
                }

                requireNotNull(providedTemplateId) {
                    "When providing a template bundle file, also a template id has to be specified."
                }

                addTemplateToClassPath(templatePath.toURI().toURL()).also {
                    templateId = providedTemplateId
                }
            } else if (templatePath.isDirectory) {
                require(templatePath.walk().maxDepth(1).filter { it.isFile }.any()) {
                    "The template path must point to a directory with template files if no template id is provided."
                }

                templateFileNames.associateWithTo(templateFiles) {
                    templatePath.resolve(it).also { file ->
                        require(file.isFile && file.extension == "pdf" && file.length() > 0) {
                            "The file '$file' does not point to a valid PDF file."
                        }
                    }
                }

                fontFileNames.associateWithTo(fontFiles) { templatePath.resolve(it) }

                val invalidFontFiles = fontFiles.values.filterNot { file ->
                    file.isFile && file.extension == "ttf" && file.length() > 0
                }

                require(invalidFontFiles.isEmpty() || invalidFontFiles.size == fontFileNames.size) {
                    val fileList = invalidFontFiles.joinToString(", ") { "'$it'" }
                    "The file(s) $fileList do(es) not point to a valid font file."
                }
            }
        }

        val outputFiles = mutableListOf<File>()
        val projects = input.ortResult.getProjects(omitExcluded = true)

        try {
            projects.forEach { project ->
                val dependencies = project.collectDependencies()

                val packages = input.ortResult.getPackages(omitExcluded = true).mapNotNull { (pkg, _) ->
                    pkg.takeIf { it.id in dependencies }
                }

                val artifacts = packages.map { pkg ->
                    val licenses = collectLicenses(pkg.id, input.ortResult)
                    AttributionDocumentPdfModel(
                        purl = pkg.purl,
                        binaryFilename = pkg.binaryArtifact.takeUnless { it.url.isEmpty() }?.let { File(it.url).name },
                        declaredLicenses = licenses.map { createLicenseInfo(it, input.licenseTextProvider) },
                        copyrightStatements = createCopyrightStatement(pkg.id, licenses, licenseFindings)
                    )
                }.toList()

                val projectCopyright = createCopyrightStatement(
                    project.id,
                    collectLicenses(project.id, input.ortResult),
                    licenseFindings
                )

                val workingDir = createTempDir()

                val generator = AttributionDocumentGeneratorImpl(
                    reportFilename,
                    workingDir,
                    templateId,
                    DocumentValues(project.id.name, project.id.version, projectCopyright)
                )

                val documentFile = if (templateFiles.size == templateFileNames.size) {
                    if (fontFiles.size == fontFileNames.size) {
                        generator.generate(
                            artifacts,
                            templateFiles[TEMPLATE_COVER_PDF],
                            templateFiles[TEMPLATE_COPYRIGHT_PDF],
                            templateFiles[TEMPLATE_CONTENT_PDF],
                            templateFiles[TEMPLATE_BACK_PDF],
                            fontFiles[REGULAR_FONT],
                            fontFiles[BOLD_FONT],
                            fontFiles[BOLD_ITALIC_FONT],
                            fontFiles[ITALIC_FONT]
                        )
                    } else {
                        generator.generate(
                            artifacts,
                            templateFiles[TEMPLATE_COVER_PDF],
                            templateFiles[TEMPLATE_COPYRIGHT_PDF],
                            templateFiles[TEMPLATE_CONTENT_PDF],
                            templateFiles[TEMPLATE_BACK_PDF]
                        )
                    }
                } else {
                    generator.generate(artifacts)
                }

                val reportFilename = "$REPORT_BASE_FILENAME-${project.id.toPath("-")}.$REPORT_EXTENSION"
                val outputFile = outputDir.resolve(reportFilename)

                // Antenna keeps around temporary files in its working directory, so we cannot just use our output
                // directory as its working directory, but have to copy the file we are interested in.
                documentFile.copyTo(outputFile)
                workingDir.safeDeleteRecursively()

                outputFiles += outputFile
            }
        } finally {
            if (templateId != DEFAULT_TEMPLATE_ID) removeTemplateFromClassPath()
        }

        return outputFiles
    }

    private fun createCopyrightStatement(
        id: Identifier,
        licenses: SortedSet<String>,
        licenseFindings: Map<Identifier, Map<LicenseFindings, List<PathExclude>>>
    ) =
        licenseFindings.getOrDefault(id, emptyMap())
            .filter { licenses.contains(it.key.license.toString()) }
            .flatMap { it.key.copyrights }
            .joinToString("\n") { it.statement }

    private fun addTemplateToClassPath(url: URL) {
        Thread.currentThread().contextClassLoader = URLClassLoader(arrayOf(url), originalClassLoader)
    }

    private fun removeTemplateFromClassPath() {
        Thread.currentThread().contextClassLoader = originalClassLoader
    }

    private fun collectLicenses(id: Identifier, ortResult: OrtResult): SortedSet<String> {
        val concludedLicense = ortResult.getConcludedLicensesForId(id)?.licenses() ?: emptyList()
        val declaredLicense = ortResult.getDeclaredLicensesForId(id)
        val detectedLicense = ortResult.getDetectedLicensesForId(id)

        return if (concludedLicense.isNotEmpty()) {
            concludedLicense.toSortedSet()
        } else {
            (declaredLicense + detectedLicense).toSortedSet()
        }
    }

    private fun createLicenseInfo(licenseId: String, licenseTextProvider: LicenseTextProvider): LicenseInfo {
        // Generate a key that is used as the license anchor in the PDF. A valid key consists of numbers and letters
        // only, any special characters are invalid.
        val key = licenseId.toByteArray().toHexString()

        // Replace characters that are not available in the Times-Roman font with WinAnsi encoding until we have a
        // proper fix for https://github.com/oss-review-toolkit/ort/issues/2755.
        val licenseText = licenseTextProvider.getLicenseText(licenseId) ?: "No license text found."

        val shortName = SpdxLicense.forId(licenseId)?.fullName ?: licenseId

        return LicenseInfo(key, replaceGlyphs(licenseText), licenseId, shortName)
    }
}
