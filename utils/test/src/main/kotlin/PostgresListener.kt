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

package org.ossreviewtoolkit.utils.test

import com.opentable.db.postgres.embedded.EmbeddedPostgres

import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import io.kotest.core.test.TestCase

import java.time.Duration

import javax.sql.DataSource

/**
 * A [TestListener] that starts an [EmbeddedPostgres] instance before running the spec and closes it after the spec has
 * finished. The database is cleared before each test.
 */
class PostgresListener(private val startupWait: Duration = Duration.ofSeconds(20)) : TestListener {
    private lateinit var postgres: EmbeddedPostgres

    val dataSource: Lazy<DataSource> get() = lazyOf(postgres.postgresDatabase)

    override suspend fun beforeSpec(spec: Spec) {
        postgres = EmbeddedPostgres.builder().setPGStartupWait(startupWait).start()
    }

    override suspend fun beforeEach(testCase: TestCase) {
        postgres.postgresDatabase.connection.use { c ->
            val s = c.createStatement()
            s.execute("DROP SCHEMA public CASCADE")
            s.execute("CREATE SCHEMA public")
        }
    }

    override suspend fun afterSpec(spec: Spec) {
        postgres.close()
    }
}
