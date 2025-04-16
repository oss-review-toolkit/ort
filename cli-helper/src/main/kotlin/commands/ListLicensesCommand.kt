/*
 * Copyright (C) 2019 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file

import java.io.File

import org.ossreviewtoolkit.helper.utils.OrtHelperCommand
import org.ossreviewtoolkit.helper.utils.downloadSources
import org.ossreviewtoolkit.helper.utils.getLicenseFindingsById
import org.ossreviewtoolkit.helper.utils.getScannedProvenance
import org.ossreviewtoolkit.helper.utils.getSourceCodeOrigin
import org.ossreviewtoolkit.helper.utils.getViolatedRulesByLicense
import org.ossreviewtoolkit.helper.utils.readOrtResult
import org.ossreviewtoolkit.helper.utils.replaceConfig
import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.dir.DirPackageConfigurationProvider
import org.ossreviewtoolkit.utils.common.FileMatcher
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.spdx.SpdxExpression

internal class ListLicensesCommand : OrtHelperCommand(
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
        help = "Only list licenses causing at least one rule violation with an offending severity, see " +
            "--offending-severities."
    ).flag()

    private val offendingSeverities by option(
        "--offending-severities",
        help = "Set the severities to use for the filtering enabled by --offending-only, specified as " +
            "comma-separated values."
    ).enum<Severity>().split(",").default(Severity.entries)

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

    private val packageConfigurationsDir by option(
        "--package-configurations-dir",
        help = "A directory that is searched recursively for package configuration files. Each file must only " +
            "contain a single package configuration."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = true)

    private val licenseAllowlist by option(
        "--license-allow-list",
        help = "Output only license findings which are contained in the given allow list."
    ).split(",")
        .default(emptyList())

    private val fileAllowList by option(
        "--file-allow-list",
        help = "Output only license findings for files whose paths matches any of the given glob expressions."
    ).split(",")
        .default(emptyList())

    override fun run() {
        val ortResult = readOrtResult(ortFile).replaceConfig(repositoryConfigurationFile)

        if (ortResult.getPackageOrProject(packageId) == null) {
            throw UsageError("Could not find the package for the given id '${packageId.toCoordinates()}'.")
        }

        val sourceCodeOrigin = ortResult.getScannedProvenance(packageId).getSourceCodeOrigin() ?: run {
            println("No scan results available.")
            return
        }

        val sourcesDir = sourceCodeDir ?: run {
            println("Downloading sources for package '${packageId.toCoordinates()}'...")
            ortResult.downloadSources(packageId, sourceCodeOrigin)
        }

        val packageConfigurationProvider = DirPackageConfigurationProvider(packageConfigurationsDir)

        fun isPathExcluded(provenance: Provenance, path: String): Boolean =
            if (ortResult.isProject(packageId)) {
                ortResult.getExcludes().paths
            } else {
                packageConfigurationProvider.getPackageConfigurations(packageId, provenance).flatMap { it.pathExcludes }
            }.any { it.matches(path) }

        val violatedRulesByLicense = ortResult.getViolatedRulesByLicense(packageId, offendingSeverities)

        val findingsByProvenance = ortResult
            .getLicenseFindingsById(
                packageId,
                packageConfigurationProvider,
                applyLicenseFindingCurations,
                decomposeLicenseExpressions
            )
            .mapValues { (provenance, locationsByLicense) ->
                locationsByLicense.filter { (license, _) ->
                    !offendingOnly || license.decompose().any { it in violatedRulesByLicense }
                }.mapValues { (license, locations) ->
                    locations.filter { location ->
                        val isAllowedFile = fileAllowList.isEmpty() || FileMatcher.matches(fileAllowList, location.path)

                        val isIncluded = !omitExcluded || !isPathExcluded(provenance, location.path) ||
                            ignoreExcludedRuleIds.intersect(violatedRulesByLicense[license].orEmpty()).isNotEmpty()

                        isAllowedFile && isIncluded
                    }
                }.mapValues { (_, locations) ->
                    locations.groupByText(sourcesDir)
                }.filter { (_, locations) ->
                    locations.isNotEmpty()
                }.filter { (license, _) ->
                    licenseAllowlist.isEmpty() || license.decompose().any { it.simpleLicense() in licenseAllowlist }
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
) : Comparable<TextLocationGroup> {
    companion object {
        private val COMPARATOR = compareBy<TextLocationGroup>({ it.text == null }, { -it.locations.size })
    }

    override fun compareTo(other: TextLocationGroup) = COMPARATOR.compare(this, other)
}

private fun Collection<TextLocationGroup>.assignReferenceNameAndSort(): List<Pair<TextLocationGroup, String>> {
    var i = 0
    return sorted().map {
        if (it.text != null) {
            Pair(it, "${i++}")
        } else {
            Pair(it, "-")
        }
    }
}

private fun Map<SpdxExpression, List<TextLocationGroup>>.writeValueAsString(
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
