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

package com.here.ort.helper.commands

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters

import com.here.ort.CommandWithHelp
import com.here.ort.helper.common.IdentifierConverter
import com.here.ort.helper.common.fetchScannedSources
import com.here.ort.helper.common.getLicenseFindingsById
import com.here.ort.helper.common.getOffendingLicensesById
import com.here.ort.model.Identifier
import com.here.ort.model.OrtResult
import com.here.ort.model.Severity
import com.here.ort.model.TextLocation
import com.here.ort.model.readValue
import com.here.ort.utils.PARAMETER_ORDER_MANDATORY
import com.here.ort.utils.PARAMETER_ORDER_OPTIONAL

import java.io.File

@Parameters(
    commandNames = ["list-licenses"],
    commandDescription = "Lists the license findings for a given package as distinct text locations."
)
internal class ListLicensesCommand : CommandWithHelp() {
    @Parameter(
        names = ["--ort-result-file"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY
    )
    private lateinit var ortResultFile: File

    @Parameter(
        names = ["--package-id"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY,
        converter = IdentifierConverter::class
    )
    private lateinit var packageId: Identifier

    @Parameter(
        names = ["--source-code-dir"],
        required = false,
        order = PARAMETER_ORDER_OPTIONAL
    )
    private var sourceCodeDir: File? = null

    @Parameter(
        names = ["--only-offending"],
        required = false,
        order = PARAMETER_ORDER_OPTIONAL
    )
    private var onlyOffending: Boolean = false

    @Parameter(
        names = ["--no-license-texts"],
        required = false,
        order = PARAMETER_ORDER_OPTIONAL
    )
    private var noLicenseTexts: Boolean = false

    override fun runCommand(jc: JCommander): Int {
        val ortResult = ortResultFile.readValue<OrtResult>()
        val sourcesDir = sourceCodeDir ?: ortResult.fetchScannedSources(packageId)
        val offendingLicenses = ortResult.getOffendingLicensesById(packageId, Severity.ERROR)

        ortResult
            .getLicenseFindingsById(packageId)
            .filter { !onlyOffending || offendingLicenses.contains(it.key) }
            .groups(sourcesDir)
            .writeValueAsString(includeLicenseTexts = !noLicenseTexts)
            .let { println(it) }

        return 0
    }
}

private data class TextLocationGroup(
    val locations: Set<TextLocation>,
    val text: String? = null
)

private fun Map<String, List<TextLocationGroup>>.writeValueAsString(includeLicenseTexts: Boolean = true): String =
    buildString {
        this@writeValueAsString.forEach { (license, textLocationGroups) ->
            appendln("  $license:")

            textLocationGroups
                .sortedByDescending { it.locations.size }
                .forEachIndexed { i, group ->
                    group.locations.forEach {
                        appendln("    [$i] ${it.path}:${it.startLine}-${it.endLine}")
                    }
                }

            if (includeLicenseTexts) {
                textLocationGroups
                    .sortedByDescending { it.locations.size }
                    .forEachIndexed { i, group ->
                        appendln("$i:\n\n${group.text}")
                    }
            }

            appendln()
        }
    }

private fun Map<String, Collection<TextLocation>>.groups(baseDir: File): Map<String, List<TextLocationGroup>> =
    mapValues { license ->
        license.value.groupByText(baseDir) ?: return mapValues { (_, groups) ->
            groups.map { TextLocationGroup(locations = setOf(it)) }
        }
    }

private fun Collection<TextLocation>.groupByText(baseDir: File): List<TextLocationGroup>? {
    val map = mutableMapOf<String, MutableSet<TextLocation>>()

    forEach { textLocation ->
        val text = textLocation.resolve(baseDir) ?: return null
        map.getOrPut(text, { mutableSetOf() }).add(textLocation)
    }

    return map.map { (text, locations) -> TextLocationGroup(locations = locations, text = text) }
}

private fun TextLocation.resolve(baseDir: File): String? {
    val lines = baseDir.resolve(path).readText().lines()
    return if (lines.size <= endLine) {
        println("Could not resolve: $path:$startLine-$endLine")
        null
    } else {
        lines.subList(startLine - 1, endLine).joinToString(separator = "\n")
    }
}
