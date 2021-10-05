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
import com.github.tomakehurst.wiremock.client.WireMock.configureFor
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
import org.ossreviewtoolkit.utils.test.shouldNotBeNull

class GitHubServiceTest : WordSpec({
    val wiremock = WireMockServer(
        WireMockConfiguration.options()
            .dynamicPort()
            .usingFilesUnderDirectory(TEST_FILES_ROOT)
    )

    beforeSpec {
        wiremock.start()
        configureFor(wiremock.port())
    }

    afterSpec {
        wiremock.stop()
    }

    beforeTest {
        wiremock.resetAll()
    }

    "error handling" should {
        "detect a failure response from the server" {
            wiremock.stubFor(
                post(anyUrl())
                    .willReturn(aResponse().withStatus(403))
            )

            val service = createService(wiremock)

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
            wiremock.stubFor(
                post(anyUrl())
                    .willReturn(
                        aResponse().withStatus(200)
                            .withBodyFile("error_response.json")
                    )
            )

            val service = createService(wiremock)

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
            wiremock.stubQuery("issues", repoVariablesRegex(), "issues_response.json")

            val service = createService(wiremock)

            val issuesResult = service.repositoryIssues(REPO_OWNER, REPO_NAME)

            issuesResult.check { issues ->
                val titles = issues.map(Issue::title)

                titles should containExactly(
                    "No license and copyright information in the files",
                    "Consider using a \"purl\" as the package identifier",
                    "downloader: Add Mercurial support"
                )

                with(issues[2]) {
                    url shouldBe "https://github.com/oss-review-toolkit/ort/issues/85"
                    bodyText shouldBe "Add support for Mercurial repositories to the downloader module."
                    closed shouldBe true
                    closedAt shouldBe "2017-12-03T18:06:57Z"
                    createdAt shouldBe "2017-12-01T15:19:39Z"
                    lastEditedAt should beNull()

                    val labels = labels?.edges.orEmpty().mapNotNull { it?.node?.name }
                    labels should containExactlyInAnyOrder("enhancement", "downloader")
                }
            }
        }
    }
})

private const val TEST_FILES_ROOT = "src/test/assets"
private const val TOKEN = "<test_oauth_token>"
private const val ENDPOINT = "/graphql"
private const val REPO_OWNER = "oss-review-toolkit"
private const val REPO_NAME = "ort"

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
private fun variablesRegex(variables: List<Pair<String, String>>): String {
    val fields = variables.joinToString(separator = ",\\s*", prefix = "\\{\\s*", postfix = "\\s*}") { variable ->
        field(variable.first, variable.second.q().p())
    }

    val variableObj = field("variables", fields)
    return ".*$variableObj.*"
}

/**
 * Generate a regular expression that matches the variables for a specific repository. Optionally, a [cursor]
 * variable can be provided.
 */
private fun repoVariablesRegex(cursor: String? = null): String =
    variablesRegex(listOfNotNull(
        "repo_owner" to REPO_OWNER,
        "repo_name" to REPO_NAME,
        cursor?.let { "cursor" to it }
    ))

/**
 * Read an expected GraphQL query from a file with the given [name] and convert it to a form, so that it can be
 * compared with the request received by the mock server. In the request, new lines are replaced by "\n", and the
 * final newline is missing.
 */
private fun readExpectedQuery(name: String): String {
    val lines = File("src/main/assets/$name.graphql").readLines()
    return lines.joinToString("\\n")
}

/**
 * Check whether this [Result] is successful and contains a non-null value. If so, invoke [block] on the value.
 */
private fun <T> Result<T>.check(block: (T) -> Unit) {
    shouldBeSuccess { value ->
        value.shouldNotBeNull { block(this) }
    }
}
