/*
 * Copyright (C) 2019 HERE Europe B.V.
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
import com.here.ort.helper.common.getViolatedRulesByLicense
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
        names = ["--omit-excluded"],
        required = false,
        order = PARAMETER_ORDER_OPTIONAL
    )
    private var omitExcluded: Boolean = false

    @Parameter(
        names = ["--ignore-excluded-rule-ids"],
        required = false,
        order = PARAMETER_ORDER_OPTIONAL
    )
    private var ignoreExcludedRuleIds: List<String> = emptyList()

    @Parameter(
        names = ["--no-license-texts"],
        required = false,
        order = PARAMETER_ORDER_OPTIONAL
    )
    private var noLicenseTexts: Boolean = false

    override fun runCommand(jc: JCommander): Int {
        val ortResult = ortResultFile.readValue<OrtResult>()
        val sourcesDir = sourceCodeDir ?: ortResult.fetchScannedSources(packageId)
        val violatedRulesByLicense = ortResult.getViolatedRulesByLicense(packageId, Severity.ERROR)

        fun isPathExcluded(path: String) = ortResult.repository.config.excludes?.let {
            it.findPathExcludes(path).isNotEmpty()
        } ?: false

        ortResult
            .getLicenseFindingsById(packageId)
            .filter { (license, _) -> !onlyOffending || violatedRulesByLicense.contains(license) }
            .mapValues { (license, locations) ->
                locations.filter {
                    !omitExcluded || !isPathExcluded(it.path) ||
                            ignoreExcludedRuleIds.intersect(violatedRulesByLicense[license].orEmpty()).isNotEmpty()
                }
            }
            .mapValues { it.value.groupByText(sourcesDir) }
            .writeValueAsString(
                isPathExcluded = { path -> isPathExcluded(path) },
                includeLicenseTexts = !noLicenseTexts
            )
            .let { println(it) }

        return 0
    }
}

private data class TextLocationGroup(
    val locations: Set<TextLocation>,
    val text: String? = null
)

private fun Collection<TextLocationGroup>.assignReferenceNameAndSort(): List<Pair<TextLocationGroup, String>> {
    var i = 0
    return sortedWith(compareBy({ it.text == null }, { -it.locations.size }))
        .map {
            if (it.text != null) {
                Pair(it, "${i++}")
            } else {
                Pair(it, "-")
            }
        }
}

private fun Map<String, List<TextLocationGroup>>.writeValueAsString(
    isPathExcluded: (String) -> Boolean,
    includeLicenseTexts: Boolean = true
): String {
    return buildString {
        fun appendlnIndent(value: String, indent: Int) {
            require(indent > 0)
            appendln(value.replaceIndent(" ".repeat(indent)))
        }

        this@writeValueAsString.forEach { (license, textLocationGroups) ->
            appendlnIndent("$license:", 2)

            val sortedGroups = textLocationGroups.assignReferenceNameAndSort()
            sortedGroups.forEach { (group, name) ->
                group.locations.forEach {
                    val excludedIndicator = if (isPathExcluded(it.path)) "(-)" else "(+)"
                    appendlnIndent(
                        "[$name] $excludedIndicator ${it.path}:${it.startLine}-${it.endLine}",
                        4
                    )
                }
            }

            if (includeLicenseTexts) {
                sortedGroups.forEach { (group, name) ->
                    if (group.text != null) {
                        appendlnIndent("\n\n[$name]", 4)
                        appendlnIndent("\n\n${group.text}\n", 6)
                    }
                }
            }

            appendln()
        }
    }
}

private fun Collection<TextLocation>.groupByText(baseDir: File): List<TextLocationGroup> {
    val resolvedLocations = mutableMapOf<String, MutableSet<TextLocation>>()

    forEach { textLocation ->
        textLocation.resolve(baseDir)?.let {
            resolvedLocations.getOrPut(it, { mutableSetOf() }).add(textLocation)
        }
    }

    val unresolvedLocations = (this - resolvedLocations.values.flatten()).distinct()

    return resolvedLocations.map { (text, locations) -> TextLocationGroup(locations = locations, text = text) } +
            unresolvedLocations.map { TextLocationGroup(locations = setOf(it)) }
}

private fun TextLocation.resolve(baseDir: File): String? {
    val file = baseDir.resolve(path)
    if (!file.isFile) return null

    val lines = file.readText().lines()
    if (lines.size <= endLine) return null

    return lines.subList(startLine - 1, endLine).joinToString(separator = "\n")
}
