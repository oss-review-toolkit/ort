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

package org.ossreviewtoolkit.clients.github

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.result.shouldBeFailureOfType
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.contain

import io.ktor.client.features.ClientRequestException

import java.io.File
import java.net.ConnectException
import java.net.ServerSocket
import java.net.URI
import java.util.regex.Pattern

import org.ossreviewtoolkit.clients.github.issuesquery.Issue

class GitHubServiceTest : WordSpec({
    val server = WireMockServer(
        WireMockConfiguration.options()
            .dynamicPort()
            .usingFilesUnderDirectory(TEST_FILES_ROOT)
    )

    beforeSpec {
        server.start()
    }

    afterSpec {
        server.stop()
    }

    beforeEach {
        server.resetAll()
    }

    "error handling" should {
        "detect a failure response from the server" {
            server.stubFor(
                post(anyUrl())
                    .willReturn(aResponse().withStatus(403))
            )

            val service = createService(server)

            val issuesResult = service.repositoryIssues(REPO_OWNER, REPO_NAME)

            issuesResult.shouldBeFailureOfType<ClientRequestException>()
        }

        "handle a connection error" {
            // Find a port on which no service is running.
            val port = ServerSocket(0).use { it.localPort }
            val serverUrl = "http://localhost:$port"

            val service = GitHubService.create(TOKEN, URI(serverUrl))

            val issuesResult = service.repositoryIssues(REPO_OWNER, REPO_NAME)

            issuesResult.shouldBeFailureOfType<ConnectException>()
        }

        "detect errors in the GraphQL result" {
            server.stubFor(
                post(anyUrl())
                    .willReturn(
                        aResponse().withStatus(200)
                            .withBodyFile("error_response.json")
                    )
            )

            val service = createService(server)

            val issuesResult = service.repositoryIssues(REPO_OWNER, REPO_NAME)

            issuesResult.shouldBeFailureOfType<QueryException>()
            val exception = issuesResult.exceptionOrNull() as QueryException
            exception.message should contain("'IssuesQuery' contains errors")
            exception.errors shouldHaveSize 1
            with(exception.errors.first()) {
                message shouldBe
                        "Requesting 101 records on the `issues` connection exceeds the `first` limit of 100 records."
                path should containExactly("repository", "issues")
            }
        }
    }

    "repositoryIssues" should {
        "return the issues of a repository" {
            server.stubQuery("issues", repoVariablesRegex(Paging.MAX_PAGE_SIZE), "issues_response.json")

            val service = createService(server)

            val issuesResult = service.repositoryIssues(REPO_OWNER, REPO_NAME)

            issuesResult.shouldBeSuccess { pagedResult ->
                val titles = pagedResult.items.map(Issue::title)

                titles should containExactly(
                    "No license and copyright information in the files",
                    "Consider using a \"purl\" as the package identifier",
                    "downloader: Add Mercurial support"
                )

                with(pagedResult.items[2]) {
                    url shouldBe "https://github.com/oss-review-toolkit/ort/issues/85"
                    bodyText shouldBe "Add support for Mercurial repositories to the downloader module."
                    closed shouldBe true
                    closedAt shouldBe "2017-12-03T18:06:57Z"
                    createdAt shouldBe "2017-12-01T15:19:39Z"
                    lastEditedAt should beNull()

                    labels() should containExactlyInAnyOrder("enhancement", "downloader")
                }

                pagedResult.pageSize shouldBe Paging.MAX_PAGE_SIZE
                pagedResult.cursor should beNull()
            }
        }

        "support paging" {
            val paging = Paging(pageSize = PAGE_SIZE, cursor = "some-cursor")
            server.stubQuery("issues", repoVariablesRegex(cursor = paging.cursor), "issues_response_paged.json")

            val service = createService(server)

            val issuesResult = service.repositoryIssues(REPO_OWNER, REPO_NAME, paging)

            issuesResult.shouldBeSuccess { pagedResult ->
                val titles = pagedResult.items.map(Issue::title)

                titles should containExactly(
                    "No license and copyright information in the files",
                    "Consider using a \"purl\" as the package identifier",
                    "downloader: Add Mercurial support"
                )

                pagedResult.pageSize shouldBe PAGE_SIZE
                pagedResult.cursor shouldBe "Y3Vyc29yOnYyOpHOEJmL_w=="
            }
        }
    }

    "repositoryReleases" should {
        "return the releases of a repository" {
            server.stubQuery("releases", repoVariablesRegex(Paging.MAX_PAGE_SIZE), "releases_response.json")

            val service = createService(server)

            val releasesResult = service.repositoryReleases(REPO_OWNER, REPO_NAME)

            releasesResult.shouldBeSuccess { pagedResult ->
                val names = pagedResult.items.mapNotNull { it.name }

                names should containExactly(
                    "Releasing 0.4.4",
                    "Releasing 0.4.5 version",
                    "Releasing 0.4.6 version",
                    "Releasing 0.4.8 version",
                    "Releasing 0.4.11"
                )

                with(pagedResult.items.first()) {
                    url shouldBe "https://github.com/flipkart-incubator/zjsonpatch/releases/tag/0.4.4"
                    publishedAt shouldBe "2018-04-12T18:47:35Z"
                    tagName shouldBe "0.4.4"
                    tagCommit?.commitUrl should contain("1ec03fce40b59e2559e3f3affde27042f1a9b644")
                }

                pagedResult.pageSize shouldBe Paging.MAX_PAGE_SIZE
                pagedResult.cursor should beNull()
            }
        }

        "support paging" {
            val paging = Paging(pageSize = PAGE_SIZE, cursor = "some-cursor")
            server.stubQuery("releases", repoVariablesRegex(cursor = paging.cursor), "releases_response_paged.json")

            val service = createService(server)

            val releasesResult = service.repositoryReleases(REPO_OWNER, REPO_NAME, paging)

            releasesResult.shouldBeSuccess { pagedResult ->
                pagedResult.items shouldHaveSize 2
                pagedResult.pageSize shouldBe PAGE_SIZE
                pagedResult.cursor shouldBe "Y3Vyc29yOnYyOpK5MjAyMC0wNi0yM1QxMzoyNzo1NiswMjowMM4BqJGR"
            }
        }
    }
})

private const val TEST_FILES_ROOT = "src/test/assets"
private const val TOKEN = "<test_oauth_token>"
private const val ENDPOINT = "/graphql"
private const val REPO_OWNER = "oss-review-toolkit"
private const val REPO_NAME = "ort"
private const val PAGE_SIZE = 32

/**
 * Create a [GitHubService] instance that is configured to access the given mock [server].
 */
private fun createService(server: WireMockServer): GitHubService =
    GitHubService.create(TOKEN, URI("http://localhost:${server.port()}$ENDPOINT"))

/**
 * Prepare this mock server to answer a GraphQL read from [queryFileName] containing variables matched by
 * [variablesRegex] with a response read from [responseFile].
 */
private fun WireMockServer.stubQuery(queryFileName: String, variablesRegex: String, responseFile: String) {
    stubFor(
        post(urlPathEqualTo(ENDPOINT))
            .withHeader("Authorization", equalTo("Bearer $TOKEN"))
            .withRequestBody(matching(variablesRegex))
            .withRequestBody(containing(readExpectedQuery(queryFileName)))
            .willReturn(
                aResponse().withStatus(200)
                    .withBodyFile(responseFile)
            )
    )
}

/**
 * Surround this string by quotes. This is useful for generating JSON-like content.
 */
private fun String.q() = "\"$this\""

/**
 * Quote this string, so that it can be added verbatim to a regular expression.
 */
private fun String.p() = Pattern.quote(this)

/**
 * Generate a JSON field (a [key] [value] pair). The [key] is surrounded by quotes, the [value] not, as it can be a
 * complex object.
 */
private fun field(key: String, value: String) = "${key.q().p()}\\s*:\\s*$value"

/**
 * Generate a regular expression that matches the given pairs of [variables].
 */
private fun variablesRegex(variables: List<Pair<String, Any>>): String {
    val fields = variables.joinToString(separator = ",\\s*", prefix = "\\{\\s*", postfix = "\\s*}") { (n, v) ->
        val value = when (v) {
            is String -> v.q().p()
            else -> v.toString()
        }
        field(n, value)
    }

    val variableObj = field("variables", fields)
    return ".*$variableObj.*"
}

/**
 * Generate a regular expression that matches the variables for a specific repository. Optionally, a [cursor]
 * variable can be provided.
 */
private fun repoVariablesRegex(pageSize: Int = PAGE_SIZE, cursor: String? = null): String =
    variablesRegex(
        listOfNotNull(
            "repo_owner" to REPO_OWNER,
            "repo_name" to REPO_NAME,
            "page_size" to pageSize,
            cursor?.let { "cursor" to it }
        )
    )

/**
 * Read an expected GraphQL query from a file with the given [name] and convert it to a form, so that it can be
 * compared with the request received by the mock server. In the request, new lines are replaced by "\n", and the
 * final newline is missing.
 */
private fun readExpectedQuery(name: String): String {
    val lines = File("src/main/assets/$name.graphql").readLines()
    return lines.joinToString("\\n")
}
