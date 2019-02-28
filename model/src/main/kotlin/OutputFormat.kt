/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.model

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

import java.io.File

/**
 * An enumeration of supported output file formats, their primary [fileExtension], and optional aliases (not including
 * the dot).
 */
enum class OutputFormat(val mapper: ObjectMapper, val fileExtension: String, vararg aliases: String) {
    /**
     * Specifies the [JSON](http://www.json.org/) format.
     */
    JSON(jsonMapper, "json"),

    /**
     * Specifies the [YAML](http://yaml.org/) format.
     */
    YAML(yamlMapper, "yml", "yaml");

    val fileExtensions = listOf(fileExtension, *aliases)
}

/**
 * Get the Jackson [ObjectMapper] for this file based on the file extension configured in [OutputFormat.mapper].
 *
 * @throws IllegalArgumentException If no matching OutputFormat for the [File.extension] can be found.
 */
fun File.mapper() =
        OutputFormat.values().find { extension in it.fileExtensions }?.mapper ?: throw IllegalArgumentException(
                "No matching ObjectMapper found for file extension '$extension' of file '$absolutePath'."
        )

/**
 * Use the Jackson mapper returned from [File.mapper] to read an object of type [T] from this file.
 */
inline fun <reified T : Any> File.readValue(): T = mapper().readValue(this)
