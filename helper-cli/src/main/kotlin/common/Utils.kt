/*
 * Copyright (C) 2019-2021 HERE Europe B.V.
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

@file:Suppress("MatchingDeclarationName", "TooManyFunctions")

package org.ossreviewtoolkit.helper.common

import java.io.File
import java.io.IOException
import java.nio.file.Paths

import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile

import okhttp3.Request

import okio.buffer
import okio.sink

import org.ossreviewtoolkit.analyzer.Analyzer
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.downloader.Downloader
import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
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
import org.ossreviewtoolkit.model.utils.SimplePackageConfigurationProvider
import org.ossreviewtoolkit.model.utils.createLicenseInfoResolver
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.spdx.SpdxExpression
import org.ossreviewtoolkit.spdx.SpdxSingleLicenseExpression
import org.ossreviewtoolkit.utils.CopyrightStatementsProcessor
import org.ossreviewtoolkit.utils.ORT_NAME
import org.ossreviewtoolkit.utils.OkHttpClientHelper
import org.ossreviewtoolkit.utils.isSymbolicLink
import org.ossreviewtoolkit.utils.safeMkdirs
import org.ossreviewtoolkit.utils.stripCredentialsFromUrl
import org.ossreviewtoolkit.utils.withoutPrefix

const val ORTH_NAME = "orth"

/**
 * Represents a mapping from repository URLs to list of [PathExclude]s for the respective repository.
 */
internal typealias RepositoryPathExcludes = Map<String, List<PathExclude>>

internal typealias RepositoryLicenseFindingCurations = Map<String, List<LicenseFindingCuration>>

/**
 * Try to download the [url] and return the downloaded temporary file. The file is automatically deleted on exit. If the
 * download fails, throw an [IOException].
 */
internal fun download(url: String): File {
    val request = Request.Builder()
        // Disable transparent gzip, otherwise we might end up writing a tar file to disk and expecting to
        // find a tar.gz file, thus failing to unpack the archive.
        // See https://github.com/square/okhttp/blob/parent-3.10.0/okhttp/src/main/java/okhttp3/internal/ \
        // http/BridgeInterceptor.java#L79
        .addHeader("Accept-Encoding", "identity")
        .get()
        .url(url)
        .build()

    OkHttpClientHelper.execute(request).use { response ->
        val body = response.body
        if (!response.isSuccessful || body == null) {
            throw IOException(response.message)
        }

        // Use the filename from the request for the last redirect.
        val tempFileName = response.request.url.pathSegments.last()
        return createTempFile(ORT_NAME, tempFileName).toFile().also { tempFile ->
            tempFile.sink().buffer().use { it.writeAll(body.source()) }
            tempFile.deleteOnExit()
        }
    }
}

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
        result.getOrPut(vcs.url.stripCredentialsFromUrl()) { mutableSetOf() } += path
    }

    return result
}

/**
 * Search the given [directory] for repositories and return a mapping from paths where each respective repository was
 * found to the corresponding [VcsInfo].
 */
internal fun findRepositories(directory: File): Map<String, VcsInfo> {
    require(directory.isDirectory)

    val analyzer = Analyzer(AnalyzerConfiguration(ignoreToolVersions = true, allowDynamicVersions = true))
    val ortResult = analyzer.analyze(absoluteProjectPath = directory, packageManagers = emptyList())

    return ortResult.repository.nestedRepositories
}

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
    packageConfigurationProvider: PackageConfigurationProvider = SimplePackageConfigurationProvider.EMPTY
): List<ProcessedCopyrightStatement> {
    val result = mutableListOf<ProcessedCopyrightStatement>()

    val processor = CopyrightStatementsProcessor()

    val licenseInfoResolver = createLicenseInfoResolver(
        packageConfigurationProvider = packageConfigurationProvider,
        copyrightGarbage = CopyrightGarbage(copyrightGarbage.toSortedSet())
    )

    getProjectAndPackageIds().forEach { id ->
        licenseInfoResolver.resolveLicenseInfo(id).forEach innerForEach@{ resolvedLicense ->
            if (omitExcluded && resolvedLicense.isDetectedExcluded) return@innerForEach

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
            packageConfigurationProvider.getPackageConfiguration(id, provenance)?.licenseFindingCurations.orEmpty()
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
 * Return the first [Provenance] matching the given [id] or null if there is no match.
 */
internal fun OrtResult.getProvenance(id: Identifier): Provenance? {
    val pkg = getPackageOrProject(id)!!

    scanner?.results?.scanResults?.forEach { (_, results) ->
        results.forEach { scanResult ->
            if (scanResult.provenance.matches(pkg)) {
                return scanResult.provenance
            }
        }
    }

    return null
}

/**
 * Return all issues from scan results. Issues for excludes [Project]s or [Package]s are not returned if and only if
 * the given [omitExcluded] is true.
 */
fun OrtResult.getScanIssues(omitExcluded: Boolean = false): List<OrtIssue> {
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
 * [respositoryConfigurationFile].
 */
fun OrtResult.replaceConfig(respositoryConfigurationFile: File?): OrtResult =
    respositoryConfigurationFile?.let {
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
 * Serialize a [RepositoryConfiguration] as YAML to the given target [File].
 */
internal fun RepositoryConfiguration.writeAsYaml(targetFile: File) {
    targetFile.absoluteFile.parentFile.safeMkdirs()
    yamlMapper.writeValue(targetFile, this)
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
    val result: MutableMap<String, MutableMap<LicenseFindingCurationHashKey, LicenseFindingCuration>> = mutableMapOf()

    fun merge(repositoryUrl: String, curation: LicenseFindingCuration, updateOnlyUpdateExisting: Boolean = false) {
        if (updateOnlyUpdateExisting && !result.containsKey(repositoryUrl)) {
            return
        }

        val curations = result.getOrPut(repositoryUrl, { mutableMapOf() })

        val key = curation.hashKey()
        if (updateOnlyUpdateExisting && !curations.containsKey(key)) {
            return
        }

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
 * Serialize this [RepositoryLicenseFindingCurations] to the given [targetFile] as YAML.
 */
@JvmName("writeRepositoryLicenseFindingCurationsAsYaml")
internal fun RepositoryLicenseFindingCurations.writeAsYaml(targetFile: File) {
    targetFile.parentFile.apply { safeMkdirs() }

    yamlMapper.writeValue(
        targetFile,
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
        if (updateOnlyUpdateExisting && !result.containsKey(repositoryUrl)) {
            return
        }

        val pathExcludes = result.getOrPut(repositoryUrl, { mutableMapOf() })
        if (updateOnlyUpdateExisting && !result.containsKey(pathExclude.pattern)) {
            return
        }

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
 * Serialize this [RepositoryPathExcludes] to the given [targetFile] as YAML.
 */
@JvmName("writeRepositoryPathExcludesAsYaml")
internal fun RepositoryPathExcludes.writeAsYaml(targetFile: File) {
    targetFile.parentFile.apply { safeMkdirs() }

    yamlMapper.writeValue(
        targetFile,
        mapValues { (_, pathExcludes) ->
            pathExcludes.sortedBy { it.pattern }
        }.toSortedMap()
    )
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
    val result = mutableMapOf<LicenseFindingCurationHashKey, LicenseFindingCuration>()

    associateByTo(result) { it.hashKey() }

    other.forEach {
        if (!updateOnlyExisting || result.containsKey(it.hashKey())) {
            result[it.hashKey()] = it
        }
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

private data class LicenseFindingCurationHashKey(
    val path: String,
    val startLines: List<Int> = emptyList(),
    val lineCount: Int? = null,
    val detectedLicense: SpdxExpression?,
    val concludedLicense: SpdxExpression
)

private fun LicenseFindingCuration.hashKey() =
    LicenseFindingCurationHashKey(path, startLines, lineCount, detectedLicense, concludedLicense)

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
        if (!updateOnlyExisting || result.containsKey(it.pattern)) {
            result[it.pattern] = it
        }
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
            curations.licenseFindings.mergeLicenseFindingCurations(other.curations.licenseFindings)
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
 * Serialize a [PackageConfiguration] as YAML to the given target [File].
 */
internal fun PackageConfiguration.writeAsYaml(targetFile: File) {
    targetFile.absoluteFile.parentFile.safeMkdirs()
    yamlMapper.writeValue(targetFile, this)
}

internal fun importPathExcludes(sourceCodeDir: File, pathExcludesFile: File): List<PathExclude> {
    println("Analyzing $sourceCodeDir...")
    val repositoryPaths = findRepositoryPaths(sourceCodeDir)
    println("Found ${repositoryPaths.size} repositories in ${repositoryPaths.values.sumBy { it.size }} locations.")

    println("Loading $pathExcludesFile...")
    val pathExcludes = pathExcludesFile.readValue<RepositoryPathExcludes>()
    println("Found ${pathExcludes.values.sumBy { it.size }} excludes for ${pathExcludes.size} repositories.")

    val result = mutableListOf<PathExclude>()

    repositoryPaths.forEach { (vcsUrl, relativePaths) ->
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
    sourceCodeDir: File,
    licenseFindingCurationsFile: File
): List<LicenseFindingCuration> {
    println("Analyzing $sourceCodeDir...")
    val repositoryPaths = findRepositoryPaths(sourceCodeDir)
    println("Found ${repositoryPaths.size} repositories in ${repositoryPaths.values.sumBy { it.size }} locations.")

    println("Loading $licenseFindingCurationsFile...")
    val curations = licenseFindingCurationsFile.readValue<RepositoryLicenseFindingCurations>()
    println("Found ${curations.values.sumBy { it.size }} curations for ${curations.size} repositories.")

    val result = mutableListOf<LicenseFindingCuration>()

    repositoryPaths.forEach { (vcsUrl, relativePaths) ->
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
