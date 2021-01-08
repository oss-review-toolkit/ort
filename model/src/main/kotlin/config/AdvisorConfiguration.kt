/*
 * Copyright (C) 2020 Bosch.IO GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * The base configuration model of the advisor.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS)
@JsonSubTypes(
    JsonSubTypes.Type(BasicAuthConfiguration::class)
)
sealed class AdvisorConfiguration

/**
 * The configuration for a security vulnerability provider using basic auth as authentication method.
 */
data class BasicAuthConfiguration(
    /**
     * The URL to use for REST API requests against the server.
     */
    val serverUrl: String,

    /**
     * A URL to use as a base for browsing vulnerability details. Defaults to the server URL.
     */
    val browseUrl: String = serverUrl,

    /**
     * Username of the provider. Used without authentication if no password or username is given.
     */
    val username: String?,

    /**
     * Password of the provider. Used without authentication if no password or username is given.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    val password: String?
) : AdvisorConfiguration()
