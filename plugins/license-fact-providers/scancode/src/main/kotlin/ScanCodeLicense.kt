/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.licensefactproviders.scancode

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlNamingStrategy

import java.io.File

import kotlin.String

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
internal data class ScanCodeLicense(
    val category: String,
    val faqUrl: String? = null,
    val homepageUrl: String? = null,
    val ignorableAuthors: Set<String> = emptySet(),
    val ignorableCopyrights: Set<String> = emptySet(),
    val ignorableEmails: Set<String> = emptySet(),
    val ignorableHolders: Set<String> = emptySet(),
    val ignorableUrls: Set<String> = emptySet(),
    @Serializable(YesNoBooleanSerializer::class)
    val isDeprecated: Boolean = false,
    @Serializable(YesNoBooleanSerializer::class)
    val isException: Boolean = false,
    @Serializable(YesNoBooleanSerializer::class)
    val isGeneric: Boolean = false,
    @Serializable(YesNoBooleanSerializer::class)
    val isUnknown: Boolean = false,
    val key: String,
    val language: String? = null,
    val minimumCoverage: Int? = null,
    val name: String,
    val notes: String? = null,
    val osiLicenseKey: String? = null,
    val osiUrl: String? = null,
    val otherSpdxLicenseKeys: Set<String> = emptySet(),
    val otherUrls: Set<String> = emptySet(),
    val owner: String? = null,
    val replacedBy: Set<String> = emptySet(),
    val shortName: String,
    val spdxLicenseKey: String? = null,
    val standardNotice: String? = null,
    val textUrls: Set<String> = emptySet(),
    // The license text is not part of the YAML part of the license data file.
    val text: String? = null
) {
    init {
        require(text == null || text.isNotBlank()) {
            "The license text must not be blank."
        }
    }
}

internal fun parseScanCodeLicenseDataFile(file: File): ScanCodeLicense? {
    require(file.extension == "LICENSE") {
        "The function works only with '.LICENSE' files, but got '.${file.extension}'."
    }

    val lines = file.readLines()

    val markerIndexes = lines.mapIndexedNotNull { index, string ->
        index.takeIf { string == "---" }
    }

    if (markerIndexes.size < 2 || markerIndexes.first() != 0) return null

    val yamlEndIndex = markerIndexes[1]
    val yaml = lines.subList(1, yamlEndIndex).joinToString("\n")
    val text = lines.subList(yamlEndIndex + 1, lines.size)
        .joinToString("\n")
        .trim { it == '\n' }
        .takeUnless { it.isBlank() }

    return YAML.decodeFromString<ScanCodeLicense>(yaml).copy(text = text)
}

private val YAML = Yaml(
    configuration = YamlConfiguration(
        strictMode = false,
        yamlNamingStrategy = YamlNamingStrategy.SnakeCase
    )
)

private object YesNoBooleanSerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("YesNoBoolean", PrimitiveKind.BOOLEAN)

    override fun serialize(encoder: Encoder, value: Boolean) {
        encoder.encodeBoolean(value)
    }

    override fun deserialize(decoder: Decoder): Boolean =
        when (decoder.decodeString().lowercase()) {
            "yes" -> true
            "no" -> false
            else -> error("Expected YAML scalar 'yes' or 'no'.")
        }
}
