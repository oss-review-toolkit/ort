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

import org.ossreviewtoolkit.model.config.PluginConfiguration
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.spdx.SpdxCompoundExpression
import org.ossreviewtoolkit.utils.spdx.SpdxConstants.LICENSE_REF_PREFIX
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseWithExceptionExpression
import org.ossreviewtoolkit.utils.spdx.SpdxModelMapper.FileFormat
import org.ossreviewtoolkit.utils.spdx.model.SpdxDocument

/**
 * Creates YAML and JSON SPDX documents mainly targeting the use case of sharing information about the dependencies
 * used, similar to e.g. a NOTICE file. Information about the project / submodule structure as well as project VCS
 * locations are deliberately omitted. The underlying idea is to clearly separate this mentioned use case from a maximum
 * detailed report which could be preferred for archiving or internal use only. The latter could be implemented either
 * as a future extension of this [SpdxDocumentReporter] or as a separate [Reporter].
 *
 * This reporter supports the following options:
 * - *creationInfo.comment*: Add the corresponding value as metadata to the [SpdxDocument.creationInfo].
 * - *creationInfo.person*: Add the corresponding value as metadata to the [SpdxDocument.creationInfo].
 * - *creationInfo.organization*: Add the corresponding value as metadata to the [SpdxDocument.creationInfo].
 * - *document.comment*: Add the corresponding value as metadata to the [SpdxDocument].
 * - *document.name*: The name of the generated [SpdxDocument], defaults to "Unnamed document".
 * - *output.file.formats*: The list of [FileFormat]s to generate, defaults to [FileFormat.YAML].
 * - *file.information.enabled*: Toggle whether the output document should contain information on file granularity
 *                               about files containing findings.
 */
class SpdxDocumentReporter : Reporter {
    companion object {
        const val REPORT_BASE_FILENAME = "bom.spdx"

        const val OPTION_CREATION_INFO_COMMENT = "creationInfo.comment"
        const val OPTION_CREATION_INFO_PERSON = "creationInfo.person"
        const val OPTION_CREATION_INFO_ORGANIZATION = "creationInfo.organization"
        const val OPTION_DOCUMENT_COMMENT = "document.comment"
        const val OPTION_DOCUMENT_NAME = "document.name"
        const val OPTION_OUTPUT_FILE_FORMATS = "output.file.formats"
        const val OPTION_FILE_INFORMATION_ENABLED = "file.information.enabled"

        private const val DOCUMENT_NAME_DEFAULT_VALUE = "Unnamed document"
    }

    override val type = "SpdxDocument"

    override fun generateReport(
        input: ReporterInput,
        outputDir: File,
        config: PluginConfiguration
    ): List<Result<File>> {
        val outputFileFormats = config.options[OPTION_OUTPUT_FILE_FORMATS]
            ?.split(',')
            ?.mapTo(mutableSetOf()) { FileFormat.valueOf(it.uppercase()) }
            ?: setOf(FileFormat.YAML)

        val params = SpdxDocumentModelMapper.SpdxDocumentParams(
            documentName = config.options.getOrDefault(OPTION_DOCUMENT_NAME, DOCUMENT_NAME_DEFAULT_VALUE),
            documentComment = config.options.getOrDefault(OPTION_DOCUMENT_COMMENT, ""),
            creationInfoComment = config.options.getOrDefault(OPTION_CREATION_INFO_COMMENT, ""),
            creationInfoPerson = config.options.getOrDefault(OPTION_CREATION_INFO_PERSON, ""),
            creationInfoOrganization = config.options.getOrDefault(OPTION_CREATION_INFO_ORGANIZATION, ""),
            fileInformationEnabled = config.options.getOrDefault(OPTION_FILE_INFORMATION_ENABLED, "true").toBoolean()
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
        files.flatMapTo(this) { it.licenseInfoInFiles }
        packages.flatMapTo(this) { it.licenseInfoFromFiles }
    }

    return buildSet {
        licenses.forEach { license ->
            SpdxExpression.parse(license, SpdxExpression.Strictness.ALLOW_ANY).getLicenseRefExceptions(this)
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
