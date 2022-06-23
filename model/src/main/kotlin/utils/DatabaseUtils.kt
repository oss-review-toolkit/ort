/*
 * Copyright (C) 2021 HERE Europe B.V.
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

package org.ossreviewtoolkit.model.utils

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

import java.util.concurrent.ConcurrentHashMap

import javax.sql.DataSource

import kotlinx.coroutines.Deferred

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.suspendedTransactionAsync
import org.jetbrains.exposed.sql.transactions.transaction

import org.ossreviewtoolkit.model.config.PostgresConnection
import org.ossreviewtoolkit.utils.ort.ORT_FULL_NAME
import org.ossreviewtoolkit.utils.ort.log

object DatabaseUtils {
    /**
     * This map holds the [HikariDataSource] based on the [PostgresConnection].
     */
    private val dataSources = ConcurrentHashMap<PostgresConnection, Lazy<DataSource>>()

    /**
     * Return a [HikariDataSource] for the given [PostgresConnection].
     */
    fun createHikariDataSource(
        config: PostgresConnection,
        applicationNameSuffix: String = "",
        maxPoolSize: Int = 5
    ): Lazy<DataSource> {
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

        return dataSources.getOrPut(config) {
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

            lazy { HikariDataSource(dataSourceConfig) }
        }
    }

    /**
     * Logs a warning in case the actual database encoding does not equal the [expectedEncoding].
     */
    fun Transaction.checkDatabaseEncoding(expectedEncoding: String = "UTF8") =
        execShow("SHOW client_encoding") { resultSet ->
            if (resultSet.next()) {
                val clientEncoding = resultSet.getString(1)
                if (clientEncoding != expectedEncoding) {
                    DatabaseUtils.log.warn {
                        "The database's client_encoding is '$clientEncoding' but should be '$expectedEncoding'."
                    }
                }
            }
        }

    /**
     * Return true if and only if a table named [tableName] exists.
     */
    fun Transaction.tableExists(tableName: String): Boolean =
        tableName in db.dialect.allTablesNames().map { it.substringAfterLast(".") }

    /**
     * Start a new transaction to execute the given [statement] on this [Database].
     */
    fun <T> Database.transaction(statement: Transaction.() -> T): T =
        transaction(this, statement)

    /**
     * Start a new asynchronous transaction to execute the given [statement] on this [Database].
     */
    suspend fun <T> Database.transactionAsync(statement: suspend Transaction.() -> T): Deferred<T> =
        suspendedTransactionAsync(db = this, statement = statement)

    /**
     * Add a property with the given [key] and [value] to the [HikariConfig]. If the [value] is *null*, this
     * function has no effect. (It is not specified how the database driver deals with *null* values in its
     * properties; so it is safer to avoid them.)
     */
    private fun HikariConfig.addDataSourcePropertyIfDefined(key: String, value: String?) {
        value?.let { addDataSourceProperty(key, it) }
    }
}
