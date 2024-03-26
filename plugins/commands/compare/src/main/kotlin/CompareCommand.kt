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

@file:Suppress("TooManyFunctions")

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

import org.ossreviewtoolkit.model.AdvisorRecord
import org.ossreviewtoolkit.model.AdvisorRun
import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.DependencyGraph
import org.ossreviewtoolkit.model.EvaluatorRun
import org.ossreviewtoolkit.model.FileList
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.ProvenanceResolutionResult
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.ResolvedConfiguration
import org.ossreviewtoolkit.model.ResolvedPackageCurations
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.model.SnippetFinding
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Curations
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.FileArchiverConfiguration
import org.ossreviewtoolkit.model.config.FileListStorageConfiguration
import org.ossreviewtoolkit.model.config.IssueResolution
import org.ossreviewtoolkit.model.config.LicenseChoices
import org.ossreviewtoolkit.model.config.LicenseFindingCuration
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.config.PackageLicenseChoice
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.PluginConfiguration
import org.ossreviewtoolkit.model.config.ProvenanceStorageConfiguration
import org.ossreviewtoolkit.model.config.RepositoryAnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.model.config.RuleViolationResolution
import org.ossreviewtoolkit.model.config.ScanStorageConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
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

    private val ignoreFileList by option(
        "--ignore-file-list", "-f",
        help = "Ignore environment differences."
    ).flag()

    private val ignoreTmpDir by option(
        "--ignore-tmp-dir", "-d",
        help = "Ignore temporary directory differences."
    ).flag()

    private val ignoreConfig by option(
        "--ignore-config", "-c",
        help = "Ignore configuration options."
    ).flag()

    private val ignoreResultsFromDifferentScannerVersions by option(
        "--ignore-version-mismatch", "-v",
        help = "Ignore scan results if they have been created by different scanner versions."
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

                val context = SemanticDiffContext(
                    ignoreConfig = ignoreConfig,
                    ignoreFileList = ignoreFileList,
                    ignoreResultsFromDifferentScannerVersions = ignoreResultsFromDifferentScannerVersions,
                    packageConfigMapper = ::lenientPackageConfigurationMapper,
                    scanResultMapper = ::lenientScanResultMapper
                )

                val diff = resultA.diff(resultB, context)

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

private fun OrtResult.diff(other: OrtResult, context: SemanticDiffContext) =
    OrtResultDiff(
        repositoryDiff = repository.diff(other.repository, context),
        analyzerRunDiff = analyzer.diff(other.analyzer),
        scannerRunDiff = scanner.diff(other.scanner, context),
        advisorRunDiff = advisor.diff(other.advisor),
        evaluatorRunDiff = evaluator.diff(other.evaluator),
        resolvedConfigurationDiff = resolvedConfiguration.diff(other.resolvedConfiguration)
    )

private fun Repository.diff(other: Repository, context: SemanticDiffContext): RepositoryDiff? =
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
            configDiff = config.diff(other.config, context)
        )
    }

private fun RepositoryConfiguration.diff(
    other: RepositoryConfiguration,
    context: SemanticDiffContext
): RepositoryConfigurationDiff? =
    if (this == other) {
        null
    } else {
        RepositoryConfigurationDiff(
            analyzerConfigDiff = analyzer.diff(other.analyzer),
            excludeDiff = excludes.diff(other.excludes),
            resolutionsDiff = resolutions.diff(other.resolutions),
            curationsDiff = curations.diff(other.curations),
            packageConfigurationsA = mappedDiff(
                packageConfigurations,
                other.packageConfigurations,
                context.packageConfigMapper
            ),
            packageConfigurationsB = mappedDiff(
                other.packageConfigurations,
                packageConfigurations.toSet(),
                context.packageConfigMapper
            ),
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

private fun AnalyzerRun?.diff(other: AnalyzerRun?): AnalyzerRunDiff? {
    if (this == other) return null

    return if (this == null) {
        AnalyzerRunDiff(
            startTimeB = other?.startTime,
            endTimeB = other?.endTime,
            environmentB = other?.environment,
            configDiff = diff(other?.config),
            resultDiff = diff(other?.result)
        )
    } else if (other == null) {
        AnalyzerRunDiff(
            startTimeA = startTime,
            endTimeA = endTime,
            environmentA = environment,
            configDiff = config.diff(null),
            resultDiff = result.diff(null)
        )
    } else {
        AnalyzerRunDiff(
            startTimeA = startTime.takeIf { it != other.startTime },
            startTimeB = other.startTime.takeIf { it != startTime },
            endTimeA = endTime.takeIf { it != other.endTime },
            endTimeB = other.endTime.takeIf { it != endTime },
            environmentA = environment.takeIf { it != other.environment },
            environmentB = other.environment.takeIf { it != environment },
            configDiff = config.diff(other.config),
            resultDiff = result.diff(other.result)
        )
    }
}

private fun AnalyzerConfiguration?.diff(other: AnalyzerConfiguration?): AnalyzerConfigurationDiff? {
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

private fun AnalyzerResult?.diff(other: AnalyzerResult?): AnalyzerResultDiff? {
    if (this == other) return null

    return if (this == null) {
        AnalyzerResultDiff(
            projectsB = other?.projects,
            packagesB = other?.packages,
            issuesB = other?.issues,
            dependencyGraphB = other?.dependencyGraphs
        )
    } else if (other == null) {
        AnalyzerResultDiff(
            projectsA = projects,
            packagesA = packages,
            issuesA = issues,
            dependencyGraphA = dependencyGraphs
        )
    } else {
        AnalyzerResultDiff(
            projectsA = (projects - other.projects).takeUnless { it.isEmpty() },
            projectsB = (other.projects - projects).takeUnless { it.isEmpty() },
            packagesA = (packages - other.packages).takeUnless { it.isEmpty() },
            packagesB = (other.packages - packages).takeUnless { it.isEmpty() },
            issuesA = issues.takeIf { it != other.issues },
            issuesB = other.issues.takeIf { it != issues },
            dependencyGraphA = dependencyGraphs.takeIf { it != other.dependencyGraphs },
            dependencyGraphB = other.dependencyGraphs.takeIf { it != dependencyGraphs }
        )
    }
}

private fun ScannerRun?.diff(other: ScannerRun?, context: SemanticDiffContext): ScannerRunDiff? {
    if (this == other) return null

    return if (this == null) {
        ScannerRunDiff(
            startTimeB = other?.startTime,
            endTimeB = other?.endTime,
            environmentB = other?.environment,
            config = diff(other?.config),
            provenancesB = other?.provenances,
            scannersB = other?.scanners,
            filesB = other?.files,
            scanResultDiff = other?.scanResults?.mapNotNullTo(mutableSetOf()) { null.diff(it, context) }
        )
    } else if (other == null) {
        ScannerRunDiff(
            startTimeA = startTime,
            endTimeA = endTime,
            environmentA = environment,
            config = config.diff(null),
            provenancesA = provenances,
            scannersA = scanners,
            filesA = files,
            scanResultDiff = scanResults.mapNotNullTo(mutableSetOf()) { it.diff(null, context) }
        )
    } else {
        val scanResultsA = scanResults.map(context.scanResultMapper)
        val scanResultsAMapping = scanResultsA.associateBy { result -> (result.provenance to result.scanner.name) }
        val scanResultsB = other.scanResults.map(context.scanResultMapper)
        val scanResultsBMapping = scanResultsB.associateBy { result -> (result.provenance to result.scanner.name) }

        val differentResults = scanResultsA.mapNotNullTo(mutableSetOf()) {
            it.diff(scanResultsBMapping[it.provenance to it.scanner.name], context)
        } + scanResultsB.mapNotNullTo(mutableSetOf()) {
            scanResultsAMapping[it.provenance to it.scanner.name].diff(it, context)
        }

        ScannerRunDiff(
            startTimeA = startTime.takeIf { it != other.startTime },
            startTimeB = other.startTime.takeIf { it != startTime },
            endTimeA = endTime.takeIf { it != other.endTime },
            endTimeB = other.endTime.takeIf { it != endTime },
            environmentA = environment.takeIf { it != other.environment },
            environmentB = other.environment.takeIf { it != environment },
            config = if (context.ignoreConfig) null else config.diff(other.config),
            provenancesA = (provenances - other.provenances).takeUnless { it.isEmpty() },
            provenancesB = (other.provenances - provenances).takeUnless { it.isEmpty() },
            scannersA = scanners.diff(other.scanners).takeUnless { it.isEmpty() },
            scannersB = other.scanners.diff(scanners).takeUnless { it.isEmpty() },
            filesA = if (context.ignoreFileList) null else (files - other.files).takeUnless { it.isEmpty() },
            filesB = if (context.ignoreFileList) null else (other.files - files).takeUnless { it.isEmpty() },
            scanResultDiff = differentResults.takeUnless { it.isEmpty() }
        )
    }
}

private fun Map<Identifier, Set<String>>.diff(other: Map<Identifier, Set<String>>): Map<Identifier, Set<String>> {
    return filter { (id, values) -> other[id] != values }
}

private fun ScannerConfiguration?.diff(other: ScannerConfiguration?): ScannerConfigurationDiff? {
    if (this == other) return null

    return if (this == null) {
        ScannerConfigurationDiff(
            skipConcludedB = other?.skipConcluded,
            archiveB = other?.archive,
            createMissingArchivesB = other?.createMissingArchives,
            detectedLicenseMappingB = other?.detectedLicenseMapping,
            fileListStorageB = other?.fileListStorage,
            configB = other?.config,
            storagesB = other?.storages,
            storageReadersB = other?.storageReaders,
            storageWritersB = other?.storageWriters,
            ignorePatternsB = other?.ignorePatterns,
            provenanceStorageB = other?.provenanceStorage
        )
    } else if (other == null) {
        ScannerConfigurationDiff(
            skipConcludedA = skipConcluded,
            archiveA = archive,
            createMissingArchivesA = createMissingArchives,
            detectedLicenseMappingA = detectedLicenseMapping,
            fileListStorageA = fileListStorage,
            configA = config,
            storagesA = storages,
            storageReadersA = storageReaders,
            storageWritersA = storageWriters,
            ignorePatternsA = ignorePatterns,
            provenanceStorageA = provenanceStorage
        )
    } else {
        ScannerConfigurationDiff(
            skipConcludedA = skipConcluded.takeIf { it != other.skipConcluded },
            skipConcludedB = other.skipConcluded.takeIf { it != skipConcluded },
            archiveA = archive.takeIf { it != other.archive },
            archiveB = other.archive.takeIf { it != archive },
            createMissingArchivesA = createMissingArchives.takeIf { it != other.createMissingArchives },
            createMissingArchivesB = other.createMissingArchives.takeIf { it != createMissingArchives },
            detectedLicenseMappingA = detectedLicenseMapping.takeIf { it != other.detectedLicenseMapping },
            detectedLicenseMappingB = other.detectedLicenseMapping.takeIf { it != detectedLicenseMapping },
            fileListStorageA = fileListStorage.takeIf { it != other.fileListStorage },
            fileListStorageB = other.fileListStorage.takeIf { it != fileListStorage },
            configA = config.takeIf { it != other.config },
            configB = other.config.takeIf { it != config },
            storagesA = storages.takeIf { it != other.storages },
            storagesB = other.storages.takeIf { it != storages },
            storageReadersA = (storageReaders.orEmpty() - other.storageReaders.orEmpty().toSet())
                .takeUnless { it.isEmpty() },
            storageReadersB = (other.storageReaders.orEmpty() - storageReaders.orEmpty().toSet())
                .takeUnless { it.isEmpty() },
            storageWritersA = (storageWriters.orEmpty() - other.storageWriters.orEmpty().toSet())
                .takeUnless { it.isEmpty() },
            storageWritersB = (other.storageWriters.orEmpty() - storageWriters.orEmpty().toSet())
                .takeUnless { it.isEmpty() },
            ignorePatternsA = (ignorePatterns - other.ignorePatterns.toSet()).takeUnless { it.isEmpty() },
            ignorePatternsB = (other.ignorePatterns - ignorePatterns.toSet()).takeUnless { it.isEmpty() },
            provenanceStorageA = provenanceStorage.takeIf { it != other.provenanceStorage },
            provenanceStorageB = other.provenanceStorage.takeIf { it != provenanceStorage }
        )
    }
}

private fun ScanResult?.diff(other: ScanResult?, context: SemanticDiffContext): ScanResultDiff? {
    val thisMapped = this?.let { context.scanResultMapper(it) }
    val otherMapped = other?.let { context.scanResultMapper(it) }
    if (thisMapped == otherMapped) return null

    return if (thisMapped == null) {
        ScanResultDiff(
            provenance = other?.provenance,
            scanner = other?.scanner,
            summaryDiff = null.diff(other?.summary)
        )
    } else if (otherMapped == null) {
        ScanResultDiff(
            provenance = thisMapped.provenance,
            scanner = thisMapped.scanner,
            summaryDiff = thisMapped.summary.diff(null)
        )
    } else {
        if (context.ignoreResultsFromDifferentScannerVersions &&
            thisMapped.scanner.version != otherMapped.scanner.version
        ) {
            null
        } else {
            ScanResultDiff(
                provenance = thisMapped.provenance,
                scanner = thisMapped.scanner,
                summaryDiff = thisMapped.summary.diff(otherMapped.summary)
            )
        }
    }
}

private fun ScanSummary?.diff(other: ScanSummary?): ScanSummaryDiff? {
    if (this == other) return null

    return if (this == null) {
        ScanSummaryDiff(
            licenseFindingsB = other?.licenseFindings,
            copyrightFindingsB = other?.copyrightFindings,
            snippetFindingsB = other?.snippetFindings,
            issuesB = other?.issues?.toSet()
        )
    } else if (other == null) {
        ScanSummaryDiff(
            licenseFindingsA = licenseFindings,
            copyrightFindingsA = copyrightFindings,
            snippetFindingsA = snippetFindings,
            issuesA = issues.toSet()
        )
    } else {
        val lenientSnippets = snippetFindings.map(::lenientSnippetFindingMapper)
        val otherLenientSnippets = other.snippetFindings.map(::lenientSnippetFindingMapper)
        val snippetFindingsA = mappedDiff(lenientSnippets, otherLenientSnippets, identityMapper())
        val reducedSnippetFindingsA = snippetFindingsA?.mapTo(mutableSetOf()) {
            reduceSnippedFindingDelta(it, otherLenientSnippets)
        }

        val snippetFindingsB = mappedDiff(otherLenientSnippets, lenientSnippets, identityMapper())
        val reducedSnippedFindingsB = snippetFindingsB?.mapTo(mutableSetOf()) {
            reduceSnippedFindingDelta(it, lenientSnippets)
        }

        ScanSummaryDiff(
            licenseFindingsA = mappedDiff(licenseFindings, other.licenseFindings, identityMapper()),
            licenseFindingsB = mappedDiff(other.licenseFindings, licenseFindings, identityMapper()),
            copyrightFindingsA = mappedDiff(copyrightFindings, other.copyrightFindings, identityMapper()),
            copyrightFindingsB = mappedDiff(other.copyrightFindings, copyrightFindings, identityMapper()),
            snippetFindingsA = reducedSnippetFindingsA,
            snippetFindingsB = reducedSnippedFindingsB,
            issuesA = mappedDiff(issues, other.issues, identityMapper()),
            issuesB = mappedDiff(other.issues, issues, identityMapper())
        )
    }
}

private fun reduceSnippedFindingDelta(
    finding: SnippetFinding,
    otherFindings: Collection<SnippetFinding>
): SnippetFinding =
    otherFindings.filter { it.sourceLocation == finding.sourceLocation }
        .map { it to it.snippets.subtract(finding.snippets) }
        .minByOrNull { it.second.size }?.first?.let { other ->
            finding.copy(snippets = finding.snippets - other.snippets)
        } ?: finding

private fun EvaluatorRun?.diff(other: EvaluatorRun?): EvaluatorRunDiff? {
    if (this == other) return null

    return if (this == null) {
        EvaluatorRunDiff(
            startTimeB = other?.startTime,
            endTimeB = other?.endTime,
            violationsB = other?.violations
        )
    } else if (other == null) {
        EvaluatorRunDiff(
            startTimeA = startTime,
            endTimeA = endTime,
            violationsA = violations
        )
    } else {
        EvaluatorRunDiff(
            startTimeA = startTime.takeIf { it != other.startTime },
            startTimeB = other.startTime.takeIf { it != startTime },
            endTimeA = endTime.takeIf { it != other.endTime },
            endTimeB = other.endTime.takeIf { it != endTime },
            violationsA = mappedDiff(violations, other.violations.toSet(), ::lenientViolationMapper)?.toList(),
            violationsB = mappedDiff(other.violations, violations.toSet(), ::lenientViolationMapper)?.toList()
        )
    }
}

private fun AdvisorRun?.diff(other: AdvisorRun?): AdvisorRunDiff? {
    if (this == other) return null

    return if (this == null) {
        AdvisorRunDiff(
            startTimeB = other?.startTime,
            endTimeB = other?.endTime,
            environmentB = other?.environment,
            configB = other?.config,
            resultsB = other?.results
        )
    } else if (other == null) {
        AdvisorRunDiff(
            startTimeA = startTime,
            endTimeA = endTime,
            environmentA = environment,
            configA = config,
            resultsA = results
        )
    } else {
        AdvisorRunDiff(
            startTimeA = startTime.takeIf { it != other.startTime },
            startTimeB = other.startTime.takeIf { it != startTime },
            endTimeA = endTime.takeIf { it != other.endTime },
            endTimeB = other.endTime.takeIf { it != endTime },
            environmentA = environment.takeIf { it != other.environment },
            environmentB = other.environment.takeIf { it != environment },
            configA = config.takeIf { it != other.config },
            configB = other.config.takeIf { it != config },
            resultsA = results.takeIf { it != other.results },
            resultsB = other.results.takeIf { it != results }
        )
    }
}

private fun ResolvedConfiguration.diff(other: ResolvedConfiguration): ResolvedConfigurationDiff? =
    if (this == other) {
        null
    } else {
        ResolvedConfigurationDiff(
            packageConfigurationsA = mappedDiff(
                packageConfigurations.orEmpty(),
                other.packageConfigurations.orEmpty(),
                ::lenientPackageConfigurationMapper
            )?.toList(),
            packageConfigurationsB = mappedDiff(
                other.packageConfigurations.orEmpty(),
                packageConfigurations.orEmpty(),
                ::lenientPackageConfigurationMapper
            )?.toList(),
            packageCurations = packageCurations.diff(other.packageCurations),
            resolutionsDiff = resolutions?.diff(other.resolutions)
        )
    }

private enum class CompareMethod {
    SEMANTIC_DIFF,
    TEXT_DIFF
}

private fun List<ResolvedPackageCurations>.diff(other: List<ResolvedPackageCurations>): Map<String, PackageCurationDiff>? {
    val providers = (map { it.provider.id } + other.map { it.provider.id }).toSortedSet()

    return providers.associateWith { provider ->
        val curationsA = find { it.provider.id == provider }?.curations.orEmpty()
        val curationsB = other.find { it.provider.id == provider }?.curations.orEmpty()

        PackageCurationDiff(
            packageCurationsA = (curationsA - curationsB).sortedBy { it.id },
            packageCurationsB = (curationsB - curationsA).sortedBy { it.id }
        )
    }.takeIf { it.isNotEmpty() }?.filter {
        it.value.packageCurationsA?.isNotEmpty() == true || it.value.packageCurationsB?.isNotEmpty() == true
    }
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

typealias DiffMapper<T> = (T) -> T

private fun lenientPackageConfigurationMapper(configuration: PackageConfiguration): PackageConfiguration =
    configuration.copy(
        pathExcludes = configuration.pathExcludes.distinct().sortedBy { it.pattern },
        licenseFindingCurations = configuration.licenseFindingCurations.distinct().sortedBy { it.path }
    )

private fun lenientScanResultMapper(scanResult: ScanResult): ScanResult =
    scanResult.copy(
        scanner = scanResult.scanner.copy(configuration = "")
    )

private fun lenientViolationMapper(violation: RuleViolation): RuleViolation =
    violation.copy(
        message = violation.message.substringBefore('\n')
    )

private fun lenientSnippetFindingMapper(snippetFinding: SnippetFinding): SnippetFinding {
    val lenientSnippets = snippetFinding.snippets.mapTo(mutableSetOf()) { it.copy(additionalData = emptyMap()) }
    return snippetFinding.copy(snippets = lenientSnippets)
}

private fun <T> identityMapper(): DiffMapper<T> = { it }

private fun <T> mappedDiff(itemsA: Collection<T>, itemsB: Collection<T>, mapper: DiffMapper<T>): Set<T>? {
    val mappedA = itemsA.mapTo(mutableSetOf(), mapper)
    val mappedB = itemsB.mapTo(mutableSetOf(), mapper)

    return (mappedA - mappedB).takeUnless { it.isEmpty() }
}

private data class SemanticDiffContext(
    val ignoreConfig: Boolean = false,
    val ignoreFileList: Boolean = false,
    val ignoreResultsFromDifferentScannerVersions: Boolean = false,
    val packageConfigMapper: DiffMapper<PackageConfiguration> = identityMapper(),
    val scanResultMapper: DiffMapper<ScanResult> = identityMapper()
)

private data class OrtResultDiff(
    val repositoryDiff: RepositoryDiff? = null,
    val analyzerRunDiff: AnalyzerRunDiff? = null,
    val scannerRunDiff: ScannerRunDiff? = null,
    val advisorRunDiff: AdvisorRunDiff? = null,
    val evaluatorRunDiff: EvaluatorRunDiff? = null,
    val resolvedConfigurationDiff: ResolvedConfigurationDiff? = null
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
    val packageConfigurationsA: Set<PackageConfiguration>? = null,
    val packageConfigurationsB: Set<PackageConfiguration>? = null,
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

private data class AnalyzerRunDiff(
    val startTimeA: Instant? = null,
    val startTimeB: Instant? = null,
    val endTimeA: Instant? = null,
    val endTimeB: Instant? = null,
    val environmentA: Environment? = null,
    val environmentB: Environment? = null,
    val configDiff: AnalyzerConfigurationDiff? = null,
    val resultDiff: AnalyzerResultDiff? = null
)

private data class AnalyzerResultDiff(
    val projectsA: Set<Project>? = null,
    val projectsB: Set<Project>? = null,
    val packagesA: Set<Package>? = null,
    val packagesB: Set<Package>? = null,
    val issuesA: Map<Identifier, List<Issue>>? = null,
    val issuesB: Map<Identifier, List<Issue>>? = null,
    val dependencyGraphA: Map<String, DependencyGraph>? = null,
    val dependencyGraphB: Map<String, DependencyGraph>? = null
)

private data class ScannerRunDiff(
    val startTimeA: Instant? = null,
    val startTimeB: Instant? = null,
    val endTimeA: Instant? = null,
    val endTimeB: Instant? = null,
    val environmentA: Environment? = null,
    val environmentB: Environment? = null,
    val config: ScannerConfigurationDiff? = null,
    val provenancesA: Set<ProvenanceResolutionResult>? = null,
    val provenancesB: Set<ProvenanceResolutionResult>? = null,
    val scannersA: Map<Identifier, Set<String>>? = null,
    val scannersB: Map<Identifier, Set<String>>? = null,
    val filesA: Set<FileList>? = null,
    val filesB: Set<FileList>? = null,
    val scanResultDiff: Set<ScanResultDiff>? = null
)

private data class ScannerConfigurationDiff(
    val skipConcludedA: Boolean? = null,
    val skipConcludedB: Boolean? = null,
    val archiveA: FileArchiverConfiguration? = null,
    val archiveB: FileArchiverConfiguration? = null,
    val createMissingArchivesA: Boolean? = null,
    val createMissingArchivesB: Boolean? = null,
    val detectedLicenseMappingA: Map<String, String>? = null,
    val detectedLicenseMappingB: Map<String, String>? = null,
    val fileListStorageA: FileListStorageConfiguration? = null,
    val fileListStorageB: FileListStorageConfiguration? = null,
    val configA: Map<String, PluginConfiguration>? = null,
    val configB: Map<String, PluginConfiguration>? = null,
    val storagesA: Map<String, ScanStorageConfiguration>? = null,
    val storagesB: Map<String, ScanStorageConfiguration>? = null,
    val storageReadersA: List<String>? = null,
    val storageReadersB: List<String>? = null,
    val storageWritersA: List<String>? = null,
    val storageWritersB: List<String>? = null,
    val ignorePatternsA: List<String>? = null,
    val ignorePatternsB: List<String>? = null,
    val provenanceStorageA: ProvenanceStorageConfiguration? = null,
    val provenanceStorageB: ProvenanceStorageConfiguration? = null
)

private data class ScanResultDiff(
    val provenance: Provenance? = null,
    val scanner: ScannerDetails? = null,
    val summaryDiff: ScanSummaryDiff? = null
)

private data class ScanSummaryDiff(
    val licenseFindingsA: Set<LicenseFinding>? = null,
    val licenseFindingsB: Set<LicenseFinding>? = null,
    val copyrightFindingsA: Set<CopyrightFinding>? = null,
    val copyrightFindingsB: Set<CopyrightFinding>? = null,
    val snippetFindingsA: Set<SnippetFinding>? = null,
    val snippetFindingsB: Set<SnippetFinding>? = null,
    val issuesA: Set<Issue>? = null,
    val issuesB: Set<Issue>? = null
)

private data class AdvisorRunDiff(
    val startTimeA: Instant? = null,
    val startTimeB: Instant? = null,
    val endTimeA: Instant? = null,
    val endTimeB: Instant? = null,
    val environmentA: Environment? = null,
    val environmentB: Environment? = null,
    val configA: AdvisorConfiguration? = null,
    val configB: AdvisorConfiguration? = null,
    val resultsA: AdvisorRecord? = null,
    val resultsB: AdvisorRecord? = null
)

private data class EvaluatorRunDiff(
    val startTimeA: Instant? = null,
    val startTimeB: Instant? = null,
    val endTimeA: Instant? = null,
    val endTimeB: Instant? = null,
    val violationsA: List<RuleViolation>? = null,
    val violationsB: List<RuleViolation>? = null
)

private data class ResolvedConfigurationDiff(
    val packageConfigurationsA: List<PackageConfiguration>? = null,
    val packageConfigurationsB: List<PackageConfiguration>? = null,
    val packageCurations: Map<String, PackageCurationDiff>? = null,
    val resolutionsDiff: ResolutionsDiff? = null
)

private data class PackageCurationDiff(
    val packageCurationsA: List<PackageCuration>? = null,
    val packageCurationsB: List<PackageCuration>? = null
)
