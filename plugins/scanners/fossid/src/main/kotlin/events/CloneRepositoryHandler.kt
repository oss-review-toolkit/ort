/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.fossid.events

import kotlin.time.Duration.Companion.minutes

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.clients.fossid.FossIdServiceWithVersion
import org.ossreviewtoolkit.clients.fossid.PolymorphicDataResponseBody
import org.ossreviewtoolkit.clients.fossid.checkDownloadStatus
import org.ossreviewtoolkit.clients.fossid.checkResponse
import org.ossreviewtoolkit.clients.fossid.createIgnoreRule
import org.ossreviewtoolkit.clients.fossid.createScan
import org.ossreviewtoolkit.clients.fossid.downloadFromGit
import org.ossreviewtoolkit.clients.fossid.listIgnoreRules
import org.ossreviewtoolkit.clients.fossid.model.CreateScanResponse
import org.ossreviewtoolkit.clients.fossid.model.Scan
import org.ossreviewtoolkit.clients.fossid.model.rules.IgnoreRule
import org.ossreviewtoolkit.clients.fossid.model.rules.RuleScope
import org.ossreviewtoolkit.clients.fossid.model.rules.RuleType
import org.ossreviewtoolkit.clients.fossid.model.status.DownloadStatus
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.plugins.scanners.fossid.FossId.Companion.GIT_FETCH_DONE_REGEX
import org.ossreviewtoolkit.plugins.scanners.fossid.FossId.Companion.WAIT_DELAY
import org.ossreviewtoolkit.plugins.scanners.fossid.FossIdConfig
import org.ossreviewtoolkit.plugins.scanners.fossid.OrtScanComment
import org.ossreviewtoolkit.plugins.scanners.fossid.convertRules
import org.ossreviewtoolkit.plugins.scanners.fossid.filterLegacyRules
import org.ossreviewtoolkit.plugins.scanners.fossid.wait
import org.ossreviewtoolkit.scanner.ScanContext

import org.semver4j.Semver

/**
 * An event handler when FossID clones a repository itself.
 */
class CloneRepositoryHandler(val config: FossIdConfig, val service: FossIdServiceWithVersion) : EventHandler {
    private val urlProvider = config.createUrlProvider()

    override fun getPackageInvalidErrorMessage(pkg: Package): String? {
        val message = "Package '${pkg.id.toCoordinates()}' uses VCS type '${pkg.vcsProcessed.type}', but only " +
            "${VcsType.GIT} is supported."
        return message.takeIf { pkg.vcsProcessed.type != VcsType.GIT }
    }

    override fun transformURL(url: String): String = urlProvider.getUrl(url)

    override suspend fun createScan(
        repositoryUrl: String,
        projectCode: String,
        scanCode: String,
        comment: OrtScanComment
    ): PolymorphicDataResponseBody<CreateScanResponse> =
        service
            .createScan(
                config.user.value,
                config.apiKey.value,
                projectCode,
                scanCode,
                repositoryUrl,
                comment.ort.revision,
                comment.asJsonString()
            )
            .checkResponse("create scan")

    override suspend fun afterScanCreation(
        scanCode: String,
        existingScan: Scan?,
        issues: MutableList<Issue>,
        context: ScanContext
    ) {
        logger.info { "Initiating the download..." }
        service.downloadFromGit(config.user.value, config.apiKey.value, scanCode)
            .checkResponse("download data from Git", false)

        if (existingScan == null) {
            issues += createIgnoreRules(scanCode, context.excludes)
        } else {
            val existingScanCode = requireNotNull(existingScan.code) {
                "The code for an existing scan must not be null."
            }

            logger.info { "Loading ignore rules from '$existingScanCode'." }

            // TODO: This is the old way of carrying the rules to the new delta scan, by querying the previous scan.
            //       With the introduction of support for the ORT excludes, this old behavior can be dropped.
            val ignoreRules = service.listIgnoreRules(config.user.value, config.apiKey.value, existingScanCode)
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
            //       Hence we could trigger 'runScan' even when 'waitForResult' is set to false.
            if (!config.waitForResult) {
                logger.info { "Ignoring unset 'waitForResult' because delta scans are requested." }
            }
        }
    }

    override suspend fun beforeCheckScan(scanCode: String) {
        waitDownloadComplete(scanCode)
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
            val response = service.createIgnoreRule(
                config.user.value,
                config.apiKey.value,
                scanCode,
                it.type,
                it.value,
                RuleScope.SCAN
            )
            // It could be that global rules are automatically added to a scan. Therefore, a failure in creation
            // because of duplication should be ignored.
            if (response.error != "Rule already exists.") {
                response.checkResponse("create ignore rules", false)

                logger.info {
                    "Ignore rule of type '${it.type}' and value '${it.value}' has been created for the new scan."
                }
            }
        }

        return excludeRuleIssues + legacyRuleIssues
    }

    /**
     * Wait until the repository of a scan with [scanCode] has been downloaded.
     */
    private suspend fun waitDownloadComplete(scanCode: String) {
        val result = wait(config.timeout.minutes, WAIT_DELAY) {
            logger.info { "Checking download status for scan '$scanCode'." }

            val response = service.checkDownloadStatus(config.user.value, config.apiKey.value, scanCode)
                .checkResponse("check download status")

            when (response.data?.value) {
                DownloadStatus.FINISHED -> return@wait true

                DownloadStatus.FAILED -> error("Could not download scan: ${response.message}.")

                else -> {
                    // There is a bug in FossID server version < 20.2: Sometimes the download is complete, but it stays
                    // in "NOT FINISHED" state. Therefore, check the output of the Git fetch command to find out whether
                    // the download has actually finished.
                    val message = response.message
                    val currentVersion = checkNotNull(Semver.coerce(service.version))
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
}
