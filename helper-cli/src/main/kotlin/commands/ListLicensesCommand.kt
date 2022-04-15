/*
 * Copyright (C) 2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.helper.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file

import java.io.File
import java.lang.IllegalArgumentException
import java.nio.file.FileSystems
import java.nio.file.Paths

import org.ossreviewtoolkit.helper.common.PackageConfigurationOption
import org.ossreviewtoolkit.helper.common.createProvider
import org.ossreviewtoolkit.helper.common.fetchScannedSources
import org.ossreviewtoolkit.helper.common.getLicenseFindingsById
import org.ossreviewtoolkit.helper.common.getPackageOrProject
import org.ossreviewtoolkit.helper.common.getViolatedRulesByLicense
import org.ossreviewtoolkit.helper.common.readOrtResult
import org.ossreviewtoolkit.helper.common.replaceConfig
import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.spdx.SpdxSingleLicenseExpression

internal class ListLicensesCommand : CliktCommand(
    help = "Lists the license findings for a given package as distinct text locations."
) {
    private val ortFile by option(
        "--ort-file", "-i",
        help = "The ORT result file to read as input."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val packageId by option(
        "--package-id",
        help = "The target package for which the licenses shall be listed."
    ).convert { Identifier(it) }
        .required()

    private val sourceCodeDir by option(
        "--source-code-dir",
        help = "A directory containing the sources for the target package. These sources should match the provenance " +
                "of the respective scan result in the ORT result. If not specified those sources are downloaded if " +
                "needed."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }

    private val offendingOnly by option(
        "--offending-only",
        help = "Only list licenses causing a rule violation of severity specified severity, see --severity."
    ).flag()

    private val offendingSeverity by option(
        "--offending-severity",
        help = "Set the severities to use filtering enabled by --offending-only, specified as comma-separated " +
                "values."
    ).enum<Severity>().split(",").default(enumValues<Severity>().asList())

    private val omitExcluded by option(
        "--omit-excluded",
        help = "Only list license findings for non-excluded file locations."
    ).flag()

    private val ignoreExcludedRuleIds by option(
        "--ignore-excluded-rule-ids",
        help = "A comma separated list of rule names for which --omit-excluded should not have any effect."
    ).split(",").default(emptyList())

    private val noLicenseTexts by option(
        "--no-license-texts",
        help = "Do not output the actual file content of file locations of license findings."
    ).flag()

    private val applyLicenseFindingCurations by option(
        "--apply-license-finding-curations",
        help = "Apply the license finding curations contained in the ORT result."
    ).flag()

    private val decomposeLicenseExpressions by option(
        "--decompose-license-expressions",
        help = "Decompose SPDX license expressions into its single licenses components and list the findings for " +
                "each single license separately."
    ).flag()

    private val repositoryConfigurationFile by option(
        "--repository-configuration-file",
        help = "Override the repository configuration contained in the ORT result."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }

    private val packageConfigurationOption by mutuallyExclusiveOptions(
        option(
            "--package-configuration-dir",
            help = "The directory containing the package configuration files to read as input. It is searched " +
                    "recursively."
        ).convert { it.expandTilde() }
            .file(mustExist = true, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = true)
            .convert { PackageConfigurationOption.Dir(it) },
        option(
            "--package-configuration-file",
            help = "The file containing the package configurations to read as input."
        ).convert { it.expandTilde() }
            .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
            .convert { PackageConfigurationOption.File(it) }
    ).single()

    private val licenseAllowlist by option(
        "--license-allow-list",
        help = "Output only license findings which are contained in the given allow list."
    ).split(",")
        .default(emptyList())

    private val fileAllowList by option(
        "--file-allow-list",
        help = "Output only license findings for files whose paths matches any of the given glob expressions."
    ).convert { csv ->
        csv.split(',').map { pattern -> FileSystems.getDefault().getPathMatcher("glob:$pattern") }
    }.default(emptyList())

    override fun run() {
        val ortResult = readOrtResult(ortFile).replaceConfig(repositoryConfigurationFile)

        if (ortResult.getPackageOrProject(packageId) == null) {
            throw UsageError("Could not find the package for the given id '${packageId.toCoordinates()}'.")
        }

        val sourcesDir = sourceCodeDir ?: run {
            println("Downloading sources for package '${packageId.toCoordinates()}'...")
            ortResult.fetchScannedSources(packageId)
        }

        val packageConfigurationProvider = packageConfigurationOption.createProvider()

        fun isPathExcluded(provenance: Provenance, path: String): Boolean =
            if (ortResult.isProject(packageId)) {
                ortResult.getExcludes().paths
            } else {
                packageConfigurationProvider.getPackageConfiguration(packageId, provenance)?.pathExcludes.orEmpty()
            }.any { it.matches(path) }

        val violatedRulesByLicense = ortResult.getViolatedRulesByLicense(packageId, offendingSeverity)

        val findingsByProvenance = ortResult
            .getLicenseFindingsById(
                packageId,
                packageConfigurationProvider,
                applyLicenseFindingCurations,
                decomposeLicenseExpressions
            )
            .mapValues { (provenance, locationsByLicense) ->
                locationsByLicense.filter { (license, _) ->
                    !offendingOnly || license in violatedRulesByLicense
                }.mapValues { (license, locations) ->
                    locations.filter { location ->
                        val isAllowedFile = fileAllowList.isEmpty() || fileAllowList.any {
                            it.matches(Paths.get(location.path))
                        }

                        val isIncluded = !omitExcluded || !isPathExcluded(provenance, location.path) ||
                                ignoreExcludedRuleIds.intersect(violatedRulesByLicense[license].orEmpty()).isNotEmpty()

                        isAllowedFile && isIncluded
                    }
                }.mapValues { (_, locations) ->
                    locations.groupByText(sourcesDir)
                }.filter { (_, locations) ->
                    locations.isNotEmpty()
                }.filter { (license, _) ->
                    licenseAllowlist.isEmpty() || license.simpleLicense() in licenseAllowlist
                }
            }

        buildString {
            appendLine("  scan results:")
            findingsByProvenance.keys.forEachIndexed { i, provenance ->
                appendLine("    [$i] ${provenance.writeValueAsString()}")
            }
        }.also { println(it) }

        findingsByProvenance.keys.forEachIndexed { i, provenance ->
            findingsByProvenance.getValue(provenance).writeValueAsString(
                isPathExcluded = { path -> isPathExcluded(provenance, path) },
                provenanceIndex = i,
                includeLicenseTexts = !noLicenseTexts
            ).also { println(it) }
        }
    }
}

private data class TextLocationGroup(
    val locations: Set<TextLocation>,
    val text: String? = null
) {
    companion object {
        val COMPARATOR = compareBy<TextLocationGroup>({ it.text == null }, { -it.locations.size })
    }
}

private fun Collection<TextLocationGroup>.assignReferenceNameAndSort(): List<Pair<TextLocationGroup, String>> {
    var i = 0
    return sortedWith(TextLocationGroup.COMPARATOR)
        .map {
            if (it.text != null) {
                Pair(it, "${i++}")
            } else {
                Pair(it, "-")
            }
        }
}

private fun Map<SpdxSingleLicenseExpression, List<TextLocationGroup>>.writeValueAsString(
    isPathExcluded: (String) -> Boolean,
    provenanceIndex: Int,
    includeLicenseTexts: Boolean = true
): String {
    return buildString {
        fun appendLineIndent(value: String, indent: Int) {
            require(indent > 0)
            appendLine(value.replaceIndent(" ".repeat(indent)))
        }

        this@writeValueAsString.forEach { (license, textLocationGroups) ->
            appendLineIndent("$license [$provenanceIndex]:", 2)

            val sortedGroups = textLocationGroups.assignReferenceNameAndSort()
            sortedGroups.forEach { (group, name) ->
                group.locations.forEach {
                    val excludedIndicator = if (isPathExcluded(it.path)) "(-)" else "(+)"
                    appendLineIndent(
                        "[$name] $excludedIndicator ${it.path}:${it.startLine}-${it.endLine}",
                        4
                    )
                }
            }

            if (includeLicenseTexts) {
                sortedGroups.forEach { (group, name) ->
                    if (group.text != null) {
                        appendLineIndent("\n\n[$name]", 4)
                        appendLineIndent("\n\n${group.text}\n", 6)
                    }
                }
            }

            appendLine()
        }
    }
}

private fun Collection<TextLocation>.groupByText(baseDir: File): List<TextLocationGroup> {
    val resolvedLocations = mutableMapOf<String, MutableSet<TextLocation>>()

    forEach { textLocation ->
        textLocation.resolve(baseDir)?.let {
            resolvedLocations.getOrPut(it) { mutableSetOf() } += textLocation
        }
    }

    val unresolvedLocations = (this - resolvedLocations.values.flatten()).toSet()

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

/**
 * Return a representation dedicated for this [ListLicensesCommand] command which is more compact than the
 * representation returned by [Provenance.toString()], does not have any nesting and omits some irrelevant fields.
 */
private fun Provenance.writeValueAsString(): String =
    when (this) {
        is ArtifactProvenance -> "url=${sourceArtifact.url}, hash=${sourceArtifact.hash.value}"
        is RepositoryProvenance -> {
            "type=${vcsInfo.type}, url=${vcsInfo.url}, path=${vcsInfo.path}, revision=$resolvedRevision"
        }
        else -> throw IllegalArgumentException("Provenance must have either a non-null source artifact or VCS info.")
    }
