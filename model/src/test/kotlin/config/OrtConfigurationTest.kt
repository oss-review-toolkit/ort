/*
 * Copyright (C) 2019 HERE Europe B.V.
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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.utils.ORT_NAME
import org.ossreviewtoolkit.utils.test.containExactly as containExactlyEntries
import org.ossreviewtoolkit.utils.test.shouldNotBeNull

class OrtConfigurationTest : WordSpec({
    "OrtConfiguration" should {
        "be deserializable from HOCON" {
            val refConfig = File("src/test/assets/reference.conf")
            val ortConfig = OrtConfiguration.load(configFile = refConfig)

            ortConfig.scanner shouldNotBeNull {
                archive shouldNotBeNull {
                    patterns should containExactly("LICENSE*", "COPYING*")
                    storage.httpFileStorage.shouldBeNull()
                    storage.localFileStorage shouldNotBeNull {
                        directory shouldBe File("~/.ort/scanner/archive")
                    }
                }

                fileBasedStorage shouldNotBeNull {
                    backend.httpFileStorage shouldNotBeNull {
                        url shouldBe "https://your-http-server"
                        headers should containExactlyEntries("key1" to "value1", "key2" to "value2")
                    }

                    backend.localFileStorage shouldNotBeNull {
                        directory shouldBe File("~/.ort/scanner/results")
                    }
                }

                postgresStorage shouldNotBeNull {
                    url shouldBe "jdbc:postgresql://your-postgresql-server:5444/your-database"
                    schema shouldBe "schema"
                    username shouldBe "username"
                    password shouldBe "password"
                    sslmode shouldBe "required"
                    sslcert shouldBe "/defaultdir/postgresql.crt"
                    sslkey shouldBe "/defaultdir/postgresql.pk8"
                    sslrootcert shouldBe "/defaultdir/root.crt"
                }

                options.shouldNotBeNull()
            }
        }

        "correctly prioritize the sources" {
            val configFile = createTestConfig(
                """
                ort {
                  scanner {
                    postgresStorage {
                      url = "postgresql://your-postgresql-server:5444/your-database"
                      schema = schema
                      username = username
                      password = password
                    }
                  }
                }
                """.trimIndent()
            )

            val config = OrtConfiguration.load(
                args = mapOf(
                    "ort.scanner.postgresStorage.schema" to "argsSchema",
                    "other.property" to "someValue"
                ),
                configFile = configFile
            )

            config.scanner shouldNotBeNull {
                postgresStorage shouldNotBeNull {
                    username shouldBe "username"
                    schema shouldBe "argsSchema"
                }
            }
        }

        "ignore a non-existing configuration file" {
            val args = mapOf("foo" to "bar")
            val file = File("nonExistingConfig.conf")

            val config = OrtConfiguration.load(configFile = file, args = args)

            config.scanner.shouldBeNull()
        }
    }
})

/**
 * Create a test configuration with the [data] specified.
 */
private fun createTestConfig(data: String): File =
    createTempFile(ORT_NAME, ".conf").apply {
        writeText(data)
        deleteOnExit()
    }
