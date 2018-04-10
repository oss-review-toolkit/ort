/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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
import java.io.File

/**
 * An enumeration of supported output file formats and their [fileExtension] (not including the dot).
 */
enum class OutputFormat(val fileExtension: String, val mapper: ObjectMapper) {
    /**
     * Specifies the [JSON](http://www.json.org/) format.
     */
    JSON("json", jsonMapper),

    /**
     * Specifies the [YAML](http://yaml.org/) format.
     */
    YAML("yml", yamlMapper);

    companion object {
        /**
         * The list of all available output formats.
         */
        @JvmField
        val ALL = OutputFormat.values().asList()
    }
}

/**
 * Get the Jackson [ObjectMapper] for this file based on the file extension configured in [OutputFormat.mapper].
 *
 * @throws IllegalArgumentException If no matching OutputFormat for the [File.extension] can be found.
 */
fun File.mapper(): ObjectMapper {
    OutputFormat.values().forEach {
        if (extension == it.fileExtension) {
            return it.mapper
        }
    }
    throw IllegalArgumentException(
            "No matching ObjectMapper found for file extension '$extension' of file '$absolutePath'."
    )
}
