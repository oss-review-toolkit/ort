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

package org.ossreviewtoolkit.plugins.packagemanagers.bower

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.module.kotlin.readValue

import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.plugins.packagemanagers.bower.PackageMeta.Author
import org.ossreviewtoolkit.utils.common.textValueOrEmpty

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class PackageInfo(
    val pkgMeta: PackageMeta,
    val dependencies: Map<String, PackageInfo> = emptyMap()
)

/**
 * See https://github.com/bower/spec/blob/master/json.md.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class PackageMeta(
    val name: String? = null,
    val authors: List<Author> = emptyList(),
    val description: String? = null,
    val license: String? = null,
    val homepage: String? = null,
    val dependencies: Map<String, String> = emptyMap(),
    val devDependencies: Map<String, String> = emptyMap(),
    val version: String? = null,
    @JsonProperty("_resolution")
    val resolution: Resolution? = null,
    val repository: Repository? = null,
    @JsonProperty("_source")
    val source: String?
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Resolution(
        val type: String? = null,
        val tag: String? = null,
        val commit: String? = null
    )

    @JsonDeserialize(using = AuthorDeserializer::class)
    data class Author(
        val name: String,
        val email: String? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Repository(
        val type: String,
        val url: String
    )
}

private val MAPPER = jsonMapper.copy().setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)

internal fun parsePackageInfoJson(json: String): PackageInfo = MAPPER.readValue<PackageInfo>(json)

/**
 * Parse information about the author. According to https://github.com/bower/spec/blob/master/json.md#authors,
 * there are two formats to specify the authors of a package (similar to NPM). The difference is that the
 * strings or objects are inside an array.
 */
private class AuthorDeserializer : StdDeserializer<Author>(Author::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Author {
        val node = p.codec.readTree<JsonNode>(p)
        return when {
            node.isTextual -> Author(node.textValue())
            else -> Author(node["name"].textValueOrEmpty(), node["email"]?.textValue())
        }
    }
}
