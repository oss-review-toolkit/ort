/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package com.here.ort.scanner.storages

import com.opentable.db.postgres.embedded.EmbeddedPostgres

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

class PostgresStorageTest : StringSpec() {
    init {
        "Embedded postgres works" {
            EmbeddedPostgres.start().use { postgres ->
                val connection = postgres.postgresDatabase.connection

                val statement = connection.createStatement()
                val resultSet = statement.executeQuery("SELECT 1")

                resultSet.next() shouldBe true
                resultSet.getInt(1) shouldBe 1
                resultSet.next() shouldBe false
            }
        }
    }
}
