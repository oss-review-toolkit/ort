/*
 * Copyright (C) 2021 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.utils.test

import com.zaxxer.hikari.HikariConfig

import io.kotest.core.extensions.MountableExtension
import io.kotest.core.listeners.TestListener
import io.kotest.core.test.TestCase
import io.kotest.extensions.testcontainers.JdbcDatabaseContainerSpecExtension

import javax.sql.DataSource

import org.testcontainers.containers.PostgreSQLContainer

/**
 * A [TestListener] that starts a [PostgreSQLContainer] before running the spec and closes it after the spec has
 * finished. The database is cleared before each test.
 */
class PostgresListener :
    TestListener,
    MountableExtension<HikariConfig, DataSource> by JdbcDatabaseContainerSpecExtension(
        PostgreSQLContainer<Nothing>("postgres:18-alpine")
    ) {
    val dataSource: Lazy<DataSource> get() = lazyOf(
        mount {
            maximumPoolSize = 3
            minimumIdle = 1
        }
    )

    override suspend fun beforeEach(testCase: TestCase) {
        dataSource.value.connection.use { c ->
            c.createStatement().use { s ->
                s.execute("DROP SCHEMA public CASCADE")
                s.execute("CREATE SCHEMA public")
            }
        }
    }
}
