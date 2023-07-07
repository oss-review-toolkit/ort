/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.scanner.storages.utils

import com.fasterxml.jackson.databind.JsonNode

import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.utils.common.textValueOrEmpty

/**
 * Generate scanner details using the given [name] by parsing a raw [result].
 */
internal fun getScanCodeDetails(name: String, result: JsonNode): ScannerDetails {
    val header = result["headers"].single()
    val version = header["tool_version"].textValueOrEmpty()
    val config = getScanCodeOptions(header["options"])
    return ScannerDetails(name, version, config)
}

/**
 * Convert the JSON node with ScanCode [options] to a string that corresponds to the options as they have been passed on
 * the command line.
 */
private fun getScanCodeOptions(options: JsonNode?): String {
    fun addValues(list: MutableList<String>, node: JsonNode, key: String) {
        if (node.isEmpty) {
            list += key
            list += node.asText()
        } else {
            node.forEach {
                list += key
                list += it.asText()
            }
        }
    }

    return options?.let {
        val optionList = mutableListOf<String>()

        it.fieldNames().asSequence().forEach { option ->
            addValues(optionList, it[option], option)
        }

        optionList.joinToString(separator = " ")
    }.orEmpty()
}
