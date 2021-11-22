/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.advisor.advisors

import java.net.URI
import java.time.Instant
import java.util.concurrent.Executors
import java.util.regex.Pattern

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

import org.ossreviewtoolkit.advisor.AbstractAdviceProviderFactory
import org.ossreviewtoolkit.advisor.AdviceProvider
import org.ossreviewtoolkit.clients.github.DateTime
import org.ossreviewtoolkit.clients.github.GitHubService
import org.ossreviewtoolkit.clients.github.Paging
import org.ossreviewtoolkit.clients.github.QueryResult
import org.ossreviewtoolkit.clients.github.issuesquery.Issue
import org.ossreviewtoolkit.clients.github.labels
import org.ossreviewtoolkit.clients.github.releasesquery.Release
import org.ossreviewtoolkit.model.AdvisorCapability
import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorSummary
import org.ossreviewtoolkit.model.Defect
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.model.config.GitHubDefectsConfiguration
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.utils.common.collectMessagesAsString
import org.ossreviewtoolkit.utils.common.enumSetOf
import org.ossreviewtoolkit.utils.core.filterVersionNames
import org.ossreviewtoolkit.utils.core.log
import org.ossreviewtoolkit.utils.core.showStackTrace

/**
 * An [AdviceProvider] implementation that obtains information about open defects from GitHub.
 *
 * This advice provider can handle packages whose source code is hosted on GitHub. It queries the repository of such a
 * package for the existing issues and tries to match them against the package's version.
 *
 * Unfortunately, mapping a GitHub issue to the release in which it got fixed is not trivial, since there is no such
 * property as a fix version. This implementation therefore compares the date an issue was closed with the release date
 * of a package. But even the release date of a package cannot be obtained easily; this involves matching of
 * revisions and tags against the package's metadata. If such a match fails, this implementation will only return
 * issues that are currently open.
 *
 * In general, the data model of GitHub issues is rather weak from a semantic point of view. There is no clear
 * distinction between defects, feature requests, or other types of issues. The information retrieved by this
 * implementation is therefore of limited value. To ameliorate the situation, it is possible to define filters that
 * match against labels assigned to issues in a rather generic way. However, as there are no standards for labels over
 * different repositories, it is hard to come up with a reasonable set of filters. In addition, it is possible to
 * configure a maximum number of issues that are retrieved from a single repository.
 *
 * For these reasons, this advisor is more a reference implementation for ORT's defects model and not necessarily
 * suitable for production usage.
 */
class GitHubDefects(name: String, gitHubConfiguration: GitHubDefectsConfiguration) : AdviceProvider(name) {
    class Factory : AbstractAdviceProviderFactory<GitHubDefects>("GitHubDefects") {
        override fun create(config: AdvisorConfiguration) =
            GitHubDefects(providerName, config.forProvider { gitHubDefects })
    }

    companion object {
        /**
         * The default number of parallel requests executed by this advisor implementation. This value is used if the
         * corresponding property in the configuration is unspecified. It is chosen rather arbitrarily.
         */
        const val DEFAULT_PARALLEL_REQUESTS = 4
    }

    /**
     * The details returned with each [AdvisorResult] produced by this instance. As this is constant, it can be
     * created once beforehand.
     */
    override val details = AdvisorDetails(providerName, enumSetOf(AdvisorCapability.DEFECTS))

    /** The filters to be applied to issue labels. */
    private val labelFilters = gitHubConfiguration.labelFilter.toLabelFilters()

    /** The maximum number of defects to retrieve. */
    private val maxDefects = gitHubConfiguration.maxNumberOfIssuesPerRepository ?: Int.MAX_VALUE

    /** The number of requests to be processed in parallel. */
    private val parallelRequests = gitHubConfiguration.parallelRequests ?: DEFAULT_PARALLEL_REQUESTS

    /** The service for accessing the GitHub GraphQL API. */
    private val service by lazy {
        GitHubService.create(
            token = gitHubConfiguration.token.orEmpty(),
            url = gitHubConfiguration.endpointUrl?.let { URI(it) } ?: GitHubService.ENDPOINT
        )
    }

    override suspend fun retrievePackageFindings(packages: List<Package>): Map<Package, List<AdvisorResult>> =
        Executors.newFixedThreadPool(parallelRequests).asCoroutineDispatcher().use { context ->
            withContext(context) {
                packages.associateWith { async { findDefectsForPackage(it) } }
                    .mapValues { entry -> entry.value.await() }
            }
        }

    /**
     * Try to find the release in the given list of [releases] for the given [package][pkg]. As there is no direct
     * relation between packages and releases, try several strategies, e.g. based on tag names or commit hashes.
     */
    internal fun findReleaseFor(pkg: Package, releases: List<Release>): Release? {
        val tags = filterVersionNames(pkg.id.version, releases.map(Release::tagName), pkg.id.name)
        if (tags.size == 1) return releases.find { it.tagName == tags.first() }

        val revision = "/${pkg.vcsProcessed.revision}"
        return releases.find { it.tagCommit?.commitUrl?.endsWith(revision) ?: false }
    }

    /**
     * Produce an [AdvisorResult] for the given [package][pkg]. Check whether this package is hosted on GitHub. If so,
     * query the GitHub API; otherwise, return an empty list.
     */
    private suspend fun findDefectsForPackage(pkg: Package): List<AdvisorResult> =
        REGEX_GITHUB.matchEntire(pkg.vcsProcessed.url)?.let { matchResult ->
            val gitHubPkg =
                GitHubPackage(pkg, repoOwner = matchResult.groupValues[1], repoName = matchResult.groupValues[2])
            findDefectsForGitHubPackage(gitHubPkg)
        }.orEmpty()

    /**
     * Produce an [AdvisorResult] for the given [package][pkg], which is assumed to be hosted on GitHub. Query the
     * GitHub repository  for the necessary information.
     */
    private suspend fun findDefectsForGitHubPackage(pkg: GitHubPackage): List<AdvisorResult> {
        log.info { "Finding defects for package '${pkg.pkg.id.toCoordinates()}'." }

        val startTime = Instant.now()
        val ortIssues = mutableListOf<OrtIssue>()

        fun <T> handleError(result: Result<List<T>>, itemType: String): List<T> =
            result.onFailure { exception ->
                exception.showStackTrace()

                ortIssues += createAndLogIssue(
                    providerName,
                    "Failed to load information about $itemType for package '${pkg.pkg.id.toCoordinates()}': " +
                            exception.collectMessagesAsString(),
                    Severity.ERROR
                )
            }.getOrNull().orEmpty()

        val releases = handleError(
            fetchAll { service.repositoryReleases(pkg.repoOwner, pkg.repoName, it) },
            "releases"
        )

        log.debug { "Found ${releases.size} releases for package '${pkg.pkg.id.toCoordinates()}'." }

        val issues = handleError(
            fetchAll(maxDefects) { service.repositoryIssues(pkg.repoOwner, pkg.repoName, it) },
            "issues"
        )

        log.debug { "Found ${issues.size} issues for package '${pkg.pkg.id.toCoordinates()}'." }

        val defects = if (ortIssues.isEmpty()) {
            issuesForRelease(pkg, issues.applyLabelFilters(), releases, ortIssues).also {
                log.debug { "Found ${it.size} defects for package '${pkg.pkg.id.toCoordinates()}'." }
            }
        } else {
            emptyList()
        }

        return defects.takeUnless { it.isEmpty() && ortIssues.isEmpty() }?.let {
            listOf(
                AdvisorResult(
                    advisor = details,
                    summary = AdvisorSummary(startTime, Instant.now(), ortIssues),
                    defects = it
                )
            )
        }.orEmpty()
    }

    /**
     * Filter the list of [issues] for defects affecting the version of the given [package][pkg]. Use [releases] for
     * the version match. Add an entry to [ortIssues] if the release for the package cannot be determined.
     */
    private fun issuesForRelease(
        pkg: GitHubPackage,
        issues: List<Issue>,
        releases: List<Release>,
        ortIssues: MutableList<OrtIssue>
    ): List<Defect> {
        val releaseDate = findReleaseFor(pkg.pkg, releases)?.publishedAt?.let(Instant::parse)
            ?: Instant.now().also {
                ortIssues += createAndLogIssue(
                    providerName,
                    "Could not determine release date for package '${pkg.pkg.id.toCoordinates()}'.",
                    Severity.HINT
                )
            }

        log.debug { "Assuming release date $releaseDate for package '${pkg.pkg.id.toCoordinates()}'." }
        return issues.filter { it.closedAfter(releaseDate) }.map { it.toDefect(releases) }
    }

    /**
     * Return a filtered list of [Issue]s according to the label filters defined in the configuration.
     */
    private fun List<Issue>.applyLabelFilters(): List<Issue> = filter { issue ->
        val labels = issue.labels()
        labelFilters.find { it.matches(labels) }?.including ?: false
    }
}

/**
 * A data class that associates a [Package] with metadata about the GitHub repository it is hosted.
 */
private data class GitHubPackage(
    /** The original package. */
    val pkg: Package,

    /** The owner of the repository. */
    val repoOwner: String,

    /** The repository name. */
    val repoName: String
)

/**
 * A data class representing a filter to be applied to the labels of issues.
 */
private data class LabelFilter(
    /** The expression to match against the label names. */
    val expression: Regex,

    /** Flag whether a match should include issues into the result set. */
    val including: Boolean
) {
    /**
     * Check whether this filter matches at least one of the given [labels].
     */
    fun matches(labels: List<String>): Boolean = labels.any(expression::matches)
}

/**
 * Transform this list of label filter expressions to [LabelFilter]s.
 */
private fun List<String>.toLabelFilters(): List<LabelFilter> =
    map { expression ->
        if (expression.startsWith('!') || expression.startsWith('-')) {
            LabelFilter(expression.substring(1).toFilterRegex(), including = false)
        } else {
            LabelFilter(expression.toFilterRegex(), including = true)
        }
    }

/**
 * Generate a [Regex] that corresponds to this filter expression string. Filter strings use a syntax slightly different
 * from regular expressions; for instance "*" is used as wildcard. Other parts in the string need to be quoted.
 */
private fun String.toFilterRegex(): Regex {
    val parts = split(REGEX_FILTER_WILDCARDS).filterNot(String::isEmpty)
    val expression = parts.joinToString(separator = "", prefix = "(?i)^", postfix = "$") { part ->
        part.takeUnless { part == "*" }?.let(Pattern::quote) ?: ".*"
    }

    return Regex(expression)
}

/** A regular expression to match for GitHub repository URLs. */
private val REGEX_GITHUB = "https://github.com/(.+)/(.+).git".toRegex()

/**
 * A regular expression to split a filter string at wildcard characters. The wildcards are included in the result.
 */
private val REGEX_FILTER_WILDCARDS = "(?<=[*])|(?=[*])".toRegex()

/**
 * Convert this [Issue] to a [Defect], using [releases] to determine the fix release.
 */
private fun Issue.toDefect(releases: List<Release>): Defect =
    Defect(
        id = url.substringAfterLast('/'),
        url = URI(url),
        title = title,
        description = bodyText,
        creationTime = createdAt.toInstant(),
        modificationTime = lastEditedAt.toInstant(),
        closingTime = closedAt.toInstant(),
        state = if (closed) "closed" else "open",
        labels = labels().associateWith { "" },
        fixReleaseUrl = closedAt?.let { releases.firstReleaseAfter(it) }?.url
    )

/**
 * Return a flag whether this issue was closed after the given [time]. This is used to compare the time when an issue
 * was closed with a release date to find the issues affecting a release.
 */
private fun Issue.closedAfter(time: Instant): Boolean =
    !closed || closedAt == null || Instant.parse(closedAt).isAfter(time)

/**
 * Implement paging logic to retrieve the items from all pages for the given [query]. Limit the result to [maxCount]
 * items.
 */
private suspend fun <T> fetchAll(
    maxCount: Int = Int.MAX_VALUE,
    query: suspend (Paging) -> QueryResult<T>
): Result<List<T>> {
    val resultList = mutableListOf<T>()

    // A query function that stops paging when the maximum number of results is reached.
    val wrappedQuery: suspend (Paging) -> QueryResult<T> = { paging ->
        query(paging).map { pageResult ->
            pageResult.takeIf { it.cursor == null || it.items.size + resultList.size <= maxCount }
                ?: pageResult.copy(cursor = null)
        }
    }

    return Paging.fetchAll(
        resultList,
        wrappedQuery,
        { list, result ->
            val remainingCount = maxCount - list.size
            list += result.items.take(remainingCount)
            list
        }
    )
}

/**
 * Return the first release in this sorted list that was published after the given [date]. This is used to determine
 * the fix release for issues based on their closing date.
 */
private fun List<Release>.firstReleaseAfter(date: DateTime): Release? {
    val now = Instant.now().toString()
    return find { (it.publishedAt ?: now) > date }
}

/**
 * Convert this string (which can be *null*) to an [Instant].
 */
private fun String?.toInstant() = this?.let(Instant::parse)
