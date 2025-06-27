/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.model

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.node.MissingNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

import org.yaml.snakeyaml.LoaderOptions

val PROPERTY_NAMING_STRATEGY = PropertyNamingStrategies.SNAKE_CASE as PropertyNamingStrategies.NamingBase

/**
 * A lambda expression that can be [applied][apply] to all [ObjectMapper]s to configure them the same way.
 */
val mapperConfig: ObjectMapper.() -> Unit = {
    registerKotlinModule()

    registerModule(JavaTimeModule())
    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    propertyNamingStrategy = PROPERTY_NAMING_STRATEGY
}

val jsonMapper = JsonMapper().apply(mapperConfig)

val yamlMapper: YAMLMapper = YAMLMapper(
    YAMLFactory.builder()
        .loaderOptions(
            LoaderOptions().apply {
                // Set the code point limit to the maximum possible value which is approximately 2GB, required since
                // SnakeYAML 1.32. Also see:
                //
                // https://github.com/FasterXML/jackson-dataformats-text/tree/2.15/yaml#maximum-input-yaml-document-size-3-mb
                // https://github.com/FasterXML/jackson-dataformats-text/issues/337
                //
                // TODO: Consider making this configurable.
                codePointLimit = Int.MAX_VALUE
            }
        ).build()
).apply(mapperConfig).enable(YAMLGenerator.Feature.ALLOW_LONG_KEYS)

val EMPTY_JSON_NODE: JsonNode = MissingNode.getInstance()

inline fun <reified T> String.fromYaml(): T = yamlMapper.readValue(this)

fun Any?.toYaml(): String = yamlMapper.writeValueAsString(this)

inline fun <reified T> String.fromJson(): T = jsonMapper.readValue(this)

fun Any?.toJson(prettyPrint: Boolean = true): String {
    val writer = if (prettyPrint) {
        jsonMapper.writerWithDefaultPrettyPrinter()
    } else {
        jsonMapper.writer()
    }

    return writer.writeValueAsString(this)
}
