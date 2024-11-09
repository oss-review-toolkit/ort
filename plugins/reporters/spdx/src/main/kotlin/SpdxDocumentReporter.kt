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

package org.ossreviewtoolkit.plugins.reporters.spdx

import java.io.File

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterFactory
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.spdx.SpdxCompoundExpression
import org.ossreviewtoolkit.utils.spdx.SpdxConstants.LICENSE_REF_PREFIX
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseWithExceptionExpression
import org.ossreviewtoolkit.utils.spdx.SpdxModelMapper.FileFormat
import org.ossreviewtoolkit.utils.spdx.model.SpdxDocument

data class SpdxDocumentReporterConfig(
    /**
     * The comment to add to the [SpdxDocument.creationInfo].
     */
    val creationInfoComment: String?,

    /**
     * The person to add to the [SpdxDocument.creationInfo].
     */
    val creationInfoPerson: String?,

    /**
     * The organization to add to the [SpdxDocument.creationInfo].
     */
    val creationInfoOrganization: String?,

    /**
     * The comment to add to the [SpdxDocument].
     */
    val documentComment: String?,

    /**
     * The name of the generated [SpdxDocument].
     */
    @OrtPluginOption(defaultValue = "Unnamed document")
    val documentName: String,

    /**
     * The list of file formats to generate. Supported values are "YAML" and "JSON".
     */
    @OrtPluginOption(defaultValue = "YAML")
    val outputFileFormats: List<String>,

    /**
     * Toggle whether the output document should contain information on file granularity about files containing
     * findings.
     */
    @OrtPluginOption(defaultValue = "true")
    val fileInformationEnabled: Boolean
)

/**
 * Creates YAML and JSON SPDX documents mainly targeting the use case of sharing information about the dependencies
 * used, similar to e.g. a NOTICE file. Information about the project / submodule structure as well as project VCS
 * locations are deliberately omitted. The underlying idea is to clearly separate this mentioned use case from a maximum
 * detailed report which could be preferred for archiving or internal use only. The latter could be implemented either
 * as a future extension of this [SpdxDocumentReporter] or as a separate [Reporter].
 */
@OrtPlugin(
    displayName = "SPDX Document Reporter",
    description = "Creates software bills of materials (SBOM) in the SPDX format.",
    factory = ReporterFactory::class
)
class SpdxDocumentReporter(
    override val descriptor: PluginDescriptor = SpdxDocumentReporterFactory.descriptor,
    private val config: SpdxDocumentReporterConfig
) : Reporter {
    companion object {
        const val REPORT_BASE_FILENAME = "bom.spdx"
    }

    override fun generateReport(input: ReporterInput, outputDir: File): List<Result<File>> {
        val outputFileFormats = config.outputFileFormats
            .mapTo(mutableSetOf()) { FileFormat.valueOf(it.uppercase()) }

        val params = SpdxDocumentModelMapper.SpdxDocumentParams(
            documentName = config.documentName,
            documentComment = config.documentComment.orEmpty(),
            creationInfoComment = config.creationInfoComment.orEmpty(),
            creationInfoPerson = config.creationInfoPerson.orEmpty(),
            creationInfoOrganization = config.creationInfoOrganization.orEmpty(),
            fileInformationEnabled = config.fileInformationEnabled
        )

        val spdxDocument = SpdxDocumentModelMapper.map(
            input.ortResult,
            input.licenseInfoResolver,
            input.licenseTextProvider,
            params
        )

        val licenseRefExceptions = spdxDocument.getLicenseRefExceptions()
        if (licenseRefExceptions.isNotEmpty()) {
            logger.warn {
                "The SPDX document contains the following ${licenseRefExceptions.size} '$LICENSE_REF_PREFIX' " +
                    "exceptions used by a '${SpdxExpression.WITH}' operator which does not conform with SPDX " +
                    "specification version 2:\n${licenseRefExceptions.joinToString("\n")}\nYou may be able to use " +
                    "license curations to fix up these exceptions into valid SPDX v2 license expressions."
            }
        }

        return outputFileFormats.map { fileFormat ->
            runCatching {
                outputDir.resolve("$REPORT_BASE_FILENAME.${fileFormat.fileExtension}").apply {
                    bufferedWriter().use { writer ->
                        fileFormat.mapper.writeValue(writer, spdxDocument)
                    }
                }
            }
        }
    }
}

private fun SpdxDocument.getLicenseRefExceptions(): Set<String> {
    val licenses = buildSet {
        packages.flatMapTo(this) { it.licenseInfoFromFiles }
        files.flatMapTo(this) { it.licenseInfoInFiles }
        snippets.flatMapTo(this) { it.licenseInfoInSnippets }
    }

    return buildSet {
        licenses.forEach { license ->
            SpdxExpression.parse(license).getLicenseRefExceptions(this)
        }
    }
}

private fun SpdxExpression.getLicenseRefExceptions(result: MutableSet<String>) {
    when (this) {
        is SpdxCompoundExpression -> children.forEach { it.getLicenseRefExceptions(result) }
        is SpdxLicenseWithExceptionExpression -> if (isPresent() && exception.startsWith(LICENSE_REF_PREFIX)) {
            result.add(exception)
        }

        else -> { }
    }
}
