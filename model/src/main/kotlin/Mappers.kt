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

package org.ossreviewtoolkit.model

import org.snakeyaml.engine.v2.api.LoadSettings

import tools.jackson.databind.PropertyNamingStrategies.SNAKE_CASE
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.cfg.MapperBuilder
import tools.jackson.databind.json.JsonMapper
import tools.jackson.dataformat.yaml.YAMLFactory
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.dataformat.yaml.YAMLWriteFeature
import tools.jackson.module.kotlin.kotlinModule
import tools.jackson.module.kotlin.readValue

/**
 * A lambda expression that can be [applied][apply] to all [MapperBuilder]s to build them the same way.
 */
val mapperConfig: MapperBuilder<*, *>.() -> Unit = {
    addModule(kotlinModule())
    disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
    propertyNamingStrategy(SNAKE_CASE)
}

val jsonMapper: JsonMapper by lazy {
    JsonMapper.builder()
        .apply(mapperConfig)
        .build()
}

val yamlMapper: YAMLMapper by lazy {
    val loadSettings = LoadSettings.builder()
        // Set the code point limit to the maximum possible value which is approximately 2GB, required since
        // SnakeYAML 1.32. Also see:
        //
        // https://github.com/FasterXML/jackson-dataformats-text/tree/2.15/yaml#maximum-input-yaml-document-size-3-mb
        // https://github.com/FasterXML/jackson-dataformats-text/issues/337
        //
        // TODO: Consider making this configurable.
        .setCodePointLimit(Int.MAX_VALUE)
        .build()

    val yamlFactory = YAMLFactory.builder()
        .loadSettings(loadSettings)
        .build()

    YAMLMapper.builder(yamlFactory)
        .apply(mapperConfig)
        .enable(YAMLWriteFeature.ALLOW_LONG_KEYS)
        .build()
}

inline fun <reified T> String.fromJson(): T = jsonMapper.readValue(this)

fun Any?.toJson(prettyPrint: Boolean = true): String {
    val writer = if (prettyPrint) {
        jsonMapper.writerWithDefaultPrettyPrinter()
    } else {
        jsonMapper.writer()
    }

    return writer.writeValueAsString(this)
}

inline fun <reified T> String.fromYaml(): T = yamlMapper.readValue(this)

fun Any?.toYaml(): String = yamlMapper.writeValueAsString(this)
