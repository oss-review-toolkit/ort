/*
 * Copyright (C) 2020-2022 Bosch.IO GmbH
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

package org.ossreviewtoolkit.model.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * The base configuration model of the advisor.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AdvisorConfiguration(
    val gitHubDefects: GitHubDefectsConfiguration? = null,
    val nexusIq: NexusIqConfiguration? = null,
    val osv: OsvConfiguration? = null,
    val vulnerableCode: VulnerableCodeConfiguration? = null,

    /**
     * A map with generic options for advice providers using the provider name as key. While the advice providers
     * shipped with ORT can access their configuration in a type-safe way via the other properties in this class,
     * this map offers a way for external advisor plugins to query configuration information.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    val options: Map<String, Options>? = null
)

/**
 * The configuration for Nexus IQ as a security vulnerability provider.
 */
data class NexusIqConfiguration(
    /**
     * The URL to use for REST API requests against the server.
     */
    val serverUrl: String,

    /**
     * A URL to use as a base for browsing vulnerability details. Defaults to the server URL.
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    val browseUrl: String = serverUrl,

    /**
     * The username to use for authentication. If not both [username] and [password] are provided, authentication is
     * disabled.
     */
    val username: String?,

    /**
     * The password to use for authentication. If not both [username] and [password] are provided, authentication is
     * disabled.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    val password: String?
)

/**
 * The configuration for the Google OSV vulnerability provider.
 */
data class OsvConfiguration(
    /**
     * The base URL of the OSV REST API. If undefined, default is the production endpoint of the official OSV.dev API.
     */
    val serverUrl: String?
)

/**
 * The configuration for VulnerableCode as security vulnerability provider.
 *
 * TODO: Define options for authentication.
 */
data class VulnerableCodeConfiguration(
    /**
     * The base URL of the VulnerableCode REST API.
     */
    val serverUrl: String
)

/**
 * The configuration for the GitHub Defects advisor.
 */
data class GitHubDefectsConfiguration(
    /**
     * The access token to authenticate against the GitHub GraphQL endpoint.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    val token: String?,

    /**
     * The URL of the GraphQL endpoint to be accessed by the service. If undefined, default is the endpoint of the
     * official GitHub GraphQL API.
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    val endpointUrl: String? = null,

    /**
     * A list with labels to be used for filtering GitHub issues. With GitHub's data model for issues, it is not
     * possible to determine whether a specific issue is actually a defect or something else, e.g. a feature request.
     * Via this property, it is possible to limit the issues retrieved by the GitHub defects advisor by filtering for
     * specific label values. The filtering works as follows:
     *  - Each string in this list refers to a label to be matched. The strings are processed in order.
     *  - If for an issue a label with the name of the current string is found, the issue is included into the result
     *    set.
     *  - If the current string starts with one of the characters '-' or '!', it defines an exclusion. So, if an issue
     *    contains a label named like the current string with the first character removed, this issue is not added to
     *    the result set, and filtering stops here. (The ordered processing resolves conflicting filters, as the first
     *    match wins.)
     *  - Label name matches are case-insensitive.
     *  - Wildcards are supported; a "*" matches arbitrary characters.
     *  - If the end of the list is reached and no match was found, the issue is not added to the result set. In order
     *    to have all issues included for which no specific exclusion was found, a wildcard match "*" can be added at
     *    the end.
     * Per default, some of GitHub's default labels are excluded that typically indicate that an issue is not a defect
     * (see https://docs.github.com/en/issues/using-labels-and-milestones-to-track-work/managing-labels#about-default-labels)
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    val labelFilter: List<String> = listOf("!duplicate", "!enhancement", "!invalid", "!question", "*"),

    /**
     * The maximum number of defects that are retrieved from a single repository. If a repository contains more
     * issues, only this number is returned (the newest ones). Popular libraries hosted on GitHub can really have a
     * large number of issues; therefore, it makes sense to restrict the result set produced by this advisor.
     */
    val maxNumberOfIssuesPerRepository: Int? = null,

    /**
     * Determines the number of requests to the GitHub GraphQL API that are executed in parallel. Rather than querying
     * each repository one after the other, fetching the data of multiple repositories concurrently can reduce the
     * execution times for this advisor implementation. If unspecified, a default value for parallel executions as
     * defined in the _GitHubDefects_ class is used.
     */
    val parallelRequests: Int? = null
)
