/*
 * Copyright (C) 2019-2021 HERE Europe B.V.
 * Copyright (C) 2022 Bosch.IO GmbH
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

@file:Suppress("MatchingDeclarationName", "TooManyFunctions")

package org.ossreviewtoolkit.helper.common

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator

import java.io.File
import java.nio.file.Paths
import java.sql.ResultSet

import kotlin.io.path.createTempDirectory

import org.jetbrains.exposed.sql.transactions.TransactionManager

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.downloader.Downloader
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.model.config.Curations
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.IssueResolution
import org.ossreviewtoolkit.model.config.LicenseFindingCuration
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.model.config.RuleViolationResolution
import org.ossreviewtoolkit.model.config.ScopeExclude
import org.ossreviewtoolkit.model.config.VulnerabilityResolution
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.utils.FindingCurationMatcher
import org.ossreviewtoolkit.model.utils.PackageConfigurationProvider
import org.ossreviewtoolkit.model.utils.createLicenseInfoResolver
import org.ossreviewtoolkit.model.writeValue
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.utils.common.encodeOrUnknown
import org.ossreviewtoolkit.utils.common.fileSystemEncode
import org.ossreviewtoolkit.utils.common.isSymbolicLink
import org.ossreviewtoolkit.utils.common.replaceCredentialsInUri
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.common.withoutPrefix
import org.ossreviewtoolkit.utils.ort.CopyrightStatementsProcessor
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.SpdxSingleLicenseExpression

const val ORTH_NAME = "orth"

/**
 * Represents a mapping from repository URLs to list of [PathExclude]s for the respective repository.
 */
internal typealias RepositoryPathExcludes = Map<String, List<PathExclude>>

internal typealias RepositoryLicenseFindingCurations = Map<String, List<LicenseFindingCuration>>

/**
 * Return all files underneath the given [directory].
 */
internal fun findFilesRecursive(directory: File): List<String> {
    require(directory.isDirectory)
    return directory.walk()
        .onEnter { !it.isSymbolicLink() }
        .filter { !it.isSymbolicLink() && it.isFile }
        .mapTo(mutableListOf()) { it.relativeTo(directory).path }
}

/**
 * Search the given [directory] for repositories and return a mapping from repository URLs to the relative paths where
 * each respective repository was found.
 */
internal fun findRepositoryPaths(directory: File): Map<String, Set<String>> {
    require(directory.isDirectory)

    val result = mutableMapOf<String, MutableSet<String>>()

    findRepositories(directory).forEach { (path, vcs) ->
        result.getOrPut(vcs.url.replaceCredentialsInUri()) { mutableSetOf() } += path
    }

    return result
}

/**
 * Search the given [directory] for repositories and return a mapping from paths where each respective repository was
 * found to the corresponding [VcsInfo].
 */
internal fun findRepositories(directory: File): Map<String, VcsInfo> {
    require(directory.isDirectory)

    val workingTree = VersionControlSystem.forDirectory(directory)
    return workingTree?.getNested()?.filter { (path, _) ->
        // Only include nested VCS if they are part of the analyzed directory.
        workingTree.getRootPath().resolve(path).startsWith(directory)
    }.orEmpty()
}

/**
 * Build the file for the split curations.
 */
internal fun getSplitCurationFile(parent: File, packageId: Identifier, fileExtension: String) =
    parent.resolve(packageId.type.encodeOrUnknown())
        .resolve(packageId.namespace.ifBlank { "_" }.fileSystemEncode())
        .resolve("${packageId.name.encodeOrUnknown()}.$fileExtension")

/**
 * Return an approximation for the Set-Cover Problem, see https://en.wikipedia.org/wiki/Set_cover_problem.
 */
internal fun <K, V> greedySetCover(sets: Map<K, Set<V>>): Set<K> {
    val result = mutableSetOf<K>()

    val uncovered = sets.values.flatten().toMutableSet()
    val queue = sets.entries.toMutableSet()

    while (queue.isNotEmpty()) {
        val maxCover = queue.maxByOrNull { it.value.intersect(uncovered).size }!!

        if (uncovered.intersect(maxCover.value).isNotEmpty()) {
            uncovered.removeAll(maxCover.value)
            queue.remove(maxCover)
            result += maxCover.key
        } else {
            break
        }
    }

    return result
}

/**
 * Return an approximated minimal sublist of [this] so that the result still matches the exact same entries of the given
 * [projectScopes].
 */
internal fun List<ScopeExclude>.minimize(projectScopes: List<String>): List<ScopeExclude> {
    val scopeExcludes = associateWith { scopeExclude ->
        projectScopes.filter { scopeExclude.matches(it) }.toSet()
    }

    return greedySetCover(scopeExcludes).toList()
}

/**
 * Fetches the sources from either the VCS or source artifact for the package denoted by
 * the given [id] depending on whether a scan result is present with matching [Provenance].
 */
internal fun OrtResult.fetchScannedSources(id: Identifier): File {
    val tempDir = createTempDirectory(Paths.get("."), ORTH_NAME).toFile()

    val pkg = getPackageOrProject(id)!!.let {
        if (getProvenance(id) is ArtifactProvenance) {
            it.copy(vcs = VcsInfo.EMPTY, vcsProcessed = VcsInfo.EMPTY)
        } else {
            it.copy(sourceArtifact = RemoteArtifact.EMPTY)
        }
    }

    Downloader(DownloaderConfiguration()).download(pkg, tempDir)

    return tempDir
}

/**
 * A processed copyright statement.
 */
internal data class ProcessedCopyrightStatement(
    /**
     * The package containing the copyright statement.
     */
    val packageId: Identifier,

    /**
     * The license associated with the copyright statement.
     */
    val license: SpdxExpression,

    /**
     * The processed copyright statement.
     */
    val statement: String,

    /**
     * The original statement(s) which yield this processed [statement].
     */
    val rawStatements: Set<String>
) {
    init {
        require(rawStatements.isNotEmpty()) { "The set of raw statements must not be empty." }
    }
}

/**
 * Return the processed copyright statements of all packages and projects contained in this [OrtResult]. The statements
 * are processed for each package and license separately for consistency with the notice reporter. Statements contained
 * in the given [copyrightGarbage] are omitted.
 */
internal fun OrtResult.processAllCopyrightStatements(
    omitExcluded: Boolean = true,
    copyrightGarbage: Set<String> = emptySet(),
    addAuthorsToCopyrights: Boolean = false,
    packageConfigurationProvider: PackageConfigurationProvider = PackageConfigurationProvider.EMPTY
): List<ProcessedCopyrightStatement> {
    val result = mutableListOf<ProcessedCopyrightStatement>()

    val processor = CopyrightStatementsProcessor()

    val licenseInfoResolver = createLicenseInfoResolver(
        packageConfigurationProvider = packageConfigurationProvider,
        copyrightGarbage = CopyrightGarbage(copyrightGarbage.toSortedSet()),
        addAuthorsToCopyrights = addAuthorsToCopyrights
    )

    collectProjectsAndPackages().forEach { id ->
        licenseInfoResolver.resolveLicenseInfo(id).forEach inner@{ resolvedLicense ->
            if (omitExcluded && resolvedLicense.isDetectedExcluded) return@inner

            val copyrights = resolvedLicense.getResolvedCopyrights(
                process = false,
                omitExcluded = omitExcluded
            ).flatMap { resolvedCopyright ->
                resolvedCopyright.findings.map { it.statement }
            }

            val processResult = processor.process(copyrights)

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
): Map<Provenance, Map<SpdxSingleLicenseExpression, Set<TextLocation>>> {
    val result = mutableMapOf<Provenance, MutableMap<SpdxSingleLicenseExpression, MutableSet<TextLocation>>>()

    fun getLicenseFindingsCurations(provenance: Provenance): List<LicenseFindingCuration> =
        if (isProject(id)) {
            getLicenseFindingsCurations(id)
        } else {
            packageConfigurationProvider.getPackageConfigurations(id, provenance).flatMap { it.licenseFindingCurations }
        }

    scanner?.results?.scanResults?.get(id)?.forEach { scanResult ->
        val findingsForProvenance = result.getOrPut(scanResult.provenance) { mutableMapOf() }

        scanResult.summary.licenseFindings.let { findings ->
            if (applyCurations) {
                FindingCurationMatcher().applyAll(findings, getLicenseFindingsCurations(scanResult.provenance))
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
            finding.license.decompose().forEach {
                findingsForProvenance.getOrPut(it) { mutableSetOf() } += finding.location
            }
        }
    }

    return result
}

/**
 * Return all license finding curations from [curations] represented as [RepositoryLicenseFindingCurations].
 */
internal fun getLicenseFindingCurationsByRepository(
    curations: Collection<LicenseFindingCuration>,
    nestedRepositories: Map<String, VcsInfo>
): RepositoryLicenseFindingCurations {
    val result = mutableMapOf<String, MutableList<LicenseFindingCuration>>()

    nestedRepositories.forEach { (path, vcs) ->
        val pathExcludesForRepository = result.getOrPut(vcs.url) { mutableListOf() }
        curations.forEach { curation ->
            curation.path.withoutPrefix("$path/")?.let {
                pathExcludesForRepository += curation.copy(path = it)
            }
        }
    }

    return result.mapValues { excludes -> excludes.value.sortedBy { it.path } }.toSortedMap()
}

/**
 * Return all license [Identifier]s which triggered at least one [RuleViolation] with a [severity] contained in the
 * given [severity] collection, associated with the rule names of all corresponding violated rules.
 */
internal fun OrtResult.getViolatedRulesByLicense(
    id: Identifier,
    severity: Collection<Severity> = enumValues<Severity>().asList()
): Map<SpdxSingleLicenseExpression, List<String>> =
    getRuleViolations()
        .filter { it.pkg == id && it.severity in severity && it.license != null }
        .groupBy { it.license!! }
        .mapValues { (_, ruleViolations) -> ruleViolations.map { it.rule } }

/**
 * Return the Package with the given [id] denoting either a [Project] or a [Package].
 */
internal fun OrtResult.getPackageOrProject(id: Identifier): Package? =
    getProject(id)?.toPackage() ?: getPackage(id)?.pkg

/**
 * Return the [Provenance] of the first scan result matching the given [id] or null if there is no match.
 */
internal fun OrtResult.getProvenance(id: Identifier): Provenance? = getScanResultsForId(id).firstOrNull()?.provenance

/**
 * Return all issues from scan results. Issues for excludes [Project]s or [Package]s are not returned if and only if
 * the given [omitExcluded] is true.
 */
internal fun OrtResult.getScanIssues(omitExcluded: Boolean = false): List<OrtIssue> {
    val result = mutableListOf<OrtIssue>()

    scanner?.results?.scanResults?.forEach { (id, results) ->
        if (!omitExcluded || !isExcluded(id)) {
            results.forEach { scanResult ->
                result += scanResult.summary.issues
            }
        }
    }

    return result
}

/**
 * Return all path excludes from this [OrtResult] represented as [RepositoryPathExcludes].
 */
internal fun OrtResult.getRepositoryPathExcludes(): RepositoryPathExcludes {
    fun isDefinitionsFile(pathExclude: PathExclude) = PackageManager.ALL.any {
        it.matchersForDefinitionFiles.any { matcher ->
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
 * Execute the raw SQL statement and map it using [transform].
 */
internal fun <T : Any> String.execAndMap(transform: (ResultSet) -> T): List<T> {
    val result = mutableListOf<T>()
    TransactionManager.current().exec(this) { resultSet ->
        while (resultSet.next()) {
            result += transform(resultSet)
        }
    }

    return result
}

/**
 * Return all path excludes from [pathExcludes] represented as [RepositoryPathExcludes].
 */
internal fun getPathExcludesByRepository(
    pathExcludes: Collection<PathExclude>,
    nestedRepositories: Map<String, VcsInfo>
): RepositoryPathExcludes {
    val result = mutableMapOf<String, MutableList<PathExclude>>()

    nestedRepositories.forEach { (path, vcs) ->
        val pathExcludesForRepository = result.getOrPut(vcs.url) { mutableListOf() }
        pathExcludes.forEach { pathExclude ->
            pathExclude.pattern.withoutPrefix("$path/")?.let {
                pathExcludesForRepository += pathExclude.copy(pattern = it)
            }
        }
    }

    return result.mapValues { excludes -> excludes.value.sortedBy { it.pattern } }.toSortedMap()
}

/**
 * Return all unresolved rule violations.
 */
internal fun OrtResult.getUnresolvedRuleViolations(): List<RuleViolation> {
    val resolutions = getResolutions().ruleViolations
    val violations = evaluator?.violations.orEmpty()

    return violations.filter { violation ->
        !resolutions.any { it.matches(violation) }
    }
}

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
internal fun RepositoryConfiguration.replaceRuleViolationResolutions(ruleViolations: List<RuleViolationResolution>):
        RepositoryConfiguration = copy(resolutions = resolutions.copy(ruleViolations = ruleViolations))

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
            merge(repositoryUrl, curation, false)
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
            merge(repositoryUrl, pathExclude, false)
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

private fun LicenseFindingCuration.key() =
    LicenseFindingCurationKey(path, startLines, lineCount, detectedLicense)

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
 * Return a copy with the [PathExclude]s sorted.
 */
internal fun Collection<PathExclude>.sortPathExcludes(): List<PathExclude> =
    sortedBy { it.pattern.removePrefix("*").removePrefix("*") }

/**
 * Merge the given [ScopeExclude]s replacing entries with equal [ScopeExclude.pattern].
 */
internal fun Collection<ScopeExclude>.mergeScopeExcludes(
    other: Collection<ScopeExclude>
): List<ScopeExclude> {
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
internal fun RepositoryConfiguration.merge(
    other: RepositoryConfiguration
): RepositoryConfiguration =
    RepositoryConfiguration(
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

// Wrap at column 120 minus 6 spaces of indentation.
private const val COMMENT_WRAP_COLUMN = 120 - 6

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

/**
 * Read a list of [PackageCuration]s from the given [file].
 */
internal fun readPackageCurations(file: File): List<PackageCuration> =
    if (file.isFile) {
        file.readValue()
    } else {
        emptyList()
    }

/**
 * Serialize [PackageCuration] to the given [targetFile] as YAML.
 */
internal fun Collection<PackageCuration>.writeAsYaml(targetFile: File) {
    targetFile.parentFile.safeMkdirs()

    val yaml = createBlockYamlMapper()
        .writerWithDefaultPrettyPrinter()
        .writeValueAsString(this)

    targetFile.writeText(yaml)
}

private fun createBlockYamlMapper(): ObjectMapper =
    yamlMapper.copy()
        .enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE)
        .disable(YAMLGenerator.Feature.SPLIT_LINES)
        .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)

internal fun importPathExcludes(
    repositoryPaths: Map<String, Set<String>>,
    pathExcludesFile: File,
    vcsUrlMapping: VcsUrlMapping
): List<PathExclude> {
    val result = mutableListOf<PathExclude>()
    val pathExcludes = pathExcludesFile.readValue<RepositoryPathExcludes>()

    println("Found ${repositoryPaths.size} repositories in ${repositoryPaths.values.sumOf { it.size }} locations.")
    println("Found ${pathExcludes.values.sumOf { it.size }} excludes for ${pathExcludes.size} repositories.")

    repositoryPaths.mapKeys { vcsUrlMapping.map(it.key) }.forEach { (vcsUrl, relativePaths) ->
        pathExcludes[vcsUrl]?.let { pathExcludesForRepository ->
            pathExcludesForRepository.forEach { pathExclude ->
                relativePaths.forEach { path ->
                    result += pathExclude.copy(pattern = path + '/' + pathExclude.pattern)
                }
            }
        }
    }

    return result
}

internal fun importLicenseFindingCurations(
    repositoryPaths: Map<String, Set<String>>,
    licenseFindingCurationsFile: File,
    vcsUrlMapping: VcsUrlMapping
): List<LicenseFindingCuration> {
    val curations = licenseFindingCurationsFile.readValue<RepositoryLicenseFindingCurations>()
    val result = mutableListOf<LicenseFindingCuration>()

    println("Found ${repositoryPaths.size} repositories in ${repositoryPaths.values.sumOf { it.size }} locations.")
    println("Found ${curations.values.sumOf { it.size }} curations for ${curations.size} repositories.")

    repositoryPaths.mapKeys { vcsUrlMapping.map(it.key) }.forEach { (vcsUrl, relativePaths) ->
        curations[vcsUrl]?.let { curationsForRepository ->
            curationsForRepository.forEach { curation ->
                relativePaths.forEach { path ->
                    result += curation.copy(path = path + '/' + curation.path)
                }
            }
        }
    }

    return result
}

/**
 * Return the scan result matching the given package configuration if any or null otherwise.
 */
internal fun OrtResult.getScanResultFor(packageConfiguration: PackageConfiguration): ScanResult? =
    getScanResultsForId(packageConfiguration.id).find { scanResult ->
        packageConfiguration.matches(packageConfiguration.id, scanResult.provenance)
    }

/**
 * Read [ortFile] into an [OrtResult] and return it. Make sure that information about project scopes is available
 * (by calling [OrtResult.withResolvedScopes]), so that it can be processed.
 */
internal fun readOrtResult(ortFile: File): OrtResult = ortFile.readValue<OrtResult>().withResolvedScopes()

/**
 * Write the [ortResult] to [file].
 */
internal fun writeOrtResult(ortResult: OrtResult, file: File): Unit = file.writeValue(ortResult)

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
