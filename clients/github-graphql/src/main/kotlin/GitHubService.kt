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

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.features.defaultRequest
import io.ktor.client.request.header

import java.net.URI

import org.ossreviewtoolkit.clients.github.issuesquery.Issue

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
    companion object {
        /**
         * The default endpoint URL for accessing the GitHub GraphQL API.
         */
        val ENDPOINT = URI("https://api.github.com/graphql")

        /**
         * Create a new [GitHubService] instance that uses the given [token] to authenticate against the GitHub API.
         * Optionally, the [url] for the GitHub GraphQL endpoint can be configured.
         */
        fun create(token: String, url: URI = ENDPOINT): GitHubService {
            val client = HttpClient(engineFactory = OkHttp) {
                defaultRequest {
                    header("Authorization", "Bearer $token")
                }
            }

            return GitHubService(GraphQLKtorClient(url.toURL(), client))
        }
    }

    /**
     * Return a list with [Issue]s contained in [repository] owned by [owner]. If paging is used, with [cursor] the
     * start of the page can be determined.
     */
    suspend fun repositoryIssues(owner: String, repository: String, cursor: String? = null): List<Issue> {
        val query = IssuesQuery(IssuesQuery.Variables(owner, repository, cursor))
        val result = client.execute(query)

        return result.data?.repository?.issues?.edges.orEmpty().mapNotNull { it?.node }
    }
}
