/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties("parallel_transactions")
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
    @JsonInclude(Include.NON_NULL)
    val sslcert: String? = null,

    /**
     * The full path of the key file.
     * See: https://jdbc.postgresql.org/documentation/head/connect.html
     */
    @JsonInclude(Include.NON_NULL)
    val sslkey: String? = null,

    /**
     * The full path of the root certificate file.
     * See: https://jdbc.postgresql.org/documentation/head/connect.html
     */
    @JsonInclude(Include.NON_NULL)
    val sslrootcert: String? = null,

    /**
     * The maximum number of milliseconds to wait for connections from the pool. For details see the
     * [Hikari documentation](https://github.com/brettwooldridge/HikariCP#frequently-used).
     */
    @JsonInclude(Include.NON_NULL)
    val connectionTimeout: Long? = null,

    /**
     * The maximum number of milliseconds a connection is allowed to sit idle in the pool. This requires that
     * [minimumIdle] is set to a value lower than [maximumPoolSize]. For details see the
     * [Hikari documentation](https://github.com/brettwooldridge/HikariCP#frequently-used).
     */
    @JsonInclude(Include.NON_NULL)
    val idleTimeout: Long? = null,

    /**
     * The frequency in milliseconds that the pool will attempt to keep an idle connection alive. Must be set to a value
     * lower than [maxLifetime]. For details see the
     * [Hikari documentation](https://github.com/brettwooldridge/HikariCP#frequently-used).
     */
    @JsonInclude(Include.NON_NULL)
    val keepaliveTime: Long? = null,

    /**
     * The maximum lifetime of a connection in milliseconds. For details see the
     * [Hikari documentation](https://github.com/brettwooldridge/HikariCP#frequently-used).
     */
    @JsonInclude(Include.NON_NULL)
    val maxLifetime: Long? = null,

    /**
     * The maximum size of the connection pool. For details see the
     * [Hikari documentation](https://github.com/brettwooldridge/HikariCP#frequently-used).
     */
    @JsonInclude(Include.NON_NULL)
    val maximumPoolSize: Int? = null,

    /**
     * The minimum number of idle connections that the pool tries to maintain. For details see the
     * [Hikari documentation](https://github.com/brettwooldridge/HikariCP#frequently-used).
     */
    @JsonInclude(Include.NON_NULL)
    val minimumIdle: Int? = null
)
