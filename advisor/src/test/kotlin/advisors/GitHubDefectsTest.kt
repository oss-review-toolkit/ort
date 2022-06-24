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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.contain

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify

import java.net.URI
import java.time.Instant

import org.ossreviewtoolkit.clients.github.DateTime
import org.ossreviewtoolkit.clients.github.GitHubService
import org.ossreviewtoolkit.clients.github.PagedResult
import org.ossreviewtoolkit.clients.github.Paging
import org.ossreviewtoolkit.clients.github.issuesquery.Issue
import org.ossreviewtoolkit.clients.github.issuesquery.Label
import org.ossreviewtoolkit.clients.github.issuesquery.LabelConnection
import org.ossreviewtoolkit.clients.github.issuesquery.LabelEdge
import org.ossreviewtoolkit.clients.github.releasesquery.Commit
import org.ossreviewtoolkit.clients.github.releasesquery.Release
import org.ossreviewtoolkit.model.AdvisorCapability
import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.Defect
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.model.config.GitHubDefectsConfiguration
import org.ossreviewtoolkit.utils.common.enumSetOf
import org.ossreviewtoolkit.utils.test.shouldNotBeNull

class GitHubDefectsTest : WordSpec({
    "retrievePackageFindings" should {
        "return an empty result for packages not hosted on GitHub" {
            val vcs = VcsInfo(type = VcsType.GIT, url = "https://www.example.org/repo/test.git", revision = "1")
            val pkg = Package.EMPTY.copy(vcs = vcs, vcsProcessed = vcs)

            val advisor = createAdvisor()
            val results = advisor.retrievePackageFindings(listOf(pkg))

            results.keys should containExactly(pkg)
            results[pkg] shouldNotBeNull {
                this should beEmpty()
            }
        }

        "return an empty result for a package without issues" {
            val pkg = createPackage()
            val releases = listOf(
                Release("https://release1", "rHit", time(2, 20), GIT_TAG, Commit(commitUrl("0987654321"))),
            )

            createGitHubServiceMock().configureResults(emptyList(), releases)

            val advisor = createAdvisor()
            val results = advisor.retrievePackageFindings(listOf(pkg))

            results.keys should containExactly(pkg)
            results[pkg] shouldNotBeNull {
                this should beEmpty()
            }
        }

        "return issues for a package" {
            val pkg = createPackage()
            val issues = listOf(createIssue(index = 1), createIssue(index = 2), createIssue(index = 3))

            createGitHubServiceMock().configureResults(issues, emptyList())

            val advisor = createAdvisor()
            val result = advisor.getSingleResult(pkg)

            result.defects should containExactlyInAnyOrder(
                createDefect(index = 1),
                createDefect(index = 2),
                createDefect(index = 3)
            )
        }

        "return only open issues for a package if the release cannot be matched" {
            val pkg = createPackage()
            val issues =
                listOf(createIssue(index = 1), createIssue(index = 2, closedTime = time(2, 2)), createIssue(index = 3))

            createGitHubServiceMock().configureResults(issues, emptyList())

            val advisor = createAdvisor()
            val result = advisor.getSingleResult(pkg)

            result.defects should containExactlyInAnyOrder(
                createDefect(index = 1),
                createDefect(index = 3)
            )
        }

        "create an OrtIssue for a package if the release cannot be matched" {
            val pkg = createPackage()
            val issues = listOf(createIssue(index = 1, closedTime = time(2, 2)))

            createGitHubServiceMock().configureResults(issues, emptyList())

            val advisor = createAdvisor()
            val result = advisor.getSingleResult(pkg)

            result.shouldContainIssue(pkg, Severity.HINT, "not determine release date")
        }

        "return only issues relevant for a specific release" {
            val pkg = createPackage()
            val releases = listOf(
                Release("https://release1", "r1.0", time(1, 1), "1.0", Commit(commitUrl("1234567890"))),
                Release("https://release2", "rHit", time(2, 20), GIT_TAG, Commit(commitUrl("0987654321"))),
                Release("https://release3", "rLater", time(4, 12), "1.1", Commit(commitUrl("abcdefghij")))
            )
            val issues = listOf(
                createIssue(index = 1, time(1, 10)),
                createIssue(index = 2),
                createIssue(index = 3, time(2, 21)),
                createIssue(index = 4, time(4, 10))
            )

            createGitHubServiceMock().configureResults(issues, releases)

            val advisor = createAdvisor()
            val result = advisor.getSingleResult(pkg)

            result.summary.issues should beEmpty()
            result.defects should containExactlyInAnyOrder(
                createDefect(index = 2),
                createDefect(index = 3, closedTime = time(2, 21), releases[2]),
                createDefect(index = 4, closedTime = time(4, 10), releases[2])
            )
        }

        "handle an error when querying issues from the repository" {
            val pkg = createPackage()
            val releases = listOf(
                Release("https://release1", "r1.0", time(1, 1), "1.0", Commit(commitUrl("1234567890")))
            )

            val service = createGitHubServiceMock()
            coEvery {
                service.repositoryReleases(REPO_OWNER, REPO)
            } returns Result.success(PagedResult(releases, 100, null))
            coEvery {
                service.repositoryIssues(REPO_OWNER, REPO)
            } returns Result.failure(IllegalStateException("Test exception"))

            val advisor = createAdvisor()
            val result = advisor.getSingleResult(pkg)

            result.shouldContainIssue(pkg, Severity.ERROR, "Test exception")
        }

        "handle an error when querying releases from the repository" {
            val pkg = createPackage()
            val issues = listOf(createIssue(index = 1))

            val service = createGitHubServiceMock()
            coEvery {
                service.repositoryIssues(REPO_OWNER, REPO)
            } returns Result.success(PagedResult(issues, 100, null))
            coEvery {
                service.repositoryReleases(REPO_OWNER, REPO)
            } returns Result.failure(IllegalStateException("Test exception"))

            val advisor = createAdvisor()
            val result = advisor.getSingleResult(pkg)

            result.shouldContainIssue(pkg, Severity.ERROR, "Test exception")
        }

        "only retrieve the configured number of defects" {
            val pkg = createPackage()
            val release = Release("https://release", "r1", time(3, 1), GIT_TAG, Commit(commitUrl("0987654321")))

            val maxDefectsCount = 10
            val issueIndices = 1..30
            val issues = issueIndices.map { index -> createIssue(index) }.reversed()
            val expectedDefects = issueIndices.map { index -> createDefect(index) }.takeLast(maxDefectsCount)

            createGitHubServiceMock().configureResults(issues, listOf(release))

            val advisor = createAdvisor(maxDefectsCount = maxDefectsCount)
            val result = advisor.getSingleResult(pkg)

            result.defects should containExactlyInAnyOrder(expectedDefects)
        }

        "handle paging in queries correctly" {
            val pkg = createPackage()
            val release1 = Release("https://release1", "r1.0", time(1, 1), "1.0", Commit(commitUrl("1234567890")))
            val release2 = Release("https://release2", "rHit", time(2, 20), GIT_TAG, Commit(commitUrl("0987654321")))

            val service = createGitHubServiceMock()
            coEvery {
                service.repositoryIssues(REPO_OWNER, REPO)
            } returns Result.success(PagedResult(listOf(createIssue(index = 1)), 100, "c1"))
            coEvery {
                service.repositoryIssues(REPO_OWNER, REPO, Paging(cursor = "c1"))
            } returns Result.success(PagedResult(listOf(createIssue(index = 2)), 100, null))
            coEvery {
                service.repositoryReleases(REPO_OWNER, REPO)
            } returns Result.success(PagedResult(listOf(release1), 100, "c2"))
            coEvery {
                service.repositoryReleases(REPO_OWNER, REPO, Paging(cursor = "c2"))
            } returns Result.success(PagedResult(listOf(release2), 100, null))

            val advisor = createAdvisor()
            val result = advisor.getSingleResult(pkg)

            result.summary.issues should beEmpty()
            result.defects should containExactlyInAnyOrder(createDefect(index = 1), createDefect(index = 2))
        }

        "only retrieve the configured number of defects in paged queries" {
            val pkg = createPackage()

            val service = createGitHubServiceMock()
            coEvery {
                service.repositoryIssues(REPO_OWNER, REPO)
            } returns Result.success(PagedResult(listOf(createIssue(index = 4), createIssue(index = 3)), 100, "c1"))
            coEvery {
                service.repositoryIssues(REPO_OWNER, REPO, Paging(cursor = "c1"))
            } returns Result.success(PagedResult(listOf(createIssue(index = 2), createIssue(index = 1)), 100, "c2"))
            coEvery {
                service.repositoryReleases(REPO_OWNER, REPO)
            } returns Result.success(PagedResult(emptyList(), 100, null))

            val advisor = createAdvisor(maxDefectsCount = 3)
            val result = advisor.getSingleResult(pkg)

            result.defects should containExactlyInAnyOrder(
                createDefect(index = 2),
                createDefect(index = 3),
                createDefect(4)
            )
        }
    }

    "findReleaseFor" should {
        "do sophisticated matching of tags" {
            val releases = listOf(
                Release("https://release1", "r1.0", time(1, 1), "1.0", Commit(commitUrl("1234567890"))),
                Release("https://release2", "rHit", time(2, 20), "ort-$GIT_TAG", Commit(commitUrl("0987654321"))),
                Release("https://release3", "rLater", time(4, 12), "1.1", Commit(commitUrl("abcdefghij")))
            )

            val advisor = createAdvisor()

            advisor.findReleaseFor(createPackage(), releases) shouldBe releases[1]
        }

        "fallback on the commit hash" {
            val commitHash = "0123456789abcdef"
            val pkg = createPackage(commitHash)
            val releases = listOf(
                Release("https://release1", "r1.0", time(1, 1), "1.0", Commit(commitUrl("1234567890"))),
                Release("https://release2", "rHit", time(2, 20), "other", Commit(commitUrl(commitHash))),
                Release("https://release3", "rLater", time(4, 12), "1.1", Commit(commitUrl("abcdefghij")))
            )

            val advisor = createAdvisor()

            advisor.findReleaseFor(pkg, releases) shouldBe releases[1]
        }
    }

    "the GitHubService instance" should {
        "use the configured endpoint URI" {
            val endpointUri = URI("https://www.example.org/alternative/endpoint")
            createGitHubServiceMock(endpointUri).configureResults(emptyList(), emptyList())

            val advisor = createAdvisor(url = endpointUri.toString())
            advisor.retrievePackageFindings(listOf(createPackage()))

            verify {
                GitHubService.create(GITHUB_TOKEN, endpointUri, any())
            }
        }
    }

    "label filters" should {
        "filter for specific labels" {
            val pkg = createPackage()
            val issues = listOf(createIssue(index = 1), createIssue(index = 2), createIssue(index = 3))

            createGitHubServiceMock().configureResults(issues, emptyList())

            val labelFilter = listOf("label1", "label3")
            val advisor = createAdvisor(labelFilter = labelFilter)
            val result = advisor.getSingleResult(pkg)

            result.defects should containExactlyInAnyOrder(
                createDefect(index = 1),
                createDefect(index = 3)
            )
        }

        "exclude specific labels" {
            val pkg = createPackage()
            val issues = listOf(createIssue(index = 1), createIssue(index = 2), createIssue(index = 3))

            createGitHubServiceMock().configureResults(issues, emptyList())

            val labelFilter = listOf("!label1", "-label3", "*")
            val advisor = createAdvisor(labelFilter = labelFilter)
            val result = advisor.getSingleResult(pkg)

            result.defects should containExactlyInAnyOrder(createDefect(index = 2))
        }

        "do regex matches on label names" {
            val pkg = createPackage()
            val issues = listOf(createIssue(index = 1), createIssue(index = 22))

            createGitHubServiceMock().configureResults(issues, emptyList())

            val labelFilter = listOf("label*")
            val advisor = createAdvisor(labelFilter = labelFilter)
            val result = advisor.getSingleResult(pkg)

            result.defects should containExactlyInAnyOrder(
                createDefect(index = 1),
                createDefect(index = 22)
            )
        }

        "handle complex regex expressions on label names" {
            val pkg = createPackage()
            val label = LabelEdge(Label("someComplexExpression?!"))
            val issue = createIssue(index = 1).copy(labels = LabelConnection(listOf(label)))

            createGitHubServiceMock().configureResults(listOf(issue), emptyList())

            val labelFilter = listOf("some*Expression?!")
            val advisor = createAdvisor(labelFilter = labelFilter)
            val result = advisor.getSingleResult(pkg)

            result.defects shouldHaveSize 1
        }

        "do matches in a case-insensitive manner" {
            val pkg = createPackage()
            val issues = listOf(createIssue(index = 1), createIssue(index = 2), createIssue(index = 3))

            createGitHubServiceMock().configureResults(issues, emptyList())

            val labelFilter = listOf("LABEL1", "Label3")
            val advisor = createAdvisor(labelFilter = labelFilter)
            val result = advisor.getSingleResult(pkg)

            result.defects should containExactlyInAnyOrder(
                createDefect(index = 1),
                createDefect(index = 3)
            )
        }
    }

    "details" should {
        "contain correct advisor details" {
            val advisor = createAdvisor()

            advisor.details shouldBe AdvisorDetails("GitHubDefects", enumSetOf(AdvisorCapability.DEFECTS))
        }
    }
})

private const val GITHUB_TOKEN = "<github_access_token>"
private const val REPO_OWNER = "oss-review-toolkit"
private const val REPO = "ort"
private const val GIT_REPO_PREFIX = "https://github.com/$REPO_OWNER/$REPO"
private const val GIT_REPO_URL = "$GIT_REPO_PREFIX.git"
private const val GIT_TAG = "1.0.1"
private val PACKAGE_ID =
    Identifier(type = "Gradle", namespace = "org.oss-review-toolkit", name = "ort", version = GIT_TAG)

/**
 * Create a mock for the [GitHubService] and prepare the static factory method to return this mock, expecting the
 * provided [url].
 */
private fun createGitHubServiceMock(url: URI = GitHubService.ENDPOINT): GitHubService {
    val service = mockk<GitHubService>()

    mockkObject(GitHubService)
    every { GitHubService.create(GITHUB_TOKEN, url, any()) } returns service

    return service
}

/**
 * Create a test advisor instance using the factory with the configured endpoint [url]. Set [labelFilter] and
 * [the maximum number of defects to retrieve][maxDefectsCount] in the advisor's configuration.
 */
private fun createAdvisor(
    url: String? = null,
    labelFilter: List<String>? = null,
    maxDefectsCount: Int? = null
): GitHubDefects {
    val githubConfig = GitHubDefectsConfiguration(token = GITHUB_TOKEN, endpointUrl = url)
        .run { labelFilter?.let { copy(labelFilter = it) } ?: this }
        .run { maxDefectsCount?.let { copy(maxNumberOfIssuesPerRepository = it) } ?: this }
    val advisorConfig = AdvisorConfiguration(gitHubDefects = githubConfig)

    val factory = GitHubDefects.Factory()
    return factory.create(advisorConfig)
}

/**
 * Invoke this advisor for the given [package][pkg]. Make sure that a single result is available and return it.
 */
private suspend fun GitHubDefects.getSingleResult(pkg: Package): AdvisorResult {
    val results = retrievePackageFindings(listOf(pkg))

    results.keys should containExactly(pkg)
    val result = results.getValue(pkg).single()
    result.advisor.name shouldBe "GitHubDefects"
    result.advisor.capabilities shouldBe enumSetOf(AdvisorCapability.DEFECTS)

    return result
}

/**
 * Prepare this service mock to expect requests for the issues and releases of the test repository. Answer these
 * with [issues] and [releases], respective.
 */
private fun GitHubService.configureResults(issues: List<Issue>, releases: List<Release>) {
    coEvery {
        repositoryIssues(REPO_OWNER, REPO)
    } returns Result.success(PagedResult(issues, 100, null))
    coEvery {
        repositoryReleases(REPO_OWNER, REPO)
    } returns Result.success(PagedResult(releases, 100, null))
}

/**
 * Check whether this [AdvisorResult] contains exactly one issue for the given [pkg] with [expectedSeverity] and a
 * message that includes [includes].
 */
private fun AdvisorResult.shouldContainIssue(pkg: Package, expectedSeverity: Severity, includes: String) {
    defects should beEmpty()
    summary.issues shouldHaveSize 1
    with(summary.issues.first()) {
        source shouldBe advisor.name
        message should contain(pkg.id.toCoordinates())
        message should contain(includes)
        severity shouldBe expectedSeverity
    }
}

/**
 * Create a test package hosted in the test GitHub repo with the given [revision].
 */
private fun createPackage(revision: String = GIT_TAG): Package {
    val vcs = VcsInfo(type = VcsType.GIT, url = GIT_REPO_URL, revision = revision)
    return Package.EMPTY.copy(id = PACKAGE_ID, vcs = vcs, vcsProcessed = vcs)
}

/**
 * Create a test issue based on the given [index] with the provided [closedTime].
 */
private fun createIssue(index: Int, closedTime: DateTime? = null): Issue {
    val label = LabelEdge(Label("label$index"))
    val labelsCon = LabelConnection(listOf(label))
    return Issue(
        title = "TestIssue$index",
        url = "https://github.com/oss-review-toolkit/ort/issues/$index",
        bodyText = "Description $index",
        closed = closedTime != null,
        closedAt = closedTime,
        createdAt = time(1, index),
        lastEditedAt = null,
        labels = labelsCon
    )
}

/**
 * Create a test [Defect] based on the given [index], [closedTime], and [fixRelease].
 */
private fun createDefect(index: Int, closedTime: DateTime? = null, fixRelease: Release? = null): Defect =
    Defect(
        id = index.toString(),
        url = URI("https://github.com/oss-review-toolkit/ort/issues/$index"),
        title = "TestIssue$index",
        description = "Description $index",
        creationTime = time(1, index).toInstant(),
        labels = mapOf("label$index" to ""),
        state = if (closedTime != null) "closed" else "open",
        closingTime = closedTime?.toInstant(),
        fixReleaseUrl = fixRelease?.url
    )

/**
 * Generate the URL of a specific commit to the test repository based on the given [hash].
 */
private fun commitUrl(hash: String) = "$GIT_REPO_PREFIX/commit/$hash"

/**
 * Format this number to a string with two digits using a leading zero.
 */
private fun Int.toTime() = toString().padStart(2, '0')

/**
 * Generate a DateTime literal with the given [month] and [day]. This is used to generate timestamps.
 */
private fun time(month: Int, day: Int): String = "2021-${month.toTime()}-${day.toTime()}T12:00:00.123Z"

/**
 * Create an [Instant] from this string.
 */
private fun String.toInstant() = Instant.parse(this)
