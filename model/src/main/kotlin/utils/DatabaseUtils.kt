/*
 * Copyright (C) 2021 HERE Europe B.V.
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

package org.ossreviewtoolkit.model.utils

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

import org.ossreviewtoolkit.model.config.PostgresStorageConfiguration
import org.ossreviewtoolkit.utils.ORT_FULL_NAME

object DatabaseUtils {
    /**
     * Return a [HikariDataSource] for the given [PostgresStorageConfiguration].
     */
    fun createHikariDataSource(
        config: PostgresStorageConfiguration,
        applicationNameSuffix: String = "",
        maxPoolSize: Int = 5
    ): HikariDataSource {
        require(config.url.isNotBlank()) {
            "URL for PostgreSQL storage is missing."
        }

        require(config.schema.isNotBlank()) {
            "Schema for PostgreSQL storage is missing."
        }

        require(config.username.isNotBlank()) {
            "Username for PostgreSQL storage is missing."
        }

        require(config.password.isNotBlank()) {
            "Password for PostgreSQL storage is missing."
        }

        val dataSourceConfig = HikariConfig().apply {
            jdbcUrl = config.url
            username = config.username
            password = config.password
            schema = config.schema
            maximumPoolSize = maxPoolSize

            val suffix = " - $applicationNameSuffix".takeIf { applicationNameSuffix.isNotEmpty() }.orEmpty()
            addDataSourceProperty("ApplicationName", "$ORT_FULL_NAME$suffix")

            // Configure SSL, see: https://jdbc.postgresql.org/documentation/head/connect.html
            // Note that the "ssl" property is only a fallback in case "sslmode" is not used. Since we always set
            // "sslmode", "ssl" is not required.
            addDataSourceProperty("sslmode", config.sslmode)
            addDataSourcePropertyIfDefined("sslcert", config.sslcert)
            addDataSourcePropertyIfDefined("sslkey", config.sslkey)
            addDataSourcePropertyIfDefined("sslrootcert", config.sslrootcert)
        }

        return HikariDataSource(dataSourceConfig)
    }

    /**
     * Add a property with the given [key] and [value] to the [HikariConfig]. If the [value] is *null*, this
     * function has no effect. (It is not specified how the database driver deals with *null* values in its
     * properties; so it is safer to avoid them.)
     */
    private fun HikariConfig.addDataSourcePropertyIfDefined(key: String, value: String?) {
        value?.let { addDataSourceProperty(key, it) }
    }
}
