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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.module.kotlin.readValue

import java.io.File

import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.plugins.packagemanagers.pub.PackageInfo.Description

internal fun parseLockfile(lockfile: File) = yamlMapper.readValue<Lockfile>(lockfile)

/**
 * See https://github.com/dart-lang/pub/blob/d86e3c979a3889fed61b68dae9f9156d0891704d/lib/src/lock_file.dart#L18.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class Lockfile(
    val packages: Map<String, PackageInfo> = emptyMap()
)

/**
 * See https://github.com/dart-lang/pub/blob/d86e3c979a3889fed61b68dae9f9156d0891704d/lib/src/package_name.dart#L73.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class PackageInfo(
    val dependency: String,
    @JsonDeserialize(using = DescriptionDeserializer::class)
    val description: Description,
    val source: String? = null,
    val version: String? = null
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Description(
        val name: String? = null,
        val url: String? = null,
        val path: String? = null,
        @JsonProperty("resolved-ref")
        val resolvedRef: String? = null,
        val relative: Boolean? = null,
        val sha256: String? = null
    )
}

internal class DescriptionDeserializer : StdDeserializer<Description>(Description::class.java) {
    override fun deserialize(parser: JsonParser, context: DeserializationContext): Description {
        val node = context.readTree(parser)
        return if (node.isTextual) {
            Description(name = node.textValue())
        } else {
            parser.codec.readValue(node.traverse(), Description::class.java)
        }
    }
}
