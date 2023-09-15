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

import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator

import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file

import java.io.File

import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.plugins.commands.api.OrtCommand
import org.ossreviewtoolkit.plugins.packagecurationproviders.ortconfig.toCurationPath
import org.ossreviewtoolkit.plugins.packagemanagers.nuget.utils.getIdentifierWithNamespace
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.common.getCommonParentFile
import org.ossreviewtoolkit.utils.common.safeMkdirs

class MigrateCommand : OrtCommand(
    name = "migrate",
    help = "Assist with migrating ORT configuration to newer ORT versions."
) {
    private val hoconToYaml by option(
        "--hocon-to-yaml",
        help = "Perform a simple conversion of the given HOCON configuration file to YAML and print the result."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)

    private val nuGetIds by option(
        "--nuget-ids",
        help = "Convert NuGet package IDs in curations and configurations to the new format that includes a namespace."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = false, canBeDir = true, mustBeWritable = true, mustBeReadable = true)

    override fun run() {
        hoconToYaml?.run {
            echo(convertHoconToYaml(readText()))
        }

        nuGetIds?.run {
            migrateNuGetIds(this)
        }
    }

    private fun migrateNuGetIds(configDir: File) {
        val configYamlMapper = yamlMapper.copy()
            .enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE)
            .disable(YAMLGenerator.Feature.SPLIT_LINES)
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)

        val candidateFiles = FileFormat.findFilesWithKnownExtensions(configDir).toMutableList()

        val pkgCurationFiles = candidateFiles.mapNotNull { file ->
            runCatching {
                file.readValue<List<PackageCuration>>()
            }.getOrNull()?.let {
                file to it
            }
        }.toMap()

        val curationsDir = getCommonParentFile(pkgCurationFiles.keys)
        candidateFiles -= pkgCurationFiles.keys

        val pkgConfigFiles = candidateFiles.mapNotNull { file ->
            runCatching {
                file.readValue<PackageConfiguration>()
            }.getOrNull()?.let {
                file to it
            }
        }.toMap()

        candidateFiles -= pkgConfigFiles.keys
        echo("Skipping ${candidateFiles.size} files of unknown format.")

        echo("Processing ${pkgCurationFiles.size} package curation files...")
        pkgCurationFiles.forEach { (file, curations) ->
            val curationsWithFixedIds = curations.map {
                if (it.id.type == "NuGet") {
                    it.copy(id = getIdentifierWithNamespace(it.id.type, it.id.name, it.id.version))
                } else {
                    it
                }
            }

            if (curationsWithFixedIds != curations) {
                val oldPath = file.relativeTo(curationsDir).path
                val newPath = curationsWithFixedIds.first().id.toCurationPath()

                // TODO: Maybe make this optional to support layouts that do not follow ort-config conventions.
                if (newPath != oldPath) {
                    curationsDir.resolve(oldPath).delete()
                }

                val newFile = curationsDir.resolve(newPath).apply { parentFile.safeMkdirs() }
                configYamlMapper.writeValue(newFile, curationsWithFixedIds)
            }
        }

        echo("Processing ${pkgConfigFiles.size} package configuration files...")
        pkgConfigFiles.forEach { (file, config) ->
            val configWithFixedId = if (config.id.type == "NuGet") {
                config.copy(id = getIdentifierWithNamespace(config.id.type, config.id.name, config.id.version))
            } else {
                config
            }

            if (configWithFixedId != config) {
                configYamlMapper.writeValue(file, configWithFixedId)
            }
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
