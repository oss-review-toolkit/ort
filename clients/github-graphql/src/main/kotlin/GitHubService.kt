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

import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import com.expediagroup.graphql.client.types.GraphQLClientError
import com.expediagroup.graphql.client.types.GraphQLClientRequest
import com.expediagroup.graphql.client.types.GraphQLClientResponse

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.features.defaultRequest
import io.ktor.client.request.header

import java.net.URI

import kotlin.time.measureTimedValue

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.clients.github.issuesquery.Issue
import org.ossreviewtoolkit.clients.github.releasesquery.Release

/**
 * An exception class to report a GraphQL query execution that yielded errors. From instance, details about the
 * errors that occurred can be queried.
 */
class QueryException(
    message: String,

    /**
     * A list with information about the errors that have been found in the query result.
     */
    val errors: List<GraphQLClientError>
) : Exception(message)

/**
 * A service class for accessing information from the GitHub GraphQL API.
 *
 * This class provides a limited number of query methods to obtain different types of entities available in GitHub's
 * data model. The supported entities are derived from use cases of ORT that require interactions with GitHub.
 *
 * See https://docs.github.com/en/graphql.
 */
class GitHubService private constructor(
    val client: GraphQLKtorClient
) {
    companion object : Logging {
        /**
         * The default endpoint URL for accessing the GitHub GraphQL API.
         */
        val ENDPOINT = URI("https://api.github.com/graphql")

        /**
         * Create a new [GitHubService] instance that uses the given [token] to authenticate against the GitHub API.
         * Optionally, the [url] for the GitHub GraphQL endpoint can be configured, and a HTTP [client] can be
         * specified.
         */
        fun create(token: String, url: URI = ENDPOINT, client: HttpClient? = null): GitHubService {
            val clientConfig: HttpClientConfig<*>.() -> Unit = {
                defaultRequest {
                    header("Authorization", "Bearer $token")
                }
            }

            val httpClient = client?.config(clientConfig) ?: HttpClient(clientConfig)

            return GitHubService(GraphQLKtorClient(url.toURL(), httpClient))
        }
    }

    /**
     * Return a list with [Issue]s contained in [repository] owned by [owner] using the specified [paging].
     */
    suspend fun repositoryIssues(
        owner: String,
        repository: String,
        paging: Paging = Paging.INITIAL
    ): QueryResult<Issue> =
        runCatching {
            val query = IssuesQuery(IssuesQuery.Variables(owner, repository, paging.pageSize, paging.cursor))
            val result = client.executeAndCheck(query)

            val issuesConnection = result.data?.repository?.issues
            val pageInfo = issuesConnection?.pageInfo
            val nextCursor = pageInfo?.endCursor?.takeIf { pageInfo.hasNextPage }
            PagedResult(issuesConnection?.edges.orEmpty().mapNotNull { it?.node }, paging.pageSize, nextCursor)
        }

    /**
     * Return a list with [Release]s contained in [repository] owned by [owner] using the specified [paging].
     */
    suspend fun repositoryReleases(
        owner: String,
        repository: String,
        paging: Paging = Paging.INITIAL
    ): QueryResult<Release> = runCatching {
        val query = ReleasesQuery(ReleasesQuery.Variables(owner, repository, paging.pageSize, paging.cursor))
        val result = client.executeAndCheck(query)

        val releasesConnection = result.data?.repository?.releases
        val pageInfo = releasesConnection?.pageInfo
        val nextCursor = pageInfo?.endCursor?.takeIf { pageInfo.hasNextPage }
        PagedResult(releasesConnection?.edges.orEmpty().mapNotNull { it?.node }, paging.pageSize, nextCursor)
    }
}

/**
 * Return a list with the names of the labels defined for this [Issue].
 */
fun Issue.labels(): List<String> = labels?.edges.orEmpty().mapNotNull { it?.node?.name }

/**
 * Execute the given [request] and check whether the result contains errors. If so, throw a [QueryException].
 */
private suspend fun <T : Any> GraphQLKtorClient.executeAndCheck(
    request: GraphQLClientRequest<T>
): GraphQLClientResponse<T> {
    val (result, duration) = measureTimedValue { execute(request) }

    GitHubService.logger.debug { "Executed query '${request.operationName}' in $duration." }

    result.errors?.let { errors ->
        throw QueryException("Result of query '${request.operationName}' contains errors.", errors)
    }

    return result
}
