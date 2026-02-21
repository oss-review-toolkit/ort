/*
 * Copyright (C) 2017 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.utils.spdxdocument

import java.time.Instant

import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdxdocument.model.SpdxChecksum
import org.ossreviewtoolkit.utils.spdxdocument.model.SpdxCreationInfo
import org.ossreviewtoolkit.utils.spdxdocument.model.SpdxDocument
import org.ossreviewtoolkit.utils.spdxdocument.model.SpdxFile
import org.ossreviewtoolkit.utils.spdxdocument.model.SpdxRelationship

/**
 * A parser for SPDX Tag:Value format.
 *
 * The SPDX Tag:Value format uses key-value pairs with "Tag: Value" syntax.
 * Multi-line values use <text>...</text> delimiters.
 */
object SpdxTagValueParser {
    private enum class BlockType { DOCUMENT, PACKAGE, FILE, SNIPPET, RELATIONSHIP }

    private const val TEXT_BLOCK_START = "<text>"
    private const val TEXT_BLOCK_END = "</text>"

    /**
     * Parse SPDX Tag:Value format output and extract document information.
     */
    fun parse(spdxOutput: String): SpdxDocument {
        var currentBlockType = BlockType.DOCUMENT
        val acc = mutableMapOf<String, MutableList<String>>()

        val documentAcc = mutableMapOf<String, List<String>>()
        val files = mutableListOf<SpdxFile>()
        val relationships = mutableListOf<SpdxRelationship>()

        var inTextBlock = false
        var textBlockKey = ""
        var textBlockContent = StringBuilder()

        for (line in spdxOutput.lines()) {
            when {
                inTextBlock -> {
                    if (line.contains(TEXT_BLOCK_END)) {
                        val endIndex = line.indexOf(TEXT_BLOCK_END)
                        textBlockContent.append(line.substring(0, endIndex))
                        acc.getOrPut(textBlockKey) { mutableListOf() } += textBlockContent.toString().trim()
                        inTextBlock = false
                        textBlockContent = StringBuilder()
                        textBlockKey = ""
                    } else {
                        textBlockContent.appendLine(line)
                    }
                }

                line.startsWith("PackageName:") -> {
                    saveCurrentBlock(currentBlockType, acc, documentAcc, files, relationships)
                    currentBlockType = BlockType.PACKAGE
                    acc.clear()
                    acc["PackageName"] = mutableListOf(extractValue(line))
                }

                line.startsWith("FileName:") -> {
                    saveCurrentBlock(currentBlockType, acc, documentAcc, files, relationships)
                    currentBlockType = BlockType.FILE
                    acc.clear()
                    acc["FileName"] = mutableListOf(extractValue(line))
                }

                line.startsWith("SnippetSPDXID:") -> {
                    saveCurrentBlock(currentBlockType, acc, documentAcc, files, relationships)
                    currentBlockType = BlockType.SNIPPET
                    acc.clear()
                    acc["SnippetSPDXID"] = mutableListOf(extractValue(line))
                }

                line.startsWith("Relationship:") -> {
                    saveCurrentBlock(currentBlockType, acc, documentAcc, files, relationships)
                    currentBlockType = BlockType.RELATIONSHIP
                    acc.clear()
                    acc["Relationship"] = mutableListOf(extractValue(line))
                }

                line.contains(":") -> {
                    val (key, value) = parseTagValue(line)
                    if (value.startsWith(TEXT_BLOCK_START)) {
                        val content = value.removePrefix(TEXT_BLOCK_START)
                        if (content.contains(TEXT_BLOCK_END)) {
                            val endIndex = content.indexOf(TEXT_BLOCK_END)
                            acc.getOrPut(key) { mutableListOf() } += content.substring(0, endIndex).trim()
                        } else {
                            inTextBlock = true
                            textBlockKey = key
                            textBlockContent.append(content)
                            if (content.isNotEmpty()) textBlockContent.appendLine()
                        }
                    } else {
                        acc.getOrPut(key) { mutableListOf() } += value
                    }
                }
            }
        }

        saveCurrentBlock(currentBlockType, acc, documentAcc, files, relationships)

        return buildDocument(documentAcc, files, relationships)
    }

    private fun extractValue(line: String): String {
        val colonIndex = line.indexOf(':')
        return if (colonIndex >= 0) line.substring(colonIndex + 1).trim() else ""
    }

    private fun parseTagValue(line: String): Pair<String, String> {
        val colonIndex = line.indexOf(':')
        return if (colonIndex >= 0) {
            line.substring(0, colonIndex).trim() to line.substring(colonIndex + 1).trim()
        } else {
            "" to line
        }
    }

    private fun saveCurrentBlock(
        blockType: BlockType,
        acc: Map<String, List<String>>,
        documentAcc: MutableMap<String, List<String>>,
        files: MutableList<SpdxFile>,
        relationships: MutableList<SpdxRelationship>
    ) {
        when (blockType) {
            BlockType.DOCUMENT -> documentAcc.putAll(acc)

            BlockType.FILE -> files += buildFile(acc)

            BlockType.RELATIONSHIP -> relationships += parseRelationship(acc.getValue("Relationship").single())

            BlockType.PACKAGE -> TODO("Package parsing not implemented")
            BlockType.SNIPPET -> TODO("Snippet parsing not implemented")
        }
    }

    private fun buildFile(acc: Map<String, List<String>>): SpdxFile {
        val fileName = acc.getValue("FileName").single().removePrefix("./")
        val spdxId = acc.getValue("SPDXID").single()

        return SpdxFile(
            spdxId = spdxId,
            filename = fileName,
            checksums = acc["FileChecksum"]?.map { parseChecksum(it) }.orEmpty(),
            licenseConcluded = acc["LicenseConcluded"]?.firstOrNull() ?: SpdxConstants.NOASSERTION,
            licenseInfoInFiles = acc["LicenseInfoInFile"]
                ?.filter { it.isNotEmpty() }
                ?.ifEmpty { listOf(SpdxConstants.NOASSERTION) }
                ?: listOf(SpdxConstants.NOASSERTION),
            copyrightText = acc["FileCopyrightText"]?.firstOrNull() ?: SpdxConstants.NOASSERTION
        )
    }

    private fun buildDocument(
        acc: Map<String, List<String>>,
        files: List<SpdxFile>,
        relationships: List<SpdxRelationship>
    ): SpdxDocument {
        val created = acc["Created"]?.firstOrNull()?.let {
            runCatching { Instant.parse(it) }.getOrNull()
        } ?: Instant.EPOCH

        val creators = acc["Creator"]
            ?.filter { it.isNotEmpty() }
            ?.ifEmpty { listOf("Tool: unknown") }
            ?: listOf("Tool: unknown")

        return SpdxDocument(
            spdxVersion = acc["SPDXVersion"]?.firstOrNull().orEmpty(),
            dataLicense = acc["DataLicense"]?.firstOrNull().orEmpty(),
            spdxId = acc["SPDXID"]?.firstOrNull().orEmpty(),
            name = acc["DocumentName"]?.firstOrNull().orEmpty(),
            documentNamespace = acc["DocumentNamespace"]?.firstOrNull().orEmpty(),
            creationInfo = SpdxCreationInfo(created = created, creators = creators),
            files = files,
            relationships = relationships
        )
    }

    private fun parseChecksum(value: String): SpdxChecksum {
        val parts = value.split(":", limit = 2)
        require(parts.size == 2) { "Invalid checksum format: '$value'" }

        val algorithmStr = parts[0].trim().uppercase().replace("-", "_")
        val checksumValue = parts[1].trim().lowercase()

        val algorithm = SpdxChecksum.Algorithm.valueOf(algorithmStr)

        return SpdxChecksum(algorithm, checksumValue)
    }

    private fun parseRelationship(value: String): SpdxRelationship {
        val parts = value.split(" ", limit = 3)
        require(parts.size == 3) { "Invalid relationship format: '$value'" }

        val spdxElementId = parts[0].trim()
        val typeStr = parts[1].trim().uppercase().replace("-", "_")
        val relatedSpdxElement = parts[2].trim()

        val type = SpdxRelationship.Type.valueOf(typeStr)

        return SpdxRelationship(spdxElementId, type, relatedSpdxElement)
    }
}
