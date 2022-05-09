/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.readValue

import java.io.File

import org.ossreviewtoolkit.utils.common.safeMkdirs

/**
 * An enumeration of supported file formats for (de-)serialization, their primary [fileExtension] and optional aliases
 * (not including the dot).
 */
enum class FileFormat(val mapper: ObjectMapper, val fileExtension: String, vararg aliases: String) {
    /**
     * Specifies the [JSON](http://www.json.org/) format.
     */
    JSON(jsonMapper, "json"),

    /**
     * Specifies the [XML](http://www.xml.org/) format.
     */
    XML(xmlMapper, "xml"),

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

        /**
         * Return a list of all files inside [directory] with known extensions that can be deserialized.
         */
        fun findFilesWithKnownExtensions(directory: File): List<File> {
            val allFileExtensions = enumValues<FileFormat>().flatMap { it.fileExtensions }
            return directory.walkBottomUp().filter { it.isFile && it.extension in allFileExtensions }.toList()
        }
    }

    /**
     * The list of file extensions used by this file format.
     */
    val fileExtensions = listOf(fileExtension, *aliases)
}

/**
 * Get the Jackson [ObjectMapper] for this file based on the file extension configured in [FileFormat.mapper].
 *
 * @throws IllegalArgumentException If no matching [FileFormat] for the [File.extension] can be found.
 */
fun File.mapper() = FileFormat.forFile(this).mapper

/**
 * Use the Jackson mapper returned from [File.mapper] to read a tree of [(JSON) nodes][JsonNode] from this file.
 */
fun File.readTree(): JsonNode = mapper().readTree(this)

/**
 * Use the Jackson mapper returned from [File.mapper] to read an object of type [T] from this file.
 */
inline fun <reified T : Any> File.readValue(): T = mapper().readValue(this)

/**
 * Use the Jackson mapper returned from [File.mapper] to read an object of type [T] from this file, or return null if
 * the file has no content.
 */
inline fun <reified T : Any> File.readValueOrNull(): T? =
    // Parse the file in a two-step process to avoid readValue() throwing an exception on empty files. Also see
    // https://github.com/FasterXML/jackson-databind/issues/1406#issuecomment-252676674.
    mapper().let { it.convertValue(it.readTree(this)) }

/**
 * Use the Jackson mapper returned from [File.mapper] to read an object of type [T] from this file, or return the
 * [default] value if the file has no content.
 */
inline fun <reified T : Any> File.readValueOrDefault(default: T): T = readValueOrNull() ?: default

/**
 * Use the Jackson mapper returned from [File.mapper] to write an object of type [T] to this file. [prettyPrint]
 * indicates whether to use pretty printing or not. The function also ensures that the parent directory exists.
 */
inline fun <reified T : Any> File.writeValue(value: T, prettyPrint: Boolean = true) {
    parentFile.safeMkdirs()

    if (prettyPrint) {
        mapper().writerWithDefaultPrettyPrinter().writeValue(this, value)
    } else {
        mapper().writeValue(this, value)
    }
}
