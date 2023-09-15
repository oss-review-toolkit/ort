/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.commands.migrate

import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.plugins.commands.api.OrtCommand
import org.ossreviewtoolkit.utils.common.expandTilde

class MigrateCommand : OrtCommand(
    name = "migrate",
    help = "Assist with migrating ORT configuration to newer ORT versions."
) {
    private val hoconToYaml by option(
        "--hocon-to-yaml",
        help = "Perform a simple conversion of the given HOCON configuration file to YAML and print the result."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)

    override fun run() {
        hoconToYaml?.run {
            echo(convertHoconToYaml(readText()))
        }
    }
}

private val curlyBraceToColonRegex = Regex("""^(\s*\w+)\s*\{$""")
private val equalSignToColonRegex = Regex("""^(\s*\w+)\s*=\s*""")

private fun convertHoconToYaml(hocon: String): String {
    val yamlLines = mutableListOf<String>()

    val hoconLines = hocon.lines().map { it.trimEnd() }
    val i = hoconLines.iterator()

    while (i.hasNext()) {
        var line = i.next()
        val trimmedLine = line.trimStart()

        if (trimmedLine.startsWith("//")) {
            line = line.replaceFirst("//", "#")
        }

        if (line.isEmpty() || trimmedLine.startsWith("#")) {
            yamlLines += line
            continue
        }

        if (trimmedLine.endsWith("}")) continue

        line = line.replace(curlyBraceToColonRegex, "$1:")
        line = line.replace(equalSignToColonRegex, "$1: ")

        line = line.replace("\"", "'")

        yamlLines += line
    }

    return yamlLines.joinToString("\n")
}
