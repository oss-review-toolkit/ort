/*
 * Copyright (C) 2020 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import java.io.File

import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.cfg.MapperBuilder
import tools.jackson.databind.json.JsonMapper
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.module.kotlin.kotlinModule
import tools.jackson.module.kotlin.readValue

object SpdxModelMapper {
    /**
     * An enumeration of supported file formats for (de-)serialization, their primary [fileExtension] and optional
     * aliases (not including the dot).
     */
    enum class FileFormat(val mapper: ObjectMapper, val fileExtension: String, vararg aliases: String) {
        /**
         * Specifies the [JSON](http://www.json.org/) format.
         */
        JSON(jsonMapper, "json"),

        /**
         * Specifies the [YAML](http://yaml.org/) format.
         */
        YAML(yamlMapper, "yml", "yaml");

        companion object {
            /**
             * Return the [FileFormat] for the given [extension], or `null` if there is none.
             */
            fun forExtension(extension: String): FileFormat =
                extension.lowercase().let { lowerCaseExtension ->
                    FileFormat.entries.find {
                        lowerCaseExtension in it.fileExtensions
                    } ?: throw IllegalArgumentException(
                        "Unknown file format for file extension '$extension'."
                    )
                }

            /**
             * Return the [FileFormat] for the given [file], or `null` if there is none.
             */
            fun forFile(file: File): FileFormat = forExtension(file.extension)
        }

        /**
         * The list of file extensions used by this file format.
         */
        val fileExtensions = listOf(fileExtension, *aliases)
    }

    inline fun <reified T : Any> read(file: File): T = FileFormat.forFile(file).mapper.readValue(file)

    inline fun <reified T : Any> write(file: File, value: T) = FileFormat.forFile(file).mapper.writeValue(file, value)

    /*
     * JSON mapping functions.
     */

    @PublishedApi
    internal val jsonMapper: JsonMapper = JsonMapper.builder().apply(mapperConfig).build()

    inline fun <reified T : Any> fromJson(json: String): T = jsonMapper.readValue(json)

    fun toJson(value: Any): String = jsonMapper.writeValueAsString(value)

    /*
     * YAML mapping functions.
     */

    @PublishedApi
    internal val yamlMapper: YAMLMapper = YAMLMapper.builder().apply(mapperConfig).build()

    inline fun <reified T : Any> fromYaml(yaml: String): T = yamlMapper.readValue(yaml)

    fun toYaml(value: Any): String = yamlMapper.writeValueAsString(value)
}

val mapperConfig: MapperBuilder<*, *>.() -> Unit = {
    addModule(kotlinModule())
    disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
    enable(SerializationFeature.INDENT_OUTPUT)
}
