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

@file:Suppress("TooManyFunctions")

package org.ossreviewtoolkit.helper.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator

import java.io.File
import java.nio.file.Paths

import kotlin.io.path.createTempDirectory

import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.downloader.Downloader
import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.SourceCodeOrigin
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.model.config.Curations
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.Includes
import org.ossreviewtoolkit.model.config.IssueResolution
import org.ossreviewtoolkit.model.config.LicenseFindingCuration
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.PathInclude
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.model.config.RuleViolationResolution
import org.ossreviewtoolkit.model.config.ScopeExclude
import org.ossreviewtoolkit.model.config.VulnerabilityResolution
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.utils.FindingCurationMatcher
import org.ossreviewtoolkit.model.utils.createLicenseInfoResolver
import org.ossreviewtoolkit.model.utils.filterByVcsPath
import org.ossreviewtoolkit.model.writeValue
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.plugins.api.PluginConfig
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.PackageConfigurationProvider
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.ort.CopyrightStatementsProcessor
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.SpdxSingleLicenseExpression

/**
 * Return an approximated minimal sublist of [this] so that the result still matches the exact same entries of the given
 * [projectScopes].
 */
internal fun List<ScopeExclude>.minimize(projectScopes: Set<String>): List<ScopeExclude> {
    val scopeExcludes = associateWith { scopeExclude ->
        projectScopes.filter { scopeExclude.matches(it) }.toSet()
    }

    return greedySetCover(scopeExcludes).toList()
}

/**
 * Download the sources corresponding to the given [Identifier][id] and [source code origin][sourceCodeOrigin].
 */
internal fun OrtResult.downloadSources(id: Identifier, sourceCodeOrigin: SourceCodeOrigin): File {
    val config = DownloaderConfiguration(sourceCodeOrigins = listOf(sourceCodeOrigin))
    val tempDir = createTempDirectory(Paths.get("."), ORTH_NAME).toFile()

    @Suppress("UnsafeCallOnNullableType")
    val pkg = getPackageOrProject(id)!!.metadata

    Downloader(config).download(pkg, tempDir)

    return tempDir
}

/**
 * Return the processed copyright statements of all packages and projects contained in this [OrtResult]. The statements
 * are processed for each package and license separately for consistency with the notice reporter. Statements contained
 * in the given [copyrightGarbage] are omitted.
 */
internal fun OrtResult.processAllCopyrightStatements(
    omitExcluded: Boolean = true,
    copyrightGarbage: Set<String> = emptySet(),
    addAuthorsToCopyrights: Boolean = false
): List<ProcessedCopyrightStatement> {
    val result = mutableListOf<ProcessedCopyrightStatement>()

    val licenseInfoResolver = createLicenseInfoResolver(
        copyrightGarbage = CopyrightGarbage(copyrightGarbage.toSortedSet()),
        addAuthorsToCopyrights = addAuthorsToCopyrights
    )

    getProjectsAndPackages().forEach { id ->
        licenseInfoResolver.resolveLicenseInfo(id).forEach inner@{ resolvedLicense ->
            if (omitExcluded && resolvedLicense.isDetectedExcluded) return@inner

            val copyrights = resolvedLicense.getResolvedCopyrights(
                process = false,
                omitExcluded = omitExcluded
            ).flatMap { resolvedCopyright ->
                resolvedCopyright.findings.map { it.statement }
            }

            val processResult = CopyrightStatementsProcessor.process(copyrights)

            processResult.processedStatements.filterNot { it.key in copyrightGarbage }.forEach {
                result += ProcessedCopyrightStatement(
                    packageId = id,
                    license = resolvedLicense.license,
                    statement = it.key,
                    rawStatements = it.value.toSet()
                )
            }

            processResult.unprocessedStatements.filterNot { it in copyrightGarbage }.forEach {
                result += ProcessedCopyrightStatement(
                    packageId = id,
                    license = resolvedLicense.license,
                    statement = it,
                    rawStatements = setOf(it)
                )
            }
        }
    }

    return result
}

/**
 * Return all license findings for the project or package associated with the given [id]. The license
 * [LicenseFindingCuration]s contained in this [OrtResult] are applied if and only if [applyCurations] is true.
 */
internal fun OrtResult.getLicenseFindingsById(
    id: Identifier,
    packageConfigurationProvider: PackageConfigurationProvider,
    applyCurations: Boolean = true,
    decomposeLicenseExpressions: Boolean = true
): Map<Provenance, Map<SpdxExpression, Set<TextLocation>>> {
    val result = mutableMapOf<Provenance, MutableMap<SpdxExpression, MutableSet<TextLocation>>>()

    fun getLicenseFindingCurations(provenance: Provenance): List<LicenseFindingCuration> =
        if (isProject(id)) {
            getLicenseFindingCurations(id)
        } else {
            packageConfigurationProvider.getPackageConfigurations(id, provenance).flatMap { it.licenseFindingCurations }
        }

    getScanResultsForId(id).filter { it.provenance is KnownProvenance }.map {
        // If a VCS path curation has been applied after the scanning stage, it is possible to apply that
        // curation without re-scanning in case the new VCS path is a subdirectory of the scanned VCS path.
        // So, filter by VCS path to enable the user to see the effect on the detected license with a shorter
        // turn around time / without re-scanning.
        it.filterByVcsPath(getPackage(id)?.metadata?.vcsProcessed?.path.orEmpty())
    }.forEach { scanResult ->
        val findingsForProvenance = result.getOrPut(scanResult.provenance) { mutableMapOf() }

        scanResult.summary.licenseFindings.let { findings ->
            if (applyCurations) {
                FindingCurationMatcher().applyAll(findings, getLicenseFindingCurations(scanResult.provenance))
                    .mapNotNullTo(mutableSetOf()) { it.curatedFinding }
            } else {
                findings
            }
        }.let { findings ->
            if (decomposeLicenseExpressions) {
                findings.flatMap { finding ->
                    finding.license.decompose().map { finding.copy(license = it) }
                }
            } else {
                findings
            }
        }.forEach { finding ->
            findingsForProvenance.getOrPut(finding.license) { mutableSetOf() } += finding.location
        }
    }

    return result
}

/**
 * Return all license [Identifier]s which triggered at least one [RuleViolation] with a [severity] contained in the
 * given [severity] collection, associated with the rule names of all corresponding violated rules.
 */
internal fun OrtResult.getViolatedRulesByLicense(
    id: Identifier,
    severity: Collection<Severity> = Severity.entries
): Map<SpdxSingleLicenseExpression, List<String>> =
    @Suppress("UnsafeCallOnNullableType")
    getRuleViolations()
        .filter { it.pkg == id && it.severity in severity && it.license != null }
        .groupBy { it.license!! }
        .mapValues { (_, ruleViolations) -> ruleViolations.map { it.rule } }

/**
 * Return the [Provenance] of the first scan result matching the given [id] or null if there is no match.
 */
internal fun OrtResult.getScannedProvenance(id: Identifier): KnownProvenance? =
    getScanResultsForId(id).firstNotNullOfOrNull { it.provenance as? KnownProvenance }

/**
 * Return the [SourceCodeOrigin] for this provenance.
 */
internal fun KnownProvenance?.getSourceCodeOrigin(): SourceCodeOrigin? =
    when (this) {
        is ArtifactProvenance -> SourceCodeOrigin.ARTIFACT
        is RepositoryProvenance -> SourceCodeOrigin.VCS
        else -> null
    }

/**
 * Return all path excludes from this [OrtResult] represented as [RepositoryPathExcludes].
 */
internal fun OrtResult.getRepositoryPathExcludes(): RepositoryPathExcludes {
    fun isDefinitionsFile(pathExclude: PathExclude) =
        PackageManagerFactory.ALL.values.any {
            it.create(PluginConfig.EMPTY).matchersForDefinitionFiles.any { matcher ->
                pathExclude.pattern.endsWith(matcher.toString())
            }
        }

    val pathExcludes = repository.config.excludes.paths.filterNot { isDefinitionsFile(it) }

    return getPathExcludesByRepository(pathExcludes, repository.nestedRepositories)
}

/**
 * Wrap this string on word boundaries with line breaks at the given [column].
 */
internal fun String.wrapAt(column: Int): String =
    buildString {
        var text = this@wrapAt

        while (text.isNotEmpty()) {
            val firstSpaceAfterColumnIndex = text.indexOf(' ', column)
            val lastSpaceBeforeColumnIndex = text.lastIndexOf(' ', column - 1)
            val wrapIndex = lastSpaceBeforeColumnIndex.takeUnless { it == -1 } ?: firstSpaceAfterColumnIndex

            val line = if (wrapIndex != -1) {
                text.substring(0, wrapIndex)
            } else {
                text
            }

            text = text.removePrefix(line).trimStart()

            appendLine(line)
        }
    }.trimEnd()

/**
 * Return a copy of this [OrtResult] with the [Repository.config] with the content of the given
 * [repositoryConfigurationFile].
 */
internal fun OrtResult.replaceConfig(repositoryConfigurationFile: File?): OrtResult =
    repositoryConfigurationFile?.let {
        replaceConfig(it.readValue())
    } ?: this

/**
 * Return a copy with sorting applied to all entry types which are to be sorted.
 */
internal fun PackageConfiguration.sortEntries(): PackageConfiguration =
    copy(
        pathExcludes = pathExcludes.sortPathExcludes(),
        licenseFindingCurations = licenseFindingCurations.sortLicenseFindingCurations()
    )

/**
 * Return a copy with the [IssueResolution]s replaced by the given [issueResolutions].
 */
internal fun RepositoryConfiguration.replaceIssueResolutions(
    issueResolutions: List<IssueResolution>
): RepositoryConfiguration = copy(resolutions = resolutions.copy(issues = issueResolutions))

/**
 * Return a copy with the [LicenseFindingCuration]s replaced by the given scope excludes.
 */
internal fun RepositoryConfiguration.replaceLicenseFindingCurations(
    curations: List<LicenseFindingCuration>
): RepositoryConfiguration = copy(curations = this.curations.copy(licenseFindings = curations))

/**
 * Return a copy with the [PathExclude]s replaced by the given scope excludes.
 */
internal fun RepositoryConfiguration.replacePathExcludes(pathExcludes: List<PathExclude>): RepositoryConfiguration =
    copy(excludes = excludes.copy(paths = pathExcludes))

/**
 * Return a copy with the [ScopeExclude]s replaced by the given [scopeExcludes].
 */
internal fun RepositoryConfiguration.replaceScopeExcludes(scopeExcludes: List<ScopeExclude>): RepositoryConfiguration =
    copy(excludes = excludes.copy(scopes = scopeExcludes))

/**
 * Return a copy with the [RuleViolationResolution]s replaced by the given [ruleViolations].
 */
internal fun RepositoryConfiguration.replaceRuleViolationResolutions(
    ruleViolations: List<RuleViolationResolution>
): RepositoryConfiguration = copy(resolutions = resolutions.copy(ruleViolations = ruleViolations))

/**
 * Return a copy with sorting applied to all entry types which are to be sorted.
 */
internal fun RepositoryConfiguration.sortEntries(): RepositoryConfiguration =
    sortLicenseFindingCurations().sortPathExcludes().sortScopeExcludes()

/**
 * Return a copy with the [LicenseFindingCuration]s sorted.
 */
internal fun RepositoryConfiguration.sortLicenseFindingCurations(): RepositoryConfiguration =
    copy(
        curations = curations.copy(
            licenseFindings = curations.licenseFindings.sortLicenseFindingCurations()
        )
    )

/**
 * Return a copy with the [PathExclude]s sorted.
 */
internal fun RepositoryConfiguration.sortPathExcludes(): RepositoryConfiguration =
    copy(
        excludes = excludes.copy(
            paths = excludes.paths.sortPathExcludes()
        )
    )

/**
 * Return a copy with the [ScopeExclude]s sorted.
 */
internal fun RepositoryConfiguration.sortScopeExcludes(): RepositoryConfiguration =
    copy(
        excludes = excludes.copy(
            scopes = excludes.scopes.sortedBy { (pattern, _, _) ->
                pattern.removePrefix(".*")
            }
        )
    )

/**
 * Serialize a [RepositoryConfiguration] to the given [targetFile].
 */
internal fun RepositoryConfiguration.write(targetFile: File) {
    targetFile.writeValue(this)
}

/**
 * Merge the given [RepositoryLicenseFindingCurations] replacing entries with equal [LicenseFindingCuration.path],
 * [LicenseFindingCuration.startLines], [LicenseFindingCuration.lineCount], [LicenseFindingCuration.detectedLicense]
 * and [LicenseFindingCuration.concludedLicense]. If the given [updateOnlyExisting] is true then only entries with
 * matching [LicenseFindingCuration.path] are merged.
 */
internal fun RepositoryLicenseFindingCurations.mergeLicenseFindingCurations(
    other: RepositoryLicenseFindingCurations,
    updateOnlyExisting: Boolean = false
): RepositoryLicenseFindingCurations {
    val result: MutableMap<String, MutableMap<LicenseFindingCurationKey, LicenseFindingCuration>> = mutableMapOf()

    fun merge(repositoryUrl: String, curation: LicenseFindingCuration, updateOnlyUpdateExisting: Boolean = false) {
        if (updateOnlyUpdateExisting && repositoryUrl !in result) return

        val curations = result.getOrPut(repositoryUrl) { mutableMapOf() }

        val key = curation.key()
        if (updateOnlyUpdateExisting && key !in curations) return

        curations[key] = curation
    }

    forEach { (repositoryUrl, curations) ->
        curations.forEach { curation ->
            merge(repositoryUrl, curation)
        }
    }

    other.forEach { (repositoryUrl, pathExcludes) ->
        pathExcludes.forEach { pathExclude ->
            merge(repositoryUrl, pathExclude, updateOnlyExisting)
        }
    }

    return result.mapValues { (_, pathExcludes) ->
        pathExcludes.values.toList()
    }
}

/**
 * Serialize these [RepositoryLicenseFindingCurations] to the given [targetFile].
 */
@JvmName("writeRepositoryLicenseFindingCurations")
internal fun RepositoryLicenseFindingCurations.write(targetFile: File) {
    targetFile.writeValue(
        mapValues { (_, curations) ->
            curations.sortedBy { it.path.removePrefix("*").removePrefix("*") }
        }.toSortedMap()
    )
}

/**
 * Merge the given [RepositoryPathExcludes] replacing entries with equal [PathExclude.pattern].
 * If the given [updateOnlyExisting] is true then only entries with matching [PathExclude.pattern] are merged.
 */
internal fun RepositoryPathExcludes.mergePathExcludes(
    other: RepositoryPathExcludes,
    updateOnlyExisting: Boolean = false
): RepositoryPathExcludes {
    val result: MutableMap<String, MutableMap<String, PathExclude>> = mutableMapOf()

    fun merge(repositoryUrl: String, pathExclude: PathExclude, updateOnlyUpdateExisting: Boolean = false) {
        if (updateOnlyUpdateExisting && repositoryUrl !in result) return

        val pathExcludes = result.getOrPut(repositoryUrl) { mutableMapOf() }
        if (updateOnlyUpdateExisting && pathExclude.pattern !in result) return

        pathExcludes[pathExclude.pattern] = pathExclude
    }

    forEach { (repositoryUrl, pathExcludes) ->
        pathExcludes.forEach { pathExclude ->
            merge(repositoryUrl, pathExclude)
        }
    }

    other.forEach { (repositoryUrl, pathExcludes) ->
        pathExcludes.forEach { pathExclude ->
            merge(repositoryUrl, pathExclude, updateOnlyExisting)
        }
    }

    return result.mapValues { (_, pathExcludes) ->
        pathExcludes.values.toList()
    }
}

/**
 * Serialize these [RepositoryPathExcludes] to the given [targetFile].
 */
@JvmName("writeRepositoryPathExcludes")
internal fun RepositoryPathExcludes.write(targetFile: File) {
    targetFile.writeValue(
        mapValues { (_, pathExcludes) ->
            pathExcludes.sortedBy { it.pattern }
        }.toSortedMap()
    )
}

/**
 * Apply the [vcsUrlMapping] to this [RepositoryPathExcludes].
 */
internal fun RepositoryPathExcludes.mapPathExcludesVcsUrls(vcsUrlMapping: VcsUrlMapping): RepositoryPathExcludes {
    val result = mutableMapOf<String, MutableList<PathExclude>>()

    forEach { (vcsUrl, pathExcludes) ->
        result.getOrPut(vcsUrlMapping.map(vcsUrl)) { mutableListOf() } += pathExcludes
    }

    return result.mapValues { (_, pathExcludes) -> pathExcludes.distinct() }
}

/**
 * Apply the [vcsUrlMapping] to this [RepositoryLicenseFindingCurations].
 */
internal fun RepositoryLicenseFindingCurations.mapLicenseFindingCurationsVcsUrls(
    vcsUrlMapping: VcsUrlMapping
): RepositoryLicenseFindingCurations {
    val result = mutableMapOf<String, MutableList<LicenseFindingCuration>>()

    forEach { (vcsUrl, curations) ->
        result.getOrPut(vcsUrlMapping.map(vcsUrl)) { mutableListOf() } += curations
    }

    return result.mapValues { (_, pathExcludes) -> pathExcludes.distinct() }
}

/**
 * Merge the given [IssueResolution]s replacing entries with equal [IssueResolution.message].
 */
internal fun Collection<IssueResolution>.mergeIssueResolutions(
    other: Collection<IssueResolution>
): List<IssueResolution> {
    val result = mutableMapOf<String, IssueResolution>()

    associateByTo(result) { it.message }
    other.associateByTo(result) { it.message }

    return result.values.toList()
}

/**
 * Merge the given [LicenseFindingCuration]s replacing entries with equal [LicenseFindingCuration.path],
 * [LicenseFindingCuration.startLines], [LicenseFindingCuration.lineCount], [LicenseFindingCuration.detectedLicense]
 * and [LicenseFindingCuration.concludedLicense].
 * If the given [updateOnlyExisting] is true then only entries replacing existing ones are merged.
 */
internal fun Collection<LicenseFindingCuration>.mergeLicenseFindingCurations(
    other: Collection<LicenseFindingCuration>,
    updateOnlyExisting: Boolean = false
): List<LicenseFindingCuration> {
    val result = mutableMapOf<LicenseFindingCurationKey, LicenseFindingCuration>()

    associateByTo(result) { it.key() }

    other.forEach {
        if (!updateOnlyExisting || it.key() in result) result[it.key()] = it
    }

    return result.values.toList()
}

/**
 * Return a copy with the [LicenseFindingCuration]s sorted.
 */
internal fun Collection<LicenseFindingCuration>.sortLicenseFindingCurations(): List<LicenseFindingCuration> =
    sortedBy { curation ->
        curation.path.removePrefix("*").removePrefix("*")
    }

/**
 * This class holds the matcher attributes of a corresponding [LicenseFindingCuration]. It is supposed to be used by the
 * import and export commands to determine whether an existing entry shall be replaced by a new entry.
 */
private data class LicenseFindingCurationKey(
    val path: String,
    val startLines: List<Int> = emptyList(),
    val lineCount: Int? = null,
    val detectedLicense: SpdxExpression?
)

private fun LicenseFindingCuration.key() = LicenseFindingCurationKey(path, startLines, lineCount, detectedLicense)

/**
 * Merge the given [PathExclude]s replacing entries with equal [PathExclude.pattern].
 * If the given [updateOnlyExisting] is true then only entries with matching [PathExclude.pattern] are merged.
 */
internal fun Collection<PathExclude>.mergePathExcludes(
    other: Collection<PathExclude>,
    updateOnlyExisting: Boolean = false
): List<PathExclude> {
    val result = mutableMapOf<String, PathExclude>()

    associateByTo(result) { it.pattern }

    other.forEach {
        if (!updateOnlyExisting || it.pattern in result) result[it.pattern] = it
    }

    return result.values.toList()
}

/**
 * Merge the given [PathInclude]s replacing entries with equal [PathInclude.pattern].
 * If the given [updateOnlyExisting] is true then only entries with matching [PathInclude.pattern] are merged.
 */
internal fun Collection<PathInclude>.mergePathIncludes(
    other: Collection<PathInclude>,
    updateOnlyExisting: Boolean = false
): List<PathInclude> {
    val result = mutableMapOf<String, PathInclude>()

    associateByTo(result) { it.pattern }

    other.forEach {
        if (!updateOnlyExisting || it.pattern in result) result[it.pattern] = it
    }

    return result.values.toList()
}

/**
 * Return a copy with the [PathExclude]s sorted.
 */
internal fun Collection<PathExclude>.sortPathExcludes(): List<PathExclude> =
    sortedBy { it.pattern.removePrefix("*").removePrefix("*") }

/**
 * Merge the given [ScopeExclude]s replacing entries with equal [ScopeExclude.pattern].
 */
internal fun Collection<ScopeExclude>.mergeScopeExcludes(other: Collection<ScopeExclude>): List<ScopeExclude> {
    val result = mutableMapOf<String, ScopeExclude>()

    associateByTo(result) { it.pattern }
    other.associateByTo(result) { it.pattern }

    return result.values.toList()
}

/**
 * Merge the given [RuleViolationResolution]s replacing entries with equal [RuleViolationResolution.message].
 */
internal fun Collection<RuleViolationResolution>.mergeRuleViolationResolutions(
    other: Collection<RuleViolationResolution>
): List<RuleViolationResolution> {
    val result = mutableMapOf<String, RuleViolationResolution>()

    associateByTo(result) { it.message }
    other.associateByTo(result) { it.message }

    return result.values.toList()
}

/**
 * Merge the given [VulnerabilityResolution]s replacing entries with equal [VulnerabilityResolution.id].
 */
internal fun Collection<VulnerabilityResolution>.mergeVulnerabilityResolutions(
    other: Collection<VulnerabilityResolution>
): List<VulnerabilityResolution> {
    val result = mutableMapOf<String, VulnerabilityResolution>()

    associateByTo(result) { it.id }
    other.associateByTo(result) { it.id }

    return result.values.toList()
}

/**
 * Merge the given [RepositoryConfiguration] replacing entries with equal matchers.
 */
internal fun RepositoryConfiguration.merge(other: RepositoryConfiguration): RepositoryConfiguration =
    RepositoryConfiguration(
        includes = Includes(
            paths = includes.paths.mergePathIncludes(other.includes.paths)
        ),
        excludes = Excludes(
            paths = excludes.paths.mergePathExcludes(other.excludes.paths),
            scopes = excludes.scopes.mergeScopeExcludes(other.excludes.scopes)
        ),
        curations = Curations(
            licenseFindings = curations.licenseFindings.mergeLicenseFindingCurations(other.curations.licenseFindings)
        ),
        resolutions = Resolutions(
            issues = resolutions.issues.mergeIssueResolutions(other.resolutions.issues),
            ruleViolations = resolutions.ruleViolations.mergeRuleViolationResolutions(other.resolutions.ruleViolations),
            vulnerabilities = resolutions.vulnerabilities.mergeVulnerabilityResolutions(
                other.resolutions.vulnerabilities
            )
        )
    )

/**
 * Serialize a [PackageConfiguration] to the given [targetFile].
 */
internal fun PackageConfiguration.write(targetFile: File) {
    targetFile.writeValue(this)
}

/**
 * Return a copy of this [PackageCuration] with the comment formamtted.
 */
internal fun PackageCuration.formatComment(): PackageCuration {
    val comment = data.comment ?: return this
    val wrappedComment = comment.wrapAt(COMMENT_WRAP_COLUMN)
    // Ensure at least a single "\n" is contained in the comment to force the YAML mapper to use block quotes.
    val wrappedCommentWithLinebreak = "$wrappedComment\n"

    return copy(data = data.copy(comment = wrappedCommentWithLinebreak))
}

// Wrap at column 120 minus 6 spaces of indentation.
private const val COMMENT_WRAP_COLUMN = 120 - 6

/**
 * Serialize [PackageCuration] to the given [targetFile] as YAML.
 */
internal fun Collection<PackageCuration>.writeAsYaml(targetFile: File) {
    fun createBlockYamlMapper(): ObjectMapper =
        yamlMapper.copy()
            .enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE)
            .disable(YAMLGenerator.Feature.SPLIT_LINES)
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)

    targetFile.parentFile.safeMkdirs()

    val yaml = createBlockYamlMapper()
        .writerWithDefaultPrettyPrinter()
        .writeValueAsString(this)

    targetFile.writeText(yaml)
}

/**
 * Return the scan result matching the given package configuration if any or null otherwise.
 */
internal fun OrtResult.getScanResultFor(packageConfiguration: PackageConfiguration): ScanResult? =
    getScanResultsForId(packageConfiguration.id).find { scanResult ->
        packageConfiguration.matches(packageConfiguration.id, scanResult.provenance)
    }

/**
 * Return the URLs of the analyzed repository and its nested repository associated with their path(s) in the source
 * tree.
 */
internal fun OrtResult.getRepositoryPaths(): Map<String, Set<String>> {
    val result = mutableMapOf(repository.vcsProcessed.url to mutableSetOf(""))

    repository.nestedRepositories.mapValues { (path, vcsInfo) ->
        result.getOrPut(vcsInfo.url) { mutableSetOf() } += path
    }

    // For some Git-repo projects `OrtResult.repository.nestedRepositories´ misses some nested repositories for Git
    // submodules. TODO: Ensure that the OrtResult holds all nested repositories and remove below workaround.
    getProjects().forEach { project ->
        result.getOrPut(project.vcsProcessed.url) { mutableSetOf() } += project.getRepositoryPath(this)
    }

    return result
}

/**
 * Return the path of the repository of this [Project] relative to the analyzer root.
 */
internal fun Project.getRepositoryPath(ortResult: OrtResult): String {
    val projectPath = ortResult.getDefinitionFilePathRelativeToAnalyzerRoot(this).substringBeforeLast("/")
    return projectPath.removeSuffix("/${vcsProcessed.path}")
}
