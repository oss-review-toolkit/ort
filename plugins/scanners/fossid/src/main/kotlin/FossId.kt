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

package org.ossreviewtoolkit.plugins.scanners.fossid

import java.io.IOException
import java.time.Instant

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.clients.fossid.FossIdRestService
import org.ossreviewtoolkit.clients.fossid.addComponentIdentification
import org.ossreviewtoolkit.clients.fossid.addFileComment
import org.ossreviewtoolkit.clients.fossid.checkDownloadStatus
import org.ossreviewtoolkit.clients.fossid.checkResponse
import org.ossreviewtoolkit.clients.fossid.createIgnoreRule
import org.ossreviewtoolkit.clients.fossid.createProject
import org.ossreviewtoolkit.clients.fossid.createScan
import org.ossreviewtoolkit.clients.fossid.deleteScan
import org.ossreviewtoolkit.clients.fossid.downloadFromGit
import org.ossreviewtoolkit.clients.fossid.getProject
import org.ossreviewtoolkit.clients.fossid.listIdentifiedFiles
import org.ossreviewtoolkit.clients.fossid.listIgnoreRules
import org.ossreviewtoolkit.clients.fossid.listIgnoredFiles
import org.ossreviewtoolkit.clients.fossid.listMarkedAsIdentifiedFiles
import org.ossreviewtoolkit.clients.fossid.listMatchedLines
import org.ossreviewtoolkit.clients.fossid.listPendingFiles
import org.ossreviewtoolkit.clients.fossid.listScansForProject
import org.ossreviewtoolkit.clients.fossid.listSnippets
import org.ossreviewtoolkit.clients.fossid.markAsIdentified
import org.ossreviewtoolkit.clients.fossid.model.Project
import org.ossreviewtoolkit.clients.fossid.model.Scan
import org.ossreviewtoolkit.clients.fossid.model.result.MatchType
import org.ossreviewtoolkit.clients.fossid.model.result.MatchedLines
import org.ossreviewtoolkit.clients.fossid.model.rules.IgnoreRule
import org.ossreviewtoolkit.clients.fossid.model.rules.RuleScope
import org.ossreviewtoolkit.clients.fossid.model.rules.RuleType
import org.ossreviewtoolkit.clients.fossid.model.status.DownloadStatus
import org.ossreviewtoolkit.clients.fossid.model.status.ScanStatus
import org.ossreviewtoolkit.clients.fossid.runScan
import org.ossreviewtoolkit.clients.fossid.unmarkAsIdentified
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.SnippetFinding
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.config.snippet.SnippetChoice
import org.ossreviewtoolkit.model.config.snippet.SnippetChoiceReason
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.scanner.PackageScannerWrapper
import org.ossreviewtoolkit.scanner.ProvenanceScannerWrapper
import org.ossreviewtoolkit.scanner.ScanContext
import org.ossreviewtoolkit.scanner.ScannerMatcher
import org.ossreviewtoolkit.scanner.ScannerWrapperConfig
import org.ossreviewtoolkit.scanner.ScannerWrapperFactory
import org.ossreviewtoolkit.scanner.provenance.NestedProvenance
import org.ossreviewtoolkit.utils.common.Options
import org.ossreviewtoolkit.utils.common.enumSetOf
import org.ossreviewtoolkit.utils.common.replaceCredentialsInUri
import org.ossreviewtoolkit.utils.ort.showStackTrace

import org.semver4j.Semver

/**
 * A wrapper for the [FossID](https://fossid.com/) snippet scanner.
 *
 * This scanner can be configured in [ScannerConfiguration.config]. For the available options see [FossIdConfig].
 *
 * This scanner was implemented before the introduction of [provenance based scanning][ProvenanceScannerWrapper].
 * Therefore, it implements the [PackageScannerWrapper] interface for backward compatibility, even though FossID itself
 * gets a Git repository URL as input and would be a good match for [ProvenanceScannerWrapper].
 */
@Suppress("LargeClass", "TooManyFunctions")
class FossId internal constructor(
    override val name: String,
    private val config: FossIdConfig,
    private val wrapperConfig: ScannerWrapperConfig
) : PackageScannerWrapper {
    companion object {
        @JvmStatic
        private val PROJECT_NAME_REGEX = Regex("""^.*/([\w.\-]+?)(?:\.git)?$""")

        @JvmStatic
        private val GIT_FETCH_DONE_REGEX = Regex("-> FETCH_HEAD(?: Already up to date.)*$")

        /**
         * A regular expression to extract the artifact and version from a Purl returned by FossID.
         */
        @JvmStatic
        private val SNIPPET_PURL_REGEX = Regex("^.*/(?<artifact>[^@]+)@(?<version>.+)")

        @JvmStatic
        private val WAIT_DELAY = 10.seconds

        @JvmStatic
        internal val SCAN_CODE_KEY = "scancode"

        @JvmStatic
        internal val SCAN_ID_KEY = "scanid"

        @JvmStatic
        internal val SERVER_URL_KEY = "serverurl"

        @JvmStatic
        internal val PROJECT_REVISION_LABEL = "projectVcsRevision"

        @JvmStatic
        internal val SNIPPET_DATA_ID = "id"

        @JvmStatic
        internal val SNIPPET_DATA_MATCH_TYPE = "matchType"

        @JvmStatic
        internal val SNIPPET_DATA_RELEASE_DATE = "releaseDate"

        @JvmStatic
        internal val SNIPPET_DATA_MATCHED_LINE_SOURCE = "matchedLinesSource"

        @JvmStatic
        internal val SNIPPET_DATA_MATCHED_LINE_SNIPPET = "matchedLinesSnippet"

        /**
         * The scan states for which a scan can be triggered.
         */
        @JvmStatic
        private val SCAN_STATE_FOR_TRIGGER = enumSetOf(ScanStatus.NOT_STARTED, ScanStatus.NEW)

        /**
         * Convert a Git repository URL to a valid project name, e.g.
         * https://github.com/jshttp/mime-types.git -> mime-types
         */
        fun convertGitUrlToProjectName(gitRepoUrl: String): String {
            val projectNameMatcher = PROJECT_NAME_REGEX.matchEntire(gitRepoUrl)

            requireNotNull(projectNameMatcher) { "Git repository URL $gitRepoUrl does not contain a project name." }

            val projectName = projectNameMatcher.groupValues[1]

            logger.info { "Found project name '$projectName' in URL $gitRepoUrl." }

            return projectName
        }

        /**
         * Generate a list of pairs to be passed as parameters when starting a new delta scan for [existingScanCode].
         */
        internal fun deltaScanRunParameters(existingScanCode: String): Array<Pair<String, String>> =
            arrayOf(
                "reuse_identification" to "1",
                "identification_reuse_type" to "specific_scan",
                "specific_code" to existingScanCode
            )
    }

    class Factory : ScannerWrapperFactory<FossIdConfig>("FossId") {
        override fun create(config: FossIdConfig, wrapperConfig: ScannerWrapperConfig) =
            FossId(type, config, wrapperConfig)

        override fun parseConfig(options: Options, secrets: Options) = FossIdConfig.create(options, secrets)
    }

    /**
     * The qualifier of a scan when delta scans are enabled.
     */
    enum class DeltaTag {
        /**
         * Qualifier used when there is no scan and the first one is created.
         */
        ORIGIN,

        /**
         * Qualifier used for all the scans after the first one.
         */
        DELTA
    }

    private val namingProvider = config.createNamingProvider()
    private val urlProvider = config.createUrlProvider()

    // A list of all scans created in an ORT run, to be able to delete them in case of error.
    // The reasoning is that either all these scans are successful, either none is created at all (clean slate).
    // A use case is that an ORT run is created regularly e.g. nightly, and we want to have exactly the same amount
    // of scans for each package.
    private val createdScans = mutableSetOf<String>()

    private val service = FossIdRestService.create(config.serverUrl)

    override val version = service.version
    override val configuration = ""

    override val matcher: ScannerMatcher? = null

    override val readFromStorage by lazy { wrapperConfig.readFromStorageWithDefault(matcher) }

    override val writeToStorage by lazy { wrapperConfig.writeToStorageWithDefault(matcher) }

    private suspend fun getProject(projectCode: String): Project? =
        service.getProject(config.user, config.apiKey, projectCode).run {
            when {
                error == null && data != null -> {
                    logger.info { "Project '$projectCode' exists." }
                    data
                }

                error == "Project does not exist" && status == 0 -> {
                    logger.info { "Project '$projectCode' does not exist." }
                    null
                }

                else -> throw IOException("Could not get project. Additional information : $error")
            }
        }

    /**
     * Create a [ScanSummary] containing a single [issue] started at [startTime] and ended now.
     */
    private fun createSingleIssueSummary(startTime: Instant, issue: Issue) =
        ScanSummary.EMPTY.copy(startTime = startTime, endTime = Instant.now(), issues = listOf(issue))

    override fun scanPackage(nestedProvenance: NestedProvenance?, context: ScanContext): ScanResult {
        val startTime = Instant.now()

        // FossId actually never uses the provenance determined by the scanner, but determines the source code to
        // download itself based on the passed VCS URL and revision, disregarding any VCS path.
        val pkg = context.coveredPackages.first()
        val provenance = pkg.vcsProcessed.revision.takeUnless { it.isBlank() }
            ?.let { RepositoryProvenance(pkg.vcsProcessed, it) } ?: UnknownProvenance

        val issueMessage = when {
            pkg.vcsProcessed.type != VcsType.GIT ->
                "Package '${pkg.id.toCoordinates()}' uses VCS type '${pkg.vcsProcessed.type}', but only " +
                    "${VcsType.GIT} is supported."

            pkg.vcsProcessed.revision.isEmpty() ->
                "Package '${pkg.id.toCoordinates()}' has an empty VCS revision and cannot be scanned."

            else -> null
        }

        if (issueMessage != null) {
            val issue = createAndLogIssue(name, issueMessage, Severity.WARNING)
            val summary = createSingleIssueSummary(startTime, issue = issue)
            return ScanResult(provenance, details, summary)
        }

        val url = pkg.vcsProcessed.url
        val revision = pkg.vcsProcessed.revision
        val projectName = convertGitUrlToProjectName(url)

        val result = runBlocking {
            try {
                val projectCode = namingProvider.createProjectCode(projectName)

                if (getProject(projectCode) == null) {
                    logger.info { "Creating project '$projectCode'..." }

                    service.createProject(config.user, config.apiKey, projectCode, projectCode)
                        .checkResponse("create project")
                }

                val scans = service.listScansForProject(config.user, config.apiKey, projectCode)
                    .checkResponse("list scans for project").data
                checkNotNull(scans)

                val result = if (config.deltaScans) {
                    checkAndCreateDeltaScan(scans, url, revision, projectCode, projectName, context)
                } else {
                    checkAndCreateScan(scans, url, revision, projectCode, projectName, context)
                }

                if (config.waitForResult && provenance is RepositoryProvenance) {
                    val snippetChoices = context.snippetChoices.firstOrNull {
                        it.provenance.url == provenance.vcsInfo.url
                    }

                    logger.info {
                        val choices = snippetChoices?.choices?.filter {
                            it.choice.reason == SnippetChoiceReason.ORIGINAL_FINDING
                        }.orEmpty()

                        "Repository ${provenance.vcsInfo.url} has ${choices.size} snippet choice(s)."
                    }

                    logger.info {
                        val falsePositivesLocationsCount = snippetChoices?.choices?.count {
                            it.choice.reason == SnippetChoiceReason.NO_RELEVANT_FINDING
                        } ?: "N/A"

                        "Repository ${provenance.vcsInfo.url} has $falsePositivesLocationsCount location(s) with " +
                            "false positives."
                    }

                    val rawResults = getRawResults(result.scanCode, snippetChoices?.choices.orEmpty())
                    createResultSummary(
                        startTime,
                        provenance,
                        rawResults,
                        result,
                        context.detectedLicenseMapping,
                        snippetChoices?.choices.orEmpty()
                    )
                } else {
                    val issue = createAndLogIssue(
                        source = name,
                        message = "Package '${pkg.id.toCoordinates()}' has been scanned in asynchronous mode. " +
                            "Scan results need to be inspected on the server instance.",
                        severity = Severity.HINT
                    )
                    val summary = createSingleIssueSummary(startTime, issue = issue)

                    ScanResult(
                        provenance,
                        details,
                        summary,
                        mapOf(
                            SCAN_CODE_KEY to result.scanCode,
                            SCAN_ID_KEY to result.scanId,
                            SERVER_URL_KEY to config.serverUrl
                        )
                    )
                }
            } catch (e: IllegalStateException) {
                e.showStackTrace()

                val issue = createAndLogIssue(
                    source = name,
                    message = "Failed to scan package '${pkg.id.toCoordinates()}' from $url."
                )
                val summary = createSingleIssueSummary(startTime, issue = issue)

                if (!config.keepFailedScans) {
                    createdScans.forEach { code ->
                        logger.warn { "Deleting scan '$code' during exception cleanup." }
                        deleteScan(code)
                    }
                }

                ScanResult(provenance, details, summary)
            }
        }

        logger.info {
            val duration = with(result.summary) { java.time.Duration.between(startTime, endTime).toKotlinDuration() }
            "Scan has been performed. Total time was $duration."
        }

        return result
    }

    /**
     * Find the latest [Scan] in this list with a finished state. If necessary, wait for a scan to finish. Note that
     * this function expects that [recentScansForRepository] has been applied to this list for the current
     * repository.
     */
    private suspend fun List<Scan>.findLatestPendingOrFinishedScan(): Scan? =
        find { scan ->
            val scanCode = requireNotNull(scan.code) {
                "The code for an existing scan must not be null."
            }

            val response = service.checkScanStatus(config.user, config.apiKey, scanCode)
                .checkResponse("check scan status", false)
            when (response.data?.status) {
                ScanStatus.FINISHED -> true

                null, ScanStatus.NOT_STARTED, ScanStatus.INTERRUPTED, ScanStatus.NEW, ScanStatus.FAILED -> false

                ScanStatus.STARTED, ScanStatus.STARTING, ScanStatus.RUNNING, ScanStatus.SCANNING, ScanStatus.AUTO_ID,

                ScanStatus.QUEUED -> {
                    logger.warn {
                        "Found a previous scan which is still running. Will ignore the 'waitForResult' option and " +
                            "wait..."
                    }
                    waitScanComplete(scanCode)
                    true
                }
            }
        }

    /**
     * Filter this list of [Scan]s for the repository defined by [url] and Git [reference<]. If no scan is found with
     * these criteria, search for scans of the default branch [defaultBranch]. If still no scan is found, all scans for
     * this repository are taken, filtered by an optional [revision].
     * Scans returned are sorted by scan ID, so that the most recent scan comes first and the oldest scan comes last.
     */
    private fun List<Scan>.recentScansForRepository(
        url: String,
        revision: String? = null,
        projectRevision: String? = null,
        defaultBranch: String? = null
    ): List<Scan> {
        val scans = filter {
            val isArchived = it.isArchived == true
            // The scans in the server contain the url with the credentials, so we have to remove it for the
            // comparison. If we don't, the scans won't be matched if the password changes!
            val urlWithoutCredentials = it.gitRepoUrl?.replaceCredentialsInUri()
            !isArchived && urlWithoutCredentials == url
        }.sortedByDescending { it.id }

        return scans.filter { scan -> projectRevision == scan.comment }.ifEmpty {
            logger.warn {
                "No recent scan found for project revision $projectRevision. Falling back to default branch scans."
            }

            scans.filter { scan ->
                defaultBranch?.let { scan.comment == defaultBranch } == true
            }.ifEmpty {
                logger.warn { "No recent default branch scan found. Falling back to old behavior." }

                scans.filter { revision == null || it.gitBranch == revision }
            }
        }
    }

    /**
     * Call FossID service, initiate a scan and return scan data: Scan Code and Scan Id
     */
    private suspend fun checkAndCreateScan(
        scans: List<Scan>,
        url: String,
        revision: String,
        projectCode: String,
        projectName: String,
        context: ScanContext
    ): FossIdResult {
        val existingScan = scans.recentScansForRepository(url, revision = revision).findLatestPendingOrFinishedScan()

        val result = if (existingScan == null) {
            logger.info { "No scan found for $url and revision $revision. Creating scan..." }

            val scanCode = namingProvider.createScanCode(projectName = projectName, branch = revision)
            val newUrl = urlProvider.getUrl(url)
            val scanId = createScan(projectCode, scanCode, newUrl, revision)

            logger.info { "Initiating the download..." }
            service.downloadFromGit(config.user, config.apiKey, scanCode)
                .checkResponse("download data from Git", false)

            val issues = createIgnoreRules(scanCode, context.excludes)

            FossIdResult(scanCode, scanId, issues)
        } else {
            logger.info { "Scan '${existingScan.code}' found for $url and revision $revision." }

            val existingScanCode = requireNotNull(existingScan.code) {
                "The code for an existing scan must not be null."
            }

            FossIdResult(existingScanCode, existingScan.id.toString())
        }

        if (config.waitForResult) checkScan(result.scanCode)

        return result
    }

    /**
     * Call FossID service, initiate a delta scan and return scan data: Scan Code and Scan Id
     */
    private suspend fun checkAndCreateDeltaScan(
        scans: List<Scan>,
        url: String,
        revision: String,
        projectCode: String,
        projectName: String,
        context: ScanContext
    ): FossIdResult {
        val projectRevision = context.labels[PROJECT_REVISION_LABEL]

        val vcs = requireNotNull(VersionControlSystem.forUrl(url))

        val defaultBranch = vcs.getDefaultBranchName(url)
        logger.info { "Default branch is '$defaultBranch'." }

        if (projectRevision == null) {
            logger.warn { "No project revision has been given." }
        } else {
            logger.info { "Project revision is '$projectRevision'." }
        }

        val urlWithoutCredentials = url.replaceCredentialsInUri()
        if (urlWithoutCredentials != url) {
            logger.warn {
                "The URL should not contain credentials as its interaction with delta scans is unpredictable."
            }
        }

        val mappedUrl = urlProvider.getUrl(urlWithoutCredentials)
        val mappedUrlWithoutCredentials = mappedUrl.replaceCredentialsInUri()

        // we ignore the revision because we want to do a delta scan
        val recentScans = scans.recentScansForRepository(
            mappedUrlWithoutCredentials,
            projectRevision = projectRevision,
            defaultBranch = defaultBranch
        )

        logger.info { "Found ${recentScans.size} scans." }

        val existingScan = recentScans.findLatestPendingOrFinishedScan()

        val scanCode = if (existingScan == null) {
            logger.info {
                "No scan found for $mappedUrlWithoutCredentials and revision $revision. Creating origin scan..."
            }
            namingProvider.createScanCode(projectName, DeltaTag.ORIGIN, revision)
        } else {
            logger.info { "Scan '${existingScan.code}' found for $mappedUrlWithoutCredentials and revision $revision." }
            logger.info {
                "Existing scan has for reference(s): ${existingScan.comment.orEmpty()}. Creating delta scan..."
            }
            namingProvider.createScanCode(projectName, DeltaTag.DELTA, revision)
        }

        val scanId = createScan(projectCode, scanCode, mappedUrl, revision, projectRevision.orEmpty())

        logger.info { "Initiating the download..." }
        service.downloadFromGit(config.user, config.apiKey, scanCode)
            .checkResponse("download data from Git", false)

        val issues = mutableListOf<Issue>()

        if (existingScan == null) {
            issues += createIgnoreRules(scanCode, context.excludes)

            if (config.waitForResult) checkScan(scanCode)
        } else {
            val existingScanCode = requireNotNull(existingScan.code) {
                "The code for an existing scan must not be null."
            }

            logger.info { "Loading ignore rules from '$existingScanCode'." }

            // TODO: This is the old way of carrying the rules to the new delta scan, by querying the previous scan.
            //       With the introduction of support for the ORT excludes, this old behavior can be dropped.
            val ignoreRules = service.listIgnoreRules(config.user, config.apiKey, existingScanCode)
                .checkResponse("list ignore rules")
            ignoreRules.data?.let { rules ->
                logger.info { "${rules.size} ignore rule(s) have been found." }

                // When a scan is created with the optional property 'git_repo_url', the server automatically creates
                // an 'ignore rule' to exclude the '.git' directory.
                // Therefore, this rule will be created automatically and does not need to be carried from the old scan.
                val exclusions = setOf(".git", "^\\.git")
                val filteredRules = rules.filterNot { it.type == RuleType.DIRECTORY && it.value in exclusions }

                issues += createIgnoreRules(scanCode, context.excludes, filteredRules)
            }

            logger.info { "Reusing identifications from scan '$existingScanCode'." }

            // TODO: Change the logic of 'waitForResult' to wait for download results but not for scan results.
            //  Hence we could trigger 'runScan' even when 'waitForResult' is set to false.
            if (!config.waitForResult) {
                logger.info { "Ignoring unset 'waitForResult' because delta scans are requested." }
            }

            checkScan(scanCode, *deltaScanRunParameters(existingScanCode))

            enforceDeltaScanLimit(recentScans)
        }

        return FossIdResult(scanCode, scanId, issues)
    }

    /**
     * Make sure that only the configured number of delta scans exists for the current package. Based on the list of
     * [existingScans], delete older scans until the maximum number of delta scans is reached.
     * Please note that in the case of delta scans, the [existingScans] are filtered by Git references or, in a case of
     * a fallback, filtered to be only default branch scans. Therefore, the delta scan limit is enforced per branch.
     */
    private suspend fun enforceDeltaScanLimit(existingScans: List<Scan>) {
        logger.info { "Will retain up to ${config.deltaScanLimit} delta scans." }

        // The current scan needs to be counted as well, in addition to the already existing scans.
        if (existingScans.size + 1 > config.deltaScanLimit) {
            logger.info { "Deleting ${existingScans.size + 1 - config.deltaScanLimit} older scans." }
        }

        // Drop the most recent scans to keep in order to iterate over the remaining ones to delete them.
        existingScans.drop(config.deltaScanLimit - 1)
            .forEach { scan ->
                scan.code?.let { code ->
                    logger.info { "Deleting scan '$code' to enforce the maximum number of delta scans." }
                    deleteScan(code)
                }
            }
    }

    private suspend fun createIgnoreRules(
        scanCode: String,
        excludes: Excludes?,
        existingRules: List<IgnoreRule> = emptyList()
    ): List<Issue> {
        val (excludesRules, excludeRuleIssues) = (excludes ?: Excludes.EMPTY).let {
            convertRules(it).also { (rules, _) ->
                logger.info { "${rules.size} rules from ORT excludes have been found." }
            }
        }

        // Create an issue for each legacy rule.
        val (legacyRules, legacyRuleIssues) = existingRules.filterLegacyRules(excludesRules)
        if (legacyRules.isNotEmpty()) {
            logger.warn { "${legacyRules.size} legacy rules have been found." }
        }

        val allRules = excludesRules + legacyRules
        allRules.forEach {
            service.createIgnoreRule(config.user, config.apiKey, scanCode, it.type, it.value, RuleScope.SCAN)
                .checkResponse("create ignore rules", false)
            logger.info {
                "Ignore rule of type '${it.type}' and value '${it.value}' has been created for the new scan."
            }
        }

        return excludeRuleIssues + legacyRuleIssues
    }

    /**
     * Create a new scan in the FossID server and return the scan id.
     */
    private suspend fun createScan(
        projectCode: String,
        scanCode: String,
        url: String,
        revision: String,
        reference: String = ""
    ): String {
        logger.info { "Creating scan '$scanCode'..." }

        val response = service.createScan(
            config.user,
            config.apiKey,
            projectCode,
            scanCode,
            url,
            revision,
            reference
        ).checkResponse("create scan")

        val scanId = response.data?.get("scan_id")

        requireNotNull(scanId) { "Scan could not be created. The response was: ${response.message}." }

        logger.info { "Scan has been created with ID $scanId." }
        createdScans.add(scanCode)

        return scanId
    }

    /**
     * Check the repository has been downloaded and the scan has completed. The latter will be triggered if needed.
     */
    private suspend fun checkScan(scanCode: String, vararg runOptions: Pair<String, String>) {
        waitDownloadComplete(scanCode)

        val response = service.checkScanStatus(config.user, config.apiKey, scanCode)
            .checkResponse("check scan status", false)

        check(response.data?.status != ScanStatus.FAILED) { "Triggered scan has failed." }

        if (response.data?.status in SCAN_STATE_FOR_TRIGGER) {
            logger.info { "Triggering scan as it has not yet been started." }

            val optionsFromConfig = arrayOf(
                "auto_identification_detect_declaration" to "${config.detectLicenseDeclarations.compareTo(false)}",
                "auto_identification_detect_copyright" to "${config.detectCopyrightStatements.compareTo(false)}"
            )

            val scanResult = service.runScan(
                config.user, config.apiKey, scanCode, mapOf(*runOptions, *optionsFromConfig)
            )

            // Scans that were added to the queue are interpreted as an error by FossID before version 2021.2.
            // For older versions, `waitScanComplete()` is able to deal with queued scans. Therefore, not checking the
            // response of queued scans.
            val currentVersion = checkNotNull(Semver.coerce(version))
            val minVersion = checkNotNull(Semver.coerce("2021.2"))
            if (currentVersion >= minVersion || scanResult.error != "Scan was added to queue.") {
                scanResult.checkResponse("trigger scan", false)
            }

            waitScanComplete(scanCode)
        }
    }

    /**
     * Loop for the lambda [condition] to return true, with the given [delay] between loop iterations. If the [timeout]
     * has been reached, return in any case.
     */
    private suspend fun wait(timeout: Duration, delay: Duration, condition: suspend () -> Boolean) =
        withTimeoutOrNull(timeout) {
            while (!condition()) {
                delay(delay)
            }
        }

    /**
     * Wait until the repository of a scan with [scanCode] has been downloaded.
     */
    private suspend fun waitDownloadComplete(scanCode: String) {
        val result = wait(config.timeout.minutes, WAIT_DELAY) {
            logger.info { "Checking download status for scan '$scanCode'." }

            val response = service.checkDownloadStatus(config.user, config.apiKey, scanCode)
                .checkResponse("check download status")

            when (response.data) {
                DownloadStatus.FINISHED -> return@wait true

                DownloadStatus.FAILED -> error("Could not download scan: ${response.message}.")

                else -> {
                    // There is a bug with the FossID server version < 20.2: Sometimes the download is complete, but it
                    // stays in state "NOT FINISHED". Therefore, we check the output of the Git fetch to find out
                    // whether the download is actually done.
                    val message = response.message
                    val currentVersion = checkNotNull(Semver.coerce(version))
                    val minVersion = checkNotNull(Semver.coerce("20.2"))
                    if (currentVersion >= minVersion || message == null
                        || !GIT_FETCH_DONE_REGEX.containsMatchIn(message)
                    ) {
                        return@wait false
                    }

                    logger.warn { "The download is not finished but Git Fetch has completed. Carrying on..." }

                    return@wait true
                }
            }
        }

        requireNotNull(result) { "Timeout while waiting for the download to complete" }

        logger.info { "Data download has been completed." }
    }

    /**
     * Wait until a scan with [scanCode] has completed.
     */
    private suspend fun waitScanComplete(scanCode: String) {
        val result = wait(config.timeout.minutes, WAIT_DELAY) {
            logger.info { "Waiting for scan '$scanCode' to complete." }

            val response = service.checkScanStatus(config.user, config.apiKey, scanCode)
                .checkResponse("check scan status", false)

            when (response.data?.status) {
                ScanStatus.FINISHED -> true
                ScanStatus.FAILED -> error("Scan waited for has failed.")
                null -> false
                else -> {
                    logger.info {
                        "Scan status for scan '$scanCode' is '${response.data?.status}'. Waiting..."
                    }

                    false
                }
            }
        }

        requireNotNull(result) { "Timeout while waiting for the scan to complete" }

        logger.info { "Scan has been completed." }
    }

    /**
     * Delete a scan with [scanCode].
     */
    private suspend fun deleteScan(scanCode: String) {
        val response = service.deleteScan(config.user, config.apiKey, scanCode)
        response.error?.let {
            logger.error { "Cannot delete scan '$scanCode': $it." }
        }
    }

    /**
     * Get the different kind of results from the scan with [scanCode]
     */
    @Suppress("UnsafeCallOnNullableType")
    private suspend fun getRawResults(scanCode: String, snippetChoices: List<SnippetChoice>): RawResults {
        val identifiedFiles = service.listIdentifiedFiles(config.user, config.apiKey, scanCode)
            .checkResponse("list identified files")
            .data!!
        logger.info { "${identifiedFiles.size} identified files have been returned for scan '$scanCode'." }

        val markedAsIdentifiedFiles = service.listMarkedAsIdentifiedFiles(config.user, config.apiKey, scanCode)
            .checkResponse("list marked as identified files")
            .data!!
        logger.info {
            "${markedAsIdentifiedFiles.size} marked as identified files have been returned for scan '$scanCode'."
        }

        // The "match_type=ignore" info is already in the ScanResult, but here we also get the ignore reason.
        val listIgnoredFiles = service.listIgnoredFiles(config.user, config.apiKey, scanCode)
            .checkResponse("list ignored files")
            .data!!

        val pendingFiles = service.listPendingFiles(config.user, config.apiKey, scanCode)
            .checkResponse("list pending files")
            .data!!.toMutableList()
        logger.info {
            "${pendingFiles.size} pending files have been returned for scan '$scanCode'."
        }

        pendingFiles += listUnmatchedSnippetChoices(markedAsIdentifiedFiles, snippetChoices).also { newPendingFiles ->
            newPendingFiles.map {
                logger.info {
                    "Marked as identified file '$it' is not in .ort.yml anymore or its configuration has been " +
                        "altered: putting it again as 'pending'."
                }
                service.unmarkAsIdentified(config.user, config.apiKey, scanCode, it, false)
            }
        }

        val matchedLines = mutableMapOf<Int, MatchedLines>()
        val pendingFilesIterator = pendingFiles.iterator()
        val snippets = flow {
            while (pendingFilesIterator.hasNext()) {
                val file = pendingFilesIterator.next()
                logger.info { "Listing snippet for $file..." }

                val snippetResponse = service.listSnippets(config.user, config.apiKey, scanCode, file)
                    .checkResponse("list snippets")
                val snippets = checkNotNull(snippetResponse.data) {
                    "Snippet could not be listed. Response was ${snippetResponse.message}."
                }
                logger.info { "${snippets.size} snippets." }

                val filteredSnippets = snippets.filterTo(mutableSetOf()) { it.matchType.isValidType() }

                if (config.fetchSnippetMatchedLines) {
                    logger.info { "Listing snippet matched lines for $file..." }

                    coroutineScope {
                        filteredSnippets.filter { it.matchType == MatchType.PARTIAL }.map { snippet ->
                            async {
                                val matchedLinesResponse =
                                    service.listMatchedLines(config.user, config.apiKey, scanCode, file, snippet.id)
                                        .checkResponse("list snippets matched lines")
                                val lines = checkNotNull(matchedLinesResponse.data) {
                                    "Matched lines could not be listed. Response was " +
                                        "${matchedLinesResponse.message}."
                                }
                                matchedLines[snippet.id] = lines
                            }
                        }.awaitAll()
                    }
                }

                emit(file to filteredSnippets.toSet())
            }
        }

        return RawResults(
            identifiedFiles,
            markedAsIdentifiedFiles,
            listIgnoredFiles,
            pendingFiles,
            snippets,
            matchedLines
        )
    }

    /**
     * Construct the [ScanSummary] for this FossID scan.
     */
    @Suppress("LongParameterList")
    private suspend fun createResultSummary(
        startTime: Instant,
        provenance: Provenance,
        rawResults: RawResults,
        result: FossIdResult,
        detectedLicenseMapping: Map<String, String>,
        snippetChoices: List<SnippetChoice>
    ): ScanResult {
        // TODO: Maybe get issues from FossID (see has_failed_scan_files, get_failed_files and maybe get_scan_log).

        val issues = mutableListOf<Issue>()

        val snippetLicenseFindings = mutableSetOf<LicenseFinding>()
        val snippetFindings = mapSnippetFindings(
            rawResults,
            config.snippetsLimit,
            issues,
            detectedLicenseMapping,
            snippetChoices,
            snippetLicenseFindings
        )
        val newlyMarkedFiles = markFilesWithChosenSnippetsAsIdentified(
            result.scanCode,
            snippetChoices,
            snippetFindings,
            rawResults.listPendingFiles,
            snippetLicenseFindings
        )

        val pendingFilesCount = (rawResults.listPendingFiles - newlyMarkedFiles.toSet()).size

        issues.add(
            0,
            Issue(
                source = name,
                message = "This scan has $pendingFilesCount file(s) pending identification in FossID.",
                severity = Severity.HINT
            )
        )

        val ignoredFiles = rawResults.listIgnoredFiles.associateBy { it.path }

        val (licenseFindings, copyrightFindings) = rawResults.markedAsIdentifiedFiles.ifEmpty {
            rawResults.identifiedFiles
        }.mapSummary(ignoredFiles, issues, detectedLicenseMapping)

        val summary = ScanSummary(
            startTime = startTime,
            endTime = Instant.now(),
            licenseFindings = licenseFindings + snippetLicenseFindings,
            copyrightFindings = copyrightFindings,
            snippetFindings = snippetFindings,
            issues = issues + result.issues
        )

        return ScanResult(
            provenance,
            details,
            summary,
            mapOf(SCAN_CODE_KEY to result.scanCode, SCAN_ID_KEY to result.scanId, SERVER_URL_KEY to config.serverUrl)
        )
    }

    /**
     * Mark all the files in [snippetChoices] as identified, only after searching in [snippetFindings] that they have no
     * non-chosen source location remaining. Only files in [listPendingFiles] are marked.
     * Files marked as identified have a license identification and a source location (stored in a comment), using
     * [licenseFindings] as reference.
     * Returns the list of files that have been marked as identified.
     */
    private fun markFilesWithChosenSnippetsAsIdentified(
        scanCode: String,
        snippetChoices: List<SnippetChoice> = emptyList(),
        snippetFindings: Set<SnippetFinding>,
        pendingFiles: List<String>,
        licenseFindings: Set<LicenseFinding>
    ): List<String> {
        val licenseFindingsByPath = licenseFindings.groupBy { it.location.path }
        val result = mutableListOf<String>()

        runBlocking(Dispatchers.IO) {
            val candidatePathsToMark = snippetChoices.groupBy({ it.given.sourceLocation.path }) {
                it.choice.reason
            }

            val requests = mutableListOf<Deferred<Any>>()

            candidatePathsToMark.forEach { (path, reasons) ->
                val allLocationsChosen = snippetFindings.none { path == it.sourceLocation.path }

                if (allLocationsChosen) {
                    if (pendingFiles.none { path == it }) {
                        logger.info { "Not marking $path as identified as it is not pending." }
                    } else {
                        logger.info {
                            "Marking $path as identified as a choice has been made for all locations with snippet" +
                                "findings. The used reasons are: ${reasons.joinToString()}"
                        }

                        requests += async {
                            service.markAsIdentified(config.user, config.apiKey, scanCode, path, false)
                            result += path
                        }

                        val filteredSnippetChoicesByPath = snippetChoices.filter {
                            it.given.sourceLocation.path == path
                        }

                        val relevantSnippetChoices = filteredSnippetChoicesByPath.filter {
                            it.choice.reason == SnippetChoiceReason.ORIGINAL_FINDING
                        }

                        relevantSnippetChoices.forEach { filteredSnippetChoice ->
                            val match = SNIPPET_PURL_REGEX.matchEntire(filteredSnippetChoice.choice.purl.orEmpty())
                            match?.also {
                                val artifact = match.groups["artifact"]?.value.orEmpty()
                                val version = match.groups["version"]?.value.orEmpty()
                                val location = filteredSnippetChoice.given.sourceLocation

                                requests += async {
                                    logger.info {
                                        "Adding component identification '$artifact/$version' to '$path' " +
                                            "at ${location.startLine}-${location.endLine}."
                                    }

                                    service.addComponentIdentification(
                                        config.user,
                                        config.apiKey,
                                        scanCode,
                                        path,
                                        artifact,
                                        version,
                                        false
                                    )
                                }
                            }
                        }

                        // The chosen snippet source location lines can neither be stored in the scan nor the file, so
                        // it is stored in a comment attached to the identified file instead.
                        val licenseFindingsByLicense = licenseFindingsByPath[path]?.groupBy({ it.license.toString() }) {
                            it.location
                        }.orEmpty()

                        val relevantChoicesCount = relevantSnippetChoices.size
                        val notRelevantChoicesCount = filteredSnippetChoicesByPath.count {
                            it.choice.reason == SnippetChoiceReason.NO_RELEVANT_FINDING
                        }
                        val payload = OrtCommentPayload(
                            licenseFindingsByLicense,
                            relevantChoicesCount,
                            notRelevantChoicesCount
                        )
                        val comment = OrtComment(payload)
                        val jsonComment = jsonMapper.writeValueAsString(comment)
                        requests += async {
                            logger.info {
                                "Adding file comment to '$path' with relevant count $relevantChoicesCount and not " +
                                    "relevant count $notRelevantChoicesCount."
                            }
                            service.addFileComment(config.user, config.apiKey, scanCode, path, jsonComment)
                        }
                    }
                }
            }

            requests.awaitAll()
        }
        return result
    }
}

private data class FossIdResult(
    val scanCode: String,
    val scanId: String,
    val issues: List<Issue> = emptyList()
)
