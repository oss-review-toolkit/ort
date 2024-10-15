/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.pub

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlInput
import com.charleskorn.kaml.YamlScalar

import java.io.File

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder

import org.ossreviewtoolkit.plugins.packagemanagers.pub.PackageInfo.Description

private val YAML = Yaml(configuration = YamlConfiguration(strictMode = false))

internal fun parseLockfile(lockfile: File) = YAML.decodeFromString<Lockfile>(lockfile.readText())

/**
 * See https://github.com/dart-lang/pub/blob/d86e3c979a3889fed61b68dae9f9156d0891704d/lib/src/lock_file.dart#L18.
 */
@Serializable
internal data class Lockfile(
    val packages: Map<String, PackageInfo> = emptyMap()
)

/**
 * See https://github.com/dart-lang/pub/blob/d86e3c979a3889fed61b68dae9f9156d0891704d/lib/src/package_name.dart#L73.
 */
@Serializable
internal data class PackageInfo(
    val dependency: String,
    val description: Description,
    val source: String? = null,
    val version: String? = null
) {
    @KeepGeneratedSerializer
    @Serializable(DescriptionDeserializer::class)
    data class Description(
        val name: String? = null,
        val url: String? = null,
        val path: String? = null,
        @SerialName("resolved-ref")
        val resolvedRef: String? = null,
        val relative: Boolean? = null,
        val sha256: String? = null
    )
}

private object DescriptionDeserializer : KSerializer<Description> by Description.generatedSerializer() {
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor by lazy {
        val serialName = checkNotNull(Description::class.qualifiedName)

        buildSerialDescriptor(serialName, SerialKind.CONTEXTUAL) {
            element("object", Description.generatedSerializer().descriptor)
            element("string", PrimitiveSerialDescriptor("description", PrimitiveKind.STRING))
        }
    }

    override fun deserialize(decoder: Decoder): Description {
        val input = decoder.beginStructure(descriptor) as YamlInput

        val result = when (val node = input.node) {
            is YamlScalar -> Description(name = node.content)
            else -> Description.generatedSerializer().deserialize(decoder)
        }

        input.endStructure(descriptor)

        return result
    }
}
