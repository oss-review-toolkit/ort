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

package org.ossreviewtoolkit.utils.spdx

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

import java.io.File
import java.lang.IllegalArgumentException

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
                    enumValues<FileFormat>().find {
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
    internal val jsonMapper: ObjectMapper = JsonMapper().apply(mapperConfig)

    inline fun <reified T : Any> fromJson(json: String): T = jsonMapper.readValue(json)

    fun toJson(value: Any): String = jsonMapper.writeValueAsString(value)

    /*
     * YAML mapping functions.
     */

    @PublishedApi
    internal val yamlMapper: ObjectMapper = YAMLMapper().apply(mapperConfig)

    inline fun <reified T : Any> fromYaml(yaml: String): T = yamlMapper.readValue(yaml)

    fun toYaml(value: Any): String = yamlMapper.writeValueAsString(value)
}

private val mapperConfig: ObjectMapper.() -> Unit = {
    registerKotlinModule()

    registerModule(JavaTimeModule())
    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    enable(SerializationFeature.INDENT_OUTPUT)
}
