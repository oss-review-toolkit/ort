/*
 * Copyright (C) 2022 Bosch.IO GmbH
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

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS)
@JsonSubTypes(
    JsonSubTypes.Type(PostgresConnection::class),
    JsonSubTypes.Type(Sw360Connection::class)
)
sealed interface StorageConnection

data class PostgresConnection(
    /**
     * The database URL in JDBC format.
     */
    val url: String,

    /**
     * The name of the database to use.
     */
    val schema: String = "public",

    /**
     * The username to use for authentication.
     */
    val username: String,

    /**
     * The password to use for authentication.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    val password: String = "",

    /**
     * The SSL mode to use, one of "disable", "allow", "prefer", "require", "verify-ca" or "verify-full".
     * See: https://jdbc.postgresql.org/documentation/ssl/#configuring-the-client
     */
    val sslmode: String = "verify-full",

    /**
     * The full path of the certificate file.
     * See: https://jdbc.postgresql.org/documentation/head/connect.html
     */
    val sslcert: String? = null,

    /**
     * The full path of the key file.
     * See: https://jdbc.postgresql.org/documentation/head/connect.html
     */
    val sslkey: String? = null,

    /**
     * The full path of the root certificate file.
     * See: https://jdbc.postgresql.org/documentation/head/connect.html
     */
    val sslrootcert: String? = null,

    /**
     * The number of parallel transactions to use for the storage dispatcher.
     */
    val parallelTransactions: Int = 5

    /**
     * TODO: Make additional parameters configurable, see:
     *       https://jdbc.postgresql.org/documentation/head/connect.html
     */
) : StorageConnection

data class Sw360Connection(
    /**
     * The REST API URL of SW360.
     */
    val restUrl: String,

    /**
     * The authentication URL of your SW360 instance.
     */
    val authUrl: String,

    /**
     * The username for the requests to SW360.
     */
    val username: String,

    /**
     * The password of the SW360 user.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    val password: String = "",

    /**
     * The client ID of the SW360 instance for the two-step authentication.
     */
    val clientId: String,

    /**
     * The password of the client ID.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    val clientPassword: String = "",

    /**
     * Optional access token that can be used instead of the [authUrl], [username], [password], [clientId] and
     * [clientPassword] if the token is already known.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    val token: String = ""
) : StorageConnection
