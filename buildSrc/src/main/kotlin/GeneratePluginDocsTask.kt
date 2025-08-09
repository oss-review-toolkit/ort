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
import org.gradle.api.GradleException
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class GeneratePluginDocsTask : DefaultTask() {
    init {
        group = "Documentation"
        description = "Generate documentation for the plugins."
    }

    @get:InputFiles
    abstract var inputFiles: FileTree

    @OutputDirectory
    val outputDirectory = project.layout.projectDirectory.file("website/docs/plugins").asFile

    @Internal
    val jsonSlurper = JsonSlurper()

    @TaskAction
    fun generatePluginDocs() {
        if (inputFiles.files.isEmpty()) {
            throw GradleException("No plugin descriptions found, make sure to provide input files to this task.")
        }

        logger.lifecycle("Generating plugin documentation.")

        generatePluginDocs("advisors", "advisor")
        generatePluginDocs("license-fact-providers")
        generatePluginDocs("package-configuration-providers")
        generatePluginDocs("package-curation-providers")
        generatePluginDocs("package-managers", "analyzer")
        generatePluginDocs("reporters", "reporter")
        generatePluginDocs("scanners", "scanner")

        logger.lifecycle("Found a total of ${inputFiles.count()} plugins.")
    }

    private fun generatePluginDocs(pluginType: String, tool: String? = null) {
        val plugins = inputFiles.filter { "plugins/$pluginType" in it.invariantSeparatorsPath }
        val dir = outputDirectory.resolve(pluginType).apply { mkdirs() }

        val pluginTypeParts = pluginType.split('-')
        val pluginTypeCamelCase = pluginTypeParts.joinToString("") { it.replaceFirstChar(Char::uppercase) }
            .replaceFirstChar(Char::lowercase)
        logger.lifecycle("Found ${plugins.count()} ${pluginTypeParts.joinToString(" ")}:")

        plugins.sorted().forEach { file ->
            val json = checkNotNull(jsonSlurper.parse(file) as? Map<*, *>)
            val descriptor = checkNotNull(json["descriptor"] as? Map<*, *>)

            val outputFile = dir.resolve("${descriptor["displayName"]}.md")

            val markdown = buildString {
                // Set max heading level so that option sections are visible in the TOC.
                appendLine("---")
                appendLine("toc_max_heading_level: 4")
                appendLine("---")
                appendLine()

                // Write header.
                appendLine("# ${descriptor["displayName"]}")
                appendLine()
                appendLine("![${descriptor["id"]}](https://img.shields.io/badge/Plugin_ID-${descriptor["id"]}-gold)")
                appendLine()
                appendLine(descriptor["description"])
                appendLine()

                val allOptions = ((descriptor["options"]) as List<*>).map { it as Map<*, *> }

                if (allOptions.isEmpty()) return@buildString

                val (options, secrets) = allOptions.partition { it["type"] != "SECRET" }

                appendLine("## Configuration")
                appendLine()

                // Write example configuration.
                appendLine("### Example")
                appendLine()

                fun appendOptionsAndSecrets(startIndent: Int, pluginType: String) {
                    val indent = if (tool != null) {
                        append(" ".repeat(startIndent))
                        appendLine("$tool:")
                        startIndent + 2
                    } else {
                        startIndent
                    }

                    val i = " ".repeat(indent)

                    appendLine("$i$pluginType:")
                    appendLine("$i  ${descriptor["id"]}:")

                    fun appendOptions(options: List<Map<*, *>>) {
                        options.forEach {
                            val defaultValue = it["default"]
                            val type = it["type"]
                            val isStringValue = type == "SECRET" || type == "STRING" || type == "STRING_LIST"

                            append("$i      ${it["name"]}: ")
                            if (defaultValue != null) {
                                if (isStringValue) append("\"")
                                append(defaultValue)
                                if (isStringValue) append("\"")
                            } else {
                                append("<OPTIONAL_$type>")
                            }
                            appendLine()
                        }
                    }

                    if (options.isNotEmpty()) {
                        appendLine("$i    options:")
                        appendOptions(options)
                    }

                    if (secrets.isNotEmpty()) {
                        appendLine("$i    secrets:")
                        appendOptions(secrets)
                    }
                }

                appendLine("Use the following syntax to configure this plugin globally as part of `config.yml`:")
                appendLine()
                appendLine("```yaml")
                appendLine("ort:")
                appendOptionsAndSecrets(2, pluginTypeCamelCase)
                appendLine("```")
                appendLine()

                if (tool == "analyzer") {
                    appendLine("Use the following syntax to configure this plugin in a repository's `.ort.yml`:")
                    appendLine()
                    appendLine("```yaml")

                    // See https://github.com/oss-review-toolkit/ort/issues/5715.
                    appendOptionsAndSecrets(0, pluginTypeParts.joinToString("_"))

                    appendLine("```")
                    appendLine()
                    appendLine(
                        "If the plugin is configured in both locations, the configurations are merged, with options " +
                            "from `.ort.yml` taking precedence over those from `config.yml`."
                    )
                    appendLine()
                }

                appendLine("### Options")
                appendLine()

                // Write option descriptions.
                allOptions.forEach { option ->
                    val type = option["type"]

                    appendLine("#### ${option["name"]}")
                    appendLine()
                    appendLine("![$type](https://img.shields.io/badge/Type-$type-blue)")

                    if (option["isRequired"] == true) {
                        appendLine("![Required](https://img.shields.io/badge/Required-orange)")
                    }

                    option["default"]?.also {
                        appendLine("![Default](https://img.shields.io/badge/Default-$it-darkgreen)")
                    }

                    appendLine()
                    appendLine(option["description"])
                    appendLine()
                }
            }

            logger.lifecycle("Writing docs for ${descriptor["id"]} to ${outputFile.absolutePath}.")
            outputFile.writeText(markdown)
        }
    }
}
