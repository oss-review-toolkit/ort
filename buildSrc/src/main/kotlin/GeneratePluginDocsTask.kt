/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import groovy.json.JsonSlurper

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class GeneratePluginDocsTask : DefaultTask() {
    init {
        group = "documentation"
        description = "Generate documentation for the plugins."
    }

    @get:InputFiles
    abstract var inputFiles: ConfigurableFileTree

    @OutputDirectory
    val outputDirectory = project.layout.projectDirectory.file("website/docs/plugins").asFile

    @Internal
    val jsonSlurper = JsonSlurper()

    @TaskAction
    fun generatePluginDocs() {
        logger.lifecycle("Generating plugin documentation.")
        logger.lifecycle("Found a total of ${inputFiles.count()} plugins.")

        generatePluginDocs("advisors")
        generatePluginDocs("package-configuration-providers")
        generatePluginDocs("package-curation-providers")
        generatePluginDocs("package-managers")
        generatePluginDocs("reporters")
        generatePluginDocs("scanners")
    }

    private fun generatePluginDocs(pluginType: String) {
        val plugins = inputFiles.filter { it.invariantSeparatorsPath.contains("plugins/$pluginType") }
        val dir = outputDirectory.resolve(pluginType).also { it.mkdirs() }

        logger.lifecycle("Found ${plugins.count()} ${pluginType.replace('-', ' ')} plugins.")

        plugins.forEach { file ->
            val json = checkNotNull(jsonSlurper.parse(file) as? Map<*, *>)
            val descriptor = checkNotNull(json["descriptor"] as? Map<*, *>)

            val outputFile = dir.resolve("${descriptor["displayName"]}.md")

            val markdown = buildString {
                // Write header.
                appendLine("# ${descriptor["displayName"]}")
                appendLine()
                appendLine("![${descriptor["id"]}](https://img.shields.io/badge/Plugin_ID-${descriptor["id"]}-gold)")
                appendLine()
                appendLine(descriptor["description"])
                appendLine()

                val allOptions = ((descriptor["options"]) as List<*>).map { it as Map<*, *> }

                if (allOptions.isEmpty()) return@buildString

                val options = allOptions.filter { it["type"] != "SECRET" }
                val secrets = allOptions.filter { it["type"] == "SECRET" }

                appendLine("## Configuration options")
                appendLine()

                // Write example configuration.
                appendLine("### Example configuration")
                appendLine()
                appendLine("```json")
                appendLine("{")
                appendLine("  \"${descriptor["id"]}\": {")

                if (options.isNotEmpty()) {
                    appendLine("    \"options\": {")

                    options.forEachIndexed { index, option ->
                        val defaultValue = option["default"]
                        val type = option["type"]
                        val isStringValue =
                            (type == "SECRET" || type == "STRING" || type == "STRING_LIST") && defaultValue != null

                        append("      \"${option["name"]}\": ")
                        if (isStringValue) append("\"")
                        append(defaultValue)
                        if (isStringValue) append("\"")
                        if (index < options.size - 1) append(",")
                        appendLine()
                    }

                    append("    }")
                    if (secrets.isNotEmpty()) append(",")
                    appendLine()
                }

                if (secrets.isNotEmpty()) {
                    appendLine("    \"secrets\": {")

                    secrets.forEachIndexed { index, option ->
                        val defaultValue = option["default"]
                        val type = option["type"]
                        val isStringValue =
                            (type == "SECRET" || type == "STRING" || type == "STRING_LIST") && defaultValue != null

                        append("      \"${option["name"]}\": ")
                        if (isStringValue) append("\"")
                        append(defaultValue)
                        if (isStringValue) append("\"")
                        if (index < secrets.size - 1) append(",")
                        appendLine()
                    }

                    appendLine("    }")
                }

                appendLine("  }")
                appendLine("}")
                appendLine("```")

                // Write option descriptions.
                allOptions.forEach { option ->
                    val type = option["type"]

                    appendLine("### ${option["name"]}")
                    appendLine()
                    appendLine("![$type](https://img.shields.io/badge/Type-$type-blue)")

                    if (option["isRequired"] == "true") {
                        appendLine("![Required](https://img.shields.io/badge/Required-orange)")
                    }

                    option["default"]?.let {
                        appendLine("![Default](https://img.shields.io/badge/Default-$it-darkgreen)")
                    }

                    appendLine()
                    appendLine(option["description"])
                }
            }

            logger.lifecycle("Writing docs for ${descriptor["id"]} to ${outputFile.absolutePath}.")
            outputFile.writeText(markdown)
        }
    }
}
