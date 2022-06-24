/*
 * Copyright (C) 2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.reporter.reporters.spdx

import java.io.File

import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
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
 * - *creationInfo.comment*: Add the corresponding value as metadata to the [SpdxDocument].
 * - *document.comment*: Add the corresponding value as metadata to the [SpdxDocument].
 * - *document.name*: The name of the generated [SpdxDocument], defaults to "Unnamed document".
 * - *output.file.formats*: The list of [FileFormat]s to generate, defaults to [FileFormat.YAML].
 */
class SpdxDocumentReporter : Reporter {
    companion object {
        const val REPORT_BASE_FILENAME = "bom.spdx"

        const val OPTION_CREATION_INFO_COMMENT = "creationInfo.comment"
        const val OPTION_DOCUMENT_COMMENT = "document.comment"
        const val OPTION_DOCUMENT_NAME = "document.name"
        const val OPTION_OUTPUT_FILE_FORMATS = "output.file.formats"

        private const val DOCUMENT_NAME_DEFAULT_VALUE = "Unnamed document"
    }

    override val reporterName = "SpdxDocument"

    override fun generateReport(
        input: ReporterInput,
        outputDir: File,
        options: Map<String, String>
    ): List<File> {
        val outputFileFormats = options[OPTION_OUTPUT_FILE_FORMATS]
            ?.split(',')
            ?.mapTo(mutableSetOf()) { FileFormat.valueOf(it.uppercase()) }
            ?: setOf(FileFormat.YAML)

        val params = SpdxDocumentModelMapper.SpdxDocumentParams(
            documentName = options.getOrDefault(OPTION_DOCUMENT_NAME, DOCUMENT_NAME_DEFAULT_VALUE),
            documentComment = options.getOrDefault(OPTION_DOCUMENT_COMMENT, ""),
            creationInfoComment = options.getOrDefault(OPTION_CREATION_INFO_COMMENT, "")
        )

        val spdxDocument = SpdxDocumentModelMapper.map(
            input.ortResult,
            input.licenseInfoResolver,
            input.licenseTextProvider,
            params
        )

        return outputFileFormats.map { fileFormat ->
            val serializedDocument = fileFormat.mapper.writeValueAsString(spdxDocument)

            outputDir.resolve("$REPORT_BASE_FILENAME.${fileFormat.fileExtension}").apply {
                bufferedWriter().use { it.write(serializedDocument) }
            }
        }
    }
}
