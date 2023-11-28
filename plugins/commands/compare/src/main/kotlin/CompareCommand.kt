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

package org.ossreviewtoolkit.plugins.commands.compare

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.readValue

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.mordant.rendering.Theme
import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils

import java.time.Instant

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.Curations
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.IssueResolution
import org.ossreviewtoolkit.model.config.LicenseChoices
import org.ossreviewtoolkit.model.config.LicenseFindingCuration
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.config.PackageLicenseChoice
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.RepositoryAnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.model.config.RuleViolationResolution
import org.ossreviewtoolkit.model.config.ScopeExclude
import org.ossreviewtoolkit.model.config.VulnerabilityResolution
import org.ossreviewtoolkit.model.mapper
import org.ossreviewtoolkit.plugins.commands.api.OrtCommand
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.common.getCommonParentFile
import org.ossreviewtoolkit.utils.ort.Environment
import org.ossreviewtoolkit.utils.spdx.model.SpdxLicenseChoice

class CompareCommand : OrtCommand(
    name = "compare",
    help = "Compare two ORT results with various methods."
) {
    private val fileA by argument(help = "The first ORT result file to compare.")
        .convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }

    private val fileB by argument(help = "The second ORT result file to compare.")
        .convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }

    private val method by option(
        "--method", "-m",
        help = "The method to use when comparing ORT results. Must be one of " +
            "${CompareMethod.entries.map { it.name }}."
    ).enum<CompareMethod>()
        .default(CompareMethod.TEXT_DIFF)

    private val ignoreTime by option(
        "--ignore-time", "-t",
        help = "Ignore time differences."
    ).flag()

    private val ignoreEnvironment by option(
        "--ignore-environment", "-e",
        help = "Ignore environment differences."
    ).flag()

    private val ignoreTmpDir by option(
        "--ignore-tmp-dir", "-d",
        help = "Ignore temporary directory differences."
    ).flag()

    override fun run() {
        if (fileA == fileB) {
            echo("The arguments point to the same file.")
            throw ProgramResult(0)
        }

        if (fileA.extension != fileB.extension) {
            echo("The file arguments need to be of the same type.")
            throw ProgramResult(2)
        }

        val deserializer = fileA.mapper().registerModule(
            SimpleModule().apply {
                // TODO: Find a way to also ignore temporary directories.
                if (ignoreTime) addDeserializer(Instant::class.java, EpochInstantDeserializer())
                if (ignoreEnvironment) addDeserializer(Environment::class.java, DefaultEnvironmentDeserializer())
            }
        )

        val resultA = deserializer.readValue<OrtResult>(fileA)
        val resultB = deserializer.readValue<OrtResult>(fileB)

        when (method) {
            CompareMethod.SEMANTIC_DIFF -> {
                echo(
                    Theme.Default.warning(
                        "The '${CompareMethod.SEMANTIC_DIFF}' compare method is not fully implemented. Some " +
                            "properties may not be taken into account in the comparison."
                    )
                )

                if (resultA == resultB) {
                    echo("The ORT results are the same.")
                    throw ProgramResult(0)
                }

                val diff = resultA.diff(resultB)

                echo(deserializer.writeValueAsString(diff))

                throw ProgramResult(1)
            }

            CompareMethod.TEXT_DIFF -> {
                val textA = deserializer.writeValueAsString(resultA)
                val textB = deserializer.writeValueAsString(resultB)

                // Apply data type independent replacements in the texts.
                val replacements = buildMap {
                    if (ignoreTmpDir) {
                        put("""([/\\][Tt]e?mp[/\\]ort)[/\\-][\w./\\-]+""", "$1")
                    }
                }

                val replacementRegexes = replacements.mapKeys { (pattern, _) -> Regex(pattern, RegexOption.MULTILINE) }

                val linesA = replacementRegexes.replaceIn(textA).lines()
                val linesB = replacementRegexes.replaceIn(textB).lines()

                // Create unified diff output.
                val commonParent = getCommonParentFile(setOf(fileA, fileB))
                val diff = UnifiedDiffUtils.generateUnifiedDiff(
                    "a/${fileA.relativeTo(commonParent).invariantSeparatorsPath}",
                    "b/${fileB.relativeTo(commonParent).invariantSeparatorsPath}",
                    linesA,
                    DiffUtils.diff(linesA, linesB),
                    /* contextSize = */ 7
                )

                if (diff.isEmpty()) {
                    echo("The ORT results are the same.")
                    throw ProgramResult(0)
                }

                echo("The ORT results differ:")

                diff.forEach {
                    echo(it)
                }

                throw ProgramResult(1)
            }
        }
    }
}

private fun OrtResult.diff(other: OrtResult) =
    OrtResultDiff(
        repositoryDiff = repository.diff(other.repository)
    )

private fun Repository.diff(other: Repository): RepositoryDiff? =
    if (this == other) {
        null
    } else {
        RepositoryDiff(
            vcsA = vcs.takeIf { it != other.vcs },
            vcsB = other.vcs.takeIf { it != vcs },
            vcsProcessedA = vcsProcessed.takeIf { it != other.vcsProcessed },
            vcsProcessedB = other.vcsProcessed.takeIf { it != vcsProcessed },
            nestedRepositoriesA = nestedRepositories.takeIf { it != other.nestedRepositories },
            nestedRepositoriesB = other.nestedRepositories.takeIf { it != nestedRepositories },
            configDiff = config.diff(other.config)
        )
    }

private fun RepositoryConfiguration.diff(other: RepositoryConfiguration): RepositoryConfigurationDiff? =
    if (this == other) {
        null
    } else {
        RepositoryConfigurationDiff(
            analyzerConfigDiff = analyzer.diff(other.analyzer),
            excludeDiff = excludes.diff(other.excludes),
            resolutionsDiff = resolutions.diff(other.resolutions),
            curationsDiff = curations.diff(other.curations),
            packageConfigurationsA = (packageConfigurations - other.packageConfigurations.toSet())
                .takeUnless { it.isEmpty() },
            packageConfigurationsB = (other.packageConfigurations - packageConfigurations.toSet())
                .takeUnless { it.isEmpty() },
            licenseChoicesDiff = licenseChoices.diff(other.licenseChoices)
        )
    }

private fun Excludes.diff(other: Excludes): ExcludesDiff? =
    if (this == other) {
        null
    } else {
        ExcludesDiff(
            pathsA = (paths - other.paths.toSet()).takeUnless { it.isEmpty() },
            pathsB = (other.paths - paths.toSet()).takeUnless { it.isEmpty() },
            scopesA = (scopes - other.scopes.toSet()).takeUnless { it.isEmpty() },
            scopesB = (other.scopes - scopes.toSet()).takeUnless { it.isEmpty() }
        )
    }

private fun Resolutions?.diff(other: Resolutions?): ResolutionsDiff? {
    if (this == other) return null

    return if (this == null) {
        ResolutionsDiff(
            issuesB = other?.issues,
            ruleViolationsB = other?.ruleViolations,
            vulnerabilitiesB = other?.vulnerabilities
        )
    } else if (other == null) {
        ResolutionsDiff(
            issuesA = issues,
            ruleViolationsA = ruleViolations,
            vulnerabilitiesA = vulnerabilities
        )
    } else {
        ResolutionsDiff(
            issuesA = (issues - other.issues.toSet()).takeUnless { it.isEmpty() },
            issuesB = (other.issues - issues.toSet()).takeUnless { it.isEmpty() },
            ruleViolationsA = (ruleViolations - other.ruleViolations.toSet()).takeUnless { it.isEmpty() },
            ruleViolationsB = (other.ruleViolations - ruleViolations.toSet()).takeUnless { it.isEmpty() },
            vulnerabilitiesA = (vulnerabilities - other.vulnerabilities.toSet()).takeUnless { it.isEmpty() },
            vulnerabilitiesB = (other.vulnerabilities - vulnerabilities.toSet()).takeUnless { it.isEmpty() }
        )
    }
}

private fun Curations.diff(other: Curations): CurationsDiff? =
    if (this == other) {
        null
    } else {
        CurationsDiff(
            packagesA = (packages - other.packages.toSet()).takeUnless { it.isEmpty() },
            packagesB = (other.packages - packages.toSet()).takeUnless { it.isEmpty() },
            licenseFindingsA = (licenseFindings - other.licenseFindings.toSet()).takeUnless { it.isEmpty() },
            licenseFindingsB = (other.licenseFindings - licenseFindings.toSet()).takeUnless { it.isEmpty() }
        )
    }

private fun LicenseChoices.diff(other: LicenseChoices): LicenseChoicesDiff? =
    if (this == other) {
        null
    } else {
        LicenseChoicesDiff(
            repositoryLicenseChoicesA = (repositoryLicenseChoices - other.repositoryLicenseChoices.toSet())
                .takeUnless { it.isEmpty() },
            repositoryLicenseChoicesB = (other.repositoryLicenseChoices - repositoryLicenseChoices.toSet())
                .takeUnless { it.isEmpty() },
            packageLicenseChoicesA = (packageLicenseChoices - other.packageLicenseChoices.toSet())
                .takeUnless { it.isEmpty() },
            packageLicenseChoicesB = (other.packageLicenseChoices - packageLicenseChoices.toSet())
                .takeUnless { it.isEmpty() }
        )
    }

private fun RepositoryAnalyzerConfiguration?.diff(other: RepositoryAnalyzerConfiguration?): AnalyzerConfigurationDiff? {
    if (this == other) return null

    return if (this == null) {
        AnalyzerConfigurationDiff(
            allowDynamicVersionsB = other?.allowDynamicVersions,
            enabledPackageManagersB = other?.enabledPackageManagers,
            disabledPackageManagersB = other?.disabledPackageManagers,
            packageManagersB = other?.packageManagers,
            skipExcludedB = other?.skipExcluded
        )
    } else if (other == null) {
        AnalyzerConfigurationDiff(
            allowDynamicVersionsA = allowDynamicVersions,
            enabledPackageManagersA = enabledPackageManagers,
            disabledPackageManagersA = disabledPackageManagers,
            packageManagersA = packageManagers,
            skipExcludedA = skipExcluded
        )
    } else {
        AnalyzerConfigurationDiff(
            allowDynamicVersionsA = allowDynamicVersions.takeIf { it != other.allowDynamicVersions },
            allowDynamicVersionsB = other.allowDynamicVersions.takeIf { it != allowDynamicVersions },
            enabledPackageManagersA = enabledPackageManagers.takeIf { it != other.enabledPackageManagers },
            enabledPackageManagersB = other.enabledPackageManagers.takeIf { it != enabledPackageManagers },
            disabledPackageManagersA = disabledPackageManagers.takeIf { it != other.disabledPackageManagers },
            disabledPackageManagersB = other.disabledPackageManagers.takeIf { it != disabledPackageManagers },
            packageManagersA = packageManagers.takeIf { it != other.packageManagers },
            packageManagersB = other.packageManagers.takeIf { it != packageManagers },
            skipExcludedA = skipExcluded.takeIf { it != other.skipExcluded },
            skipExcludedB = other.skipExcluded.takeIf { it != skipExcluded }
        )
    }
}

private enum class CompareMethod {
    SEMANTIC_DIFF,
    TEXT_DIFF
}

private class EpochInstantDeserializer : StdDeserializer<Instant>(Instant::class.java) {
    override fun deserialize(parser: JsonParser, context: DeserializationContext): Instant =
        Instant.EPOCH.also { parser.codec.readTree<JsonNode>(parser) }
}

private class DefaultEnvironmentDeserializer : StdDeserializer<Environment>(Environment::class.java) {
    override fun deserialize(parser: JsonParser, context: DeserializationContext): Environment =
        Environment().also { parser.codec.readTree<JsonNode>(parser) }
}

private fun Map<Regex, String>.replaceIn(text: String) =
    entries.fold(text) { currentText, (from, to) ->
        currentText.replace(from, to)
    }

private data class OrtResultDiff(
    val repositoryDiff: RepositoryDiff? = null
)

private data class RepositoryDiff(
    val vcsA: VcsInfo? = null,
    val vcsB: VcsInfo? = null,
    val vcsProcessedA: VcsInfo? = null,
    val vcsProcessedB: VcsInfo? = null,
    val nestedRepositoriesA: Map<String, VcsInfo>? = null,
    val nestedRepositoriesB: Map<String, VcsInfo>? = null,
    val configDiff: RepositoryConfigurationDiff? = null
)

private data class RepositoryConfigurationDiff(
    val analyzerConfigDiff: AnalyzerConfigurationDiff? = null,
    val excludeDiff: ExcludesDiff? = null,
    val resolutionsDiff: ResolutionsDiff? = null,
    val curationsDiff: CurationsDiff? = null,
    val packageConfigurationsA: List<PackageConfiguration>? = null,
    val packageConfigurationsB: List<PackageConfiguration>? = null,
    val licenseChoicesDiff: LicenseChoicesDiff? = null
)

private data class ExcludesDiff(
    val pathsA: List<PathExclude>? = null,
    val pathsB: List<PathExclude>? = null,
    val scopesA: List<ScopeExclude>? = null,
    val scopesB: List<ScopeExclude>? = null
)

private data class ResolutionsDiff(
    val issuesA: List<IssueResolution>? = null,
    val issuesB: List<IssueResolution>? = null,
    val ruleViolationsA: List<RuleViolationResolution>? = null,
    val ruleViolationsB: List<RuleViolationResolution>? = null,
    val vulnerabilitiesA: List<VulnerabilityResolution>? = null,
    val vulnerabilitiesB: List<VulnerabilityResolution>? = null
)

private data class CurationsDiff(
    val packagesA: List<PackageCuration>? = null,
    val packagesB: List<PackageCuration>? = null,
    val licenseFindingsA: List<LicenseFindingCuration>? = null,
    val licenseFindingsB: List<LicenseFindingCuration>? = null
)

private data class LicenseChoicesDiff(
    val repositoryLicenseChoicesA: List<SpdxLicenseChoice>? = null,
    val repositoryLicenseChoicesB: List<SpdxLicenseChoice>? = null,
    val packageLicenseChoicesA: List<PackageLicenseChoice>? = null,
    val packageLicenseChoicesB: List<PackageLicenseChoice>? = null
)

private data class AnalyzerConfigurationDiff(
    val allowDynamicVersionsA: Boolean? = null,
    val allowDynamicVersionsB: Boolean? = null,
    val enabledPackageManagersA: List<String>? = null,
    val enabledPackageManagersB: List<String>? = null,
    val disabledPackageManagersA: List<String>? = null,
    val disabledPackageManagersB: List<String>? = null,
    val packageManagersA: Map<String, PackageManagerConfiguration>? = null,
    val packageManagersB: Map<String, PackageManagerConfiguration>? = null,
    val skipExcludedA: Boolean? = false,
    val skipExcludedB: Boolean? = false
)
