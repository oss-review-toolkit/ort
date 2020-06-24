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

private val templateFileNames = listOf(
    TEMPLATE_COVER_PDF, TEMPLATE_COPYRIGHT_PDF, TEMPLATE_CONTENT_PDF, TEMPLATE_BACK_PDF
)

class AntennaAttributionDocumentReporter : Reporter {
    override val reporterName = "AntennaAttributionDocument"

    private val reportFilename = "attribution-document.pdf"
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

        val artifacts = input.ortResult.getPackages().map { (pkg, _) ->
            val licenses = collectLicenses(pkg.id, input.ortResult)
            AttributionDocumentPdfModel(
                purl = pkg.purl,
                binaryFilename = pkg.binaryArtifact.takeUnless { it.url.isEmpty() }?.let { File(it.url).name },
                declaredLicenses = licenses.map { createLicenseInfo(it, input.licenseTextProvider) },
                copyrightStatements = createCopyrightStatement(pkg.id, licenses, licenseFindings)
            )
        }.toList()

        val rootProject = input.ortResult.getProjects().singleOrNull()

        // TODO: Add support for multiple projects.
        requireNotNull(rootProject) {
            "The $reporterName currently only supports ORT results with a single project."
        }

        val projectCopyright = createCopyrightStatement(
            rootProject.id,
            collectLicenses(rootProject.id, input.ortResult),
            licenseFindings
        )

        val workingDir = createTempDir()

        // Use the default template unless a custom template is provided via the options.
        var templateId = DEFAULT_TEMPLATE_ID
        val templateFiles = mutableMapOf<String, File>()

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
                require(templatePath.listFiles().isNotEmpty()) {
                    "The template path must point to a directory with template files if no template id is provided."
                }

                templateFileNames.associateWithTo(templateFiles) {
                    templatePath.resolve(it).also { file ->
                        require(file.isFile && file.extension == "pdf" && file.length() > 0) {
                            "The file '$file' does not point to a valid PDF file."
                        }
                    }
                }
            }
        }

        val generator = AttributionDocumentGeneratorImpl(
            reportFilename,
            workingDir,
            templateId,
            DocumentValues(rootProject.id.name, rootProject.id.version, projectCopyright)
        )

        val documentFile = if (templateFiles.size == templateFileNames.size) {
            generator.generate(
                    artifacts,
                    templateFiles[TEMPLATE_COVER_PDF],
                    templateFiles[TEMPLATE_COPYRIGHT_PDF],
                    templateFiles[TEMPLATE_CONTENT_PDF],
                    templateFiles[TEMPLATE_BACK_PDF]
                )
        } else {
            try {
                generator.generate(artifacts)
            } finally {
                if (templateId != DEFAULT_TEMPLATE_ID) removeTemplateFromClassPath()
            }
        }

        // Antenna keeps around temporary files in its working directory, so we cannot just use our output directory as
        // its working directory, but have to copy the file we are interested in.
        val outputFile = outputDir.resolve(reportFilename)
        documentFile.copyTo(outputFile)
        workingDir.safeDeleteRecursively()

        return listOf(outputFile)
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

    private fun createLicenseInfo(
        licenseId: String,
        licenseTextProvider: LicenseTextProvider
    ) =
        LicenseInfo(
            // Generate a key that is used as the license anchor in the PDF. A valid key consists of numbers and letters
            // only, any special characters are invalid.
            licenseId.toByteArray().toHexString(),
            // Replace tabs with spaces to work around an issue where the chosen font does not provide a (horizontal)
            // tab character, which otherwise causes something like:
            //
            //     IllegalArgumentException: U+0009 ('controlHT') is not available in this font Times-Roman encoding:
            //     WinAnsiEncoding
            licenseTextProvider.getLicenseText(licenseId)?.replace("\t", "    ") ?: "No license text found.",
            licenseId,
            SpdxLicense.forId(licenseId)?.fullName ?: licenseId
        )
}
