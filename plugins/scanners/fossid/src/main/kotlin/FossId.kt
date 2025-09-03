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
import kotlinx.coroutines.withTimeoutOrNull

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.clients.fossid.FossIdRestService
import org.ossreviewtoolkit.clients.fossid.addComponentIdentification
import org.ossreviewtoolkit.clients.fossid.addFileComment
import org.ossreviewtoolkit.clients.fossid.checkResponse
import org.ossreviewtoolkit.clients.fossid.createProject
import org.ossreviewtoolkit.clients.fossid.deleteScan
import org.ossreviewtoolkit.clients.fossid.getProject
import org.ossreviewtoolkit.clients.fossid.listIdentifiedFiles
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
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.config.snippet.SnippetChoice
import org.ossreviewtoolkit.model.config.snippet.SnippetChoiceReason
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.scanners.fossid.events.EventHandler
import org.ossreviewtoolkit.scanner.PackageScannerWrapper
import org.ossreviewtoolkit.scanner.ProvenanceScannerWrapper
import org.ossreviewtoolkit.scanner.ScanContext
import org.ossreviewtoolkit.scanner.ScannerMatcher
import org.ossreviewtoolkit.scanner.ScannerWrapperFactory
import org.ossreviewtoolkit.scanner.provenance.NestedProvenance
import org.ossreviewtoolkit.utils.common.enumSetOf
import org.ossreviewtoolkit.utils.common.replaceCredentialsInUri
import org.ossreviewtoolkit.utils.ort.runBlocking
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
@OrtPlugin(
    displayName = "FossID",
    description = "The FossID scanner plugin.",
    factory = ScannerWrapperFactory::class
)
@Suppress("LargeClass", "TooManyFunctions")
class FossId internal constructor(
    override val descriptor: PluginDescriptor = FossIdFactory.descriptor,
    internal val config: FossIdConfig
) : PackageScannerWrapper {
    companion object {
        @JvmStatic
        private val REPOSITORY_NAME_REGEX = Regex("""^.*/([\w.\-]+?)(?:\.git)?$""")

        @JvmStatic
        internal val GIT_FETCH_DONE_REGEX = Regex("-> FETCH_HEAD(?: Already up to date.)*$")

        /**
         * A regular expression to extract the artifact and version from a purl returned by FossID.
         */
        @JvmStatic
        private val SNIPPET_PURL_REGEX = Regex("^.*/(?<artifact>[^@]+)@(?<version>.+)")

        @JvmStatic
        internal val WAIT_DELAY = 10.seconds

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
         * Try to extract the repository name from a Git repository URL, for example:
         *
         * `https://github.com/jshttp/mime-types.git -> mime-types`
         *
         * @throws IllegalArgumentException if the repository name cannot be determined.
         */
        internal fun extractRepositoryName(gitRepoUrl: String): String {
            val repositoryNameMatcher = REPOSITORY_NAME_REGEX.matchEntire(gitRepoUrl)

            requireNotNull(repositoryNameMatcher) {
                "Cannot determine repository name from Git repository URL $gitRepoUrl."
            }

            return repositoryNameMatcher.groupValues[1].also {
                logger.info { "Found repository name '$it' in URL $gitRepoUrl." }
            }
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

    // A list of all scans created in an ORT run, to be able to delete them in case of error.
    // The reasoning is that either all these scans are successful, either none is created at all (clean slate).
    // A use case is that an ORT run is created regularly, e.g. nightly, and exactly the same amount of scans for each
    // package is wanted.
    private val createdScans = mutableSetOf<String>()

    private val service by lazy {
        runBlocking { FossIdRestService.create(config.serverUrl, logRequests = config.logRequests) }
    }

    override val version by lazy { service.version }
    override val configuration = ""

    override val matcher: ScannerMatcher? = null

    override val readFromStorage = false

    override val writeToStorage = config.writeToStorage

    private suspend fun getProject(projectCode: String): Project? =
        service.getProject(config.user.value, config.apiKey.value, projectCode).run {
            when {
                error == null && data?.value != null -> {
                    logger.info { "Project '$projectCode' exists." }
                    data?.value
                }

                error == "Project does not exist" && status == 0 -> {
                    logger.info { "Project '$projectCode' does not exist." }
                    null
                }

                else -> {
                    val errorMessage = "Could not get project '$projectCode' for user '${config.user.value}: $error'"
                    logger.error { errorMessage }
                    throw IOException(errorMessage)
                }
            }
        }

    /**
     * Create a [ScanSummary] containing a single [issue] started at [startTime] and ended now.
     */
    private fun createSingleIssueSummary(startTime: Instant, issue: Issue) =
        ScanSummary.EMPTY.copy(startTime = startTime, endTime = Instant.now(), issues = listOf(issue))

    override fun scanPackage(nestedProvenance: NestedProvenance?, context: ScanContext): ScanResult {
        val startTime = Instant.now()

        // FossID actually never uses the provenance determined by the scanner, but determines the source code to
        // download itself based on the passed VCS URL and revision, disregarding any VCS path.
        val pkg = context.coveredPackages.first()
        val provenance = pkg.vcsProcessed.revision.takeUnless { it.isBlank() }
            ?.let { RepositoryProvenance(pkg.vcsProcessed, it) } ?: UnknownProvenance

        val handler = EventHandler.getHandler(config, nestedProvenance, service)

        val issueMessage = when {
            !handler.isPackageValid(pkg) -> handler.getPackageInvalidErrorMessage(pkg)

            pkg.vcsProcessed.revision.isEmpty() ->
                "Package '${pkg.id.toCoordinates()}' has an empty VCS revision and cannot be scanned."

            else -> null
        }

        if (issueMessage != null) {
            val issue = createAndLogIssue(issueMessage, Severity.WARNING)
            val summary = createSingleIssueSummary(startTime, issue = issue)
            return ScanResult(provenance, details, summary)
        }

        val url = pkg.vcsProcessed.url
        val revision = pkg.vcsProcessed.revision
        val repositoryName = extractRepositoryName(url)

        val result = runBlocking {
            try {
                val projectCode = config.projectName ?: repositoryName

                if (getProject(projectCode) == null) {
                    logger.info { "Creating project '$projectCode'..." }

                    service.createProject(config.user.value, config.apiKey.value, projectCode, projectCode)
                        .checkResponse("create project")
                }

                val scans = service.listScansForProject(config.user.value, config.apiKey.value, projectCode)
                    .checkResponse("list scans for project").data
                checkNotNull(scans)

                val result = if (config.deltaScans) {
                    checkAndCreateDeltaScan(handler, scans, url, revision, projectCode, repositoryName, context)
                } else {
                    checkAndCreateScan(
                        handler,
                        scans,
                        url,
                        revision,
                        projectCode,
                        repositoryName,
                        nestedProvenance,
                        context
                    )
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
                        "Package '${pkg.id.toCoordinates()}' has been scanned in asynchronous mode. " +
                            "Scan results need to be inspected on the server instance.",
                        Severity.HINT
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

                val issue = createAndLogIssue("Failed to scan package '${pkg.id.toCoordinates()}' from $url.")
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

            val response = service.checkScanStatus(config.user.value, config.apiKey.value, scanCode)
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
        val scanIdToComments = associate { it.id to extractDeltaScanInformationFromScan(it) }

        val scans = filter {
            val scanComment = scanIdToComments[it.id]
                ?: error("Scan with ID ${it.id} does not have a valid comment to extract scan information from.")
            val isArchived = it.isArchived == true
            !isArchived && scanComment.ort.repositoryURL == url
        }.sortedByDescending { it.id }

        return scans.filter { scan ->
            val scanComment = scanIdToComments[scan.id]
                ?: error("Scan with ID ${scan.id} does not have a valid comment to extract scan information from.")

            projectRevision == scanComment.ort.projectRevision
        }.ifEmpty {
            logger.warn {
                "No recent scan found for project revision $projectRevision. Falling back to default branch scans."
            }

            scans.filter { scan ->
                val scanComment = scanIdToComments[scan.id]
                    ?: error("Scan with ID ${scan.id} does not have a valid comment to extract scan information from.")

                scanComment.ort.projectRevision == defaultBranch
            }.ifEmpty {
                logger.warn { "No recent default branch scan found. Falling back to old behavior." }

                scans.filter {
                    val scanComment = scanIdToComments[it.id] ?: error(
                        "Scan with ID ${it.id} does not have a valid comment to extract scan information from."
                    )

                    revision == null || scanComment.ort.revision == revision
                }
            }
        }
    }

    /**
     * Extract the information needed for creating a delta scan from the given [scan], which could be a legacy scan.
     * Scans S1 created by FossID cloning the repository have always the properties "gitRepoUrl" and "gitBranch" set.
     * Scans S2 created by uploading an archive have those two properties set to null.
     * Old legacy scans, which are all in S1, have the projectRevision in the "comment" property.
     * New scans for S1 and S2 have a JSON structure in the "comment" property, which contains the Git repository URL,
     * the Git revision and the project revision.
     */
    private fun extractDeltaScanInformationFromScan(scan: Scan): OrtScanComment =
        if ('{' in scan.comment.orEmpty()) {
            val comment = jsonMapper.readValue(scan.comment, OrtScanComment::class.java)
            // Even if the scan is not a legacy scan, it can wrongly contain credentials in the URL property if it was
            // created after https://github.com/oss-review-toolkit/ort/pull/10656 was merged but before this fix.
            comment.copy(ort = comment.ort.copy(repositoryURL = comment.ort.repositoryURL.replaceCredentialsInUri()))
        } else {
            // This is a legacy scan.
            // The scans in the server contain the URL with credentials, so these have to be removed for the comparison.
            // Otherwise, scans would not be matched if the password changed.
            val urlWithoutCredentials = scan.gitRepoUrl.orEmpty().replaceCredentialsInUri()
            createOrtScanComment(urlWithoutCredentials, scan.gitBranch.orEmpty(), scan.comment.orEmpty())
        }

    /**
     * Call FossID service, initiate a scan and return scan data: Scan Code and Scan Id
     */
    @Suppress("LongParameterList")
    private suspend fun checkAndCreateScan(
        handler: EventHandler,
        scans: List<Scan>,
        url: String,
        revision: String,
        projectCode: String,
        projectName: String,
        nestedProvenance: NestedProvenance?,
        context: ScanContext
    ): FossIdResult {
        val existingScan = scans.recentScansForRepository(url, revision = revision).findLatestPendingOrFinishedScan()

        val result = if (existingScan == null) {
            logger.info { "No scan found for $url and revision $revision. Creating scan..." }

            val scanCode = namingProvider.createScanCode(repositoryName = projectName, branch = revision)
            val newUrl = handler.transformURL(url)
            val scanId = createScan(handler, projectCode, scanCode, newUrl, revision)

            val issues = mutableListOf<Issue>()
            handler.afterScanCreation(scanCode, null, issues, context)

            if (config.waitForResult) checkScan(handler, scanCode)

            FossIdResult(scanCode, scanId, issues)
        } else {
            logger.info { "Scan '${existingScan.code}' found for $url and revision $revision." }

            val existingScanCode = requireNotNull(existingScan.code) {
                "The code for an existing scan must not be null."
            }

            // Create a specific handler for the existing scan.
            val handlerForExistingScan = EventHandler.getHandler(existingScan, config, nestedProvenance, service)

            if (config.waitForResult) checkScan(handlerForExistingScan, existingScan.code.orEmpty())

            FossIdResult(existingScanCode, existingScan.id.toString())
        }

        return result
    }

    /**
     * Call FossID service, initiate a delta scan and return scan data: Scan Code and Scan Id
     */
    private suspend fun checkAndCreateDeltaScan(
        handler: EventHandler,
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

        val mappedUrl = handler.transformURL(urlWithoutCredentials)
        val mappedUrlWithoutCredentials = mappedUrl.replaceCredentialsInUri()

        // Ignore the revision for delta scans.
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
            val comment = extractDeltaScanInformationFromScan(existingScan)
            logger.info {
                "Existing scan has for reference(s): $comment. Creating delta scan..."
            }

            namingProvider.createScanCode(projectName, DeltaTag.DELTA, revision)
        }

        val scanId = createScan(handler, projectCode, scanCode, mappedUrl, revision, projectRevision.orEmpty())

        val issues = mutableListOf<Issue>()

        handler.afterScanCreation(scanCode, existingScan, issues, context)

        if (existingScan == null) {
            if (config.waitForResult) checkScan(handler, scanCode)
        } else {
            val existingScanCode = requireNotNull(existingScan.code) {
                "The code for an existing scan must not be null."
            }

            logger.info { "Reusing identifications from scan '$existingScanCode'." }

            // TODO: Change the logic of 'waitForResult' to wait for download results but not for scan results.
            //  Hence we could trigger 'runScan' even when 'waitForResult' is set to false.
            if (!config.waitForResult) {
                logger.info { "Ignoring unset 'waitForResult' because delta scans are requested." }
            }

            checkScan(handler, scanCode, *deltaScanRunParameters(existingScanCode))

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

    /**
     * Create a new scan in the FossID server and return the scan id.
     */
    private suspend fun createScan(
        handler: EventHandler,
        projectCode: String,
        scanCode: String,
        url: String,
        revision: String,
        reference: String = ""
    ): String {
        logger.info { "Creating scan '$scanCode'..." }

        val urlWithoutCredentials = url.replaceCredentialsInUri()
        val comment = createOrtScanComment(urlWithoutCredentials, revision, reference)
        val response = handler.createScan(url, projectCode, scanCode, comment)

        val data = response.data?.value

        if (data?.message != null) {
            logger.warn {
                "Create scan returned an error content as payload (see issue #8462)." +
                    " Additional information: ${data.message}"
            }
        }

        val scanId = data?.scanId

        requireNotNull(scanId) { "Scan could not be created. The response was: ${response.message}." }

        logger.info { "Scan has been created with ID $scanId." }
        createdScans.add(scanCode)

        return scanId
    }

    /**
     * Check the repository has been downloaded and the scan has completed. The latter will be triggered if needed.
     */
    private suspend fun checkScan(handler: EventHandler, scanCode: String, vararg runOptions: Pair<String, String>) {
        handler.beforeCheckScan(scanCode)

        val response = service.checkScanStatus(config.user.value, config.apiKey.value, scanCode)
            .checkResponse("check scan status", false)

        check(response.data?.status != ScanStatus.FAILED) { "Triggered scan has failed." }

        if (response.data?.status in SCAN_STATE_FOR_TRIGGER) {
            logger.info { "Triggering scan as it has not yet been started." }

            val optionsFromConfig = arrayOf(
                "auto_identification_detect_declaration" to "${config.detectLicenseDeclarations.compareTo(false)}",
                "auto_identification_detect_copyright" to "${config.detectCopyrightStatements.compareTo(false)}",
                "sensitivity" to "${config.sensitivity}"
            )

            val scanResult = service.runScan(
                config.user.value, config.apiKey.value, scanCode, mapOf(*runOptions, *optionsFromConfig)
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

        handler.afterCheckScan(scanCode)
    }

    /**
     * Wait until a scan with [scanCode] has completed.
     */
    private suspend fun waitScanComplete(scanCode: String) {
        val result = wait(config.timeout.minutes, WAIT_DELAY) {
            logger.info { "Waiting for scan '$scanCode' to complete." }

            val response = service.checkScanStatus(config.user.value, config.apiKey.value, scanCode)
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
        val response = service.deleteScan(config.user.value, config.apiKey.value, scanCode)
        response.error?.let {
            logger.error { "Cannot delete scan '$scanCode': $it." }
        }
    }

    /**
     * Get the different kind of results from the scan with [scanCode]
     */
    @Suppress("UnsafeCallOnNullableType")
    private suspend fun getRawResults(scanCode: String, snippetChoices: List<SnippetChoice>): RawResults {
        val identifiedFiles = service.listIdentifiedFiles(config.user.value, config.apiKey.value, scanCode)
            .checkResponse("list identified files")
            .data!!
        logger.info { "${identifiedFiles.size} identified files have been returned for scan '$scanCode'." }

        val markedAsIdentifiedFiles = service
            .listMarkedAsIdentifiedFiles(config.user.value, config.apiKey.value, scanCode)
            .checkResponse("list marked as identified files")
            .data!!
        logger.info {
            "${markedAsIdentifiedFiles.size} marked as identified files have been returned for scan '$scanCode'."
        }

        // The "match_type=ignore" info is already in the ScanResult, but here we also get the ignore reason.
        val listIgnoredFiles = service.listIgnoredFiles(config.user.value, config.apiKey.value, scanCode)
            .checkResponse("list ignored files")
            .data!!

        val pendingFiles = service.listPendingFiles(config.user.value, config.apiKey.value, scanCode)
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

                service.unmarkAsIdentified(config.user.value, config.apiKey.value, scanCode, it, false)
            }
        }

        val matchedLines = mutableMapOf<Int, MatchedLines>()
        val pendingFilesIterator = pendingFiles.iterator()
        val snippets = flow {
            while (pendingFilesIterator.hasNext()) {
                val file = pendingFilesIterator.next()
                logger.info { "Listing snippet for $file..." }

                val snippetResponse = service.listSnippets(config.user.value, config.apiKey.value, scanCode, file)
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
                                val matchedLinesResponse = service.listMatchedLines(
                                    config.user.value,
                                    config.apiKey.value,
                                    scanCode,
                                    file,
                                    snippet.id
                                ).checkResponse("list snippets matched lines")

                                val lines = checkNotNull(matchedLinesResponse.data?.value) {
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

        val fossIdScanUrl = buildFossIdScanUrl(config.serverUrl, result.scanId)

        issues.add(
            0,
            Issue(
                source = descriptor.id,
                message = "This scan has $pendingFilesCount file(s) pending identification in FossID. " +
                    "Please review and resolve them at: $fossIdScanUrl",
                severity = if (config.treatPendingIdentificationsAsError) Severity.ERROR else Severity.HINT
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

        runBlocking(Dispatchers.IO.limitedParallelism(20)) {
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
                            service.markAsIdentified(config.user.value, config.apiKey.value, scanCode, path, false)
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
                                        config.user.value,
                                        config.apiKey.value,
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

                            service.addFileComment(config.user.value, config.apiKey.value, scanCode, path, jsonComment)
                        }
                    }
                }
            }

            requests.awaitAll()
        }

        return result
    }

    private fun buildFossIdScanUrl(serverUrl: String, scanId: String) =
        "${if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"}index.html?action=scanview&sid=$scanId"
}

private data class FossIdResult(
    val scanCode: String,
    val scanId: String,
    val issues: List<Issue> = emptyList()
)

/**
 * Loop for the lambda [condition] to return true, with the given [delay] between loop iterations. If the [timeout]
 * has been reached, return in any case.
 */
internal suspend fun wait(timeout: Duration, delay: Duration, condition: suspend () -> Boolean) =
    withTimeoutOrNull(timeout) {
        while (!condition()) {
            delay(delay)
        }
    }
