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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.extensions.system.withEnvironment
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.types.shouldBeInstanceOf

import java.io.File
import java.lang.IllegalArgumentException

import kotlin.io.path.createTempFile

import org.ossreviewtoolkit.utils.ORT_NAME
import org.ossreviewtoolkit.utils.test.containExactly as containExactlyEntries
import org.ossreviewtoolkit.utils.test.shouldNotBeNull

class OrtConfigurationTest : WordSpec({
    "OrtConfiguration" should {
        "be deserializable from HOCON" {
            val refConfig = File("src/test/assets/reference.conf")
            val ortConfig = OrtConfiguration.load(configFile = refConfig)

            ortConfig.analyzer shouldNotBeNull {
                ignoreToolVersions shouldBe true
                allowDynamicVersions shouldBe true

                sw360Configuration shouldNotBeNull {
                    restUrl shouldBe "https://your-sw360-rest-url"
                    authUrl shouldBe "https://your-authentication-url"
                    username shouldBe "username"
                    password shouldBe "password"
                    clientId shouldBe "clientId"
                    clientPassword shouldBe "clientPassword"
                    token shouldBe "token"
                }
            }

            ortConfig.scanner shouldNotBeNull {
                archive shouldNotBeNull {
                    storage.httpFileStorage should beNull()
                    storage.localFileStorage shouldNotBeNull {
                        directory shouldBe File("~/.ort/scanner/archive")
                    }
                }

                storages shouldNotBeNull {
                    keys shouldContainExactlyInAnyOrder setOf("local", "http", "clearlyDefined", "postgres")
                    val httpStorage = this["http"]
                    httpStorage.shouldBeInstanceOf<FileBasedStorageConfiguration>()
                    httpStorage.backend.httpFileStorage shouldNotBeNull {
                        url shouldBe "https://your-http-server"
                        headers should containExactlyEntries("key1" to "value1", "key2" to "value2")
                    }

                    val localStorage = this["local"]
                    localStorage.shouldBeInstanceOf<FileBasedStorageConfiguration>()
                    localStorage.backend.localFileStorage shouldNotBeNull {
                        directory shouldBe File("~/.ort/scanner/results")
                    }

                    val postgresStorage = this["postgres"]
                    postgresStorage.shouldBeInstanceOf<PostgresStorageConfiguration>()
                    postgresStorage.url shouldBe "jdbc:postgresql://your-postgresql-server:5444/your-database"
                    postgresStorage.schema shouldBe "schema"
                    postgresStorage.username shouldBe "username"
                    postgresStorage.password shouldBe "password"
                    postgresStorage.sslmode shouldBe "required"
                    postgresStorage.sslcert shouldBe "/defaultdir/postgresql.crt"
                    postgresStorage.sslkey shouldBe "/defaultdir/postgresql.pk8"
                    postgresStorage.sslrootcert shouldBe "/defaultdir/root.crt"

                    val cdStorage = this["clearlyDefined"]
                    cdStorage.shouldBeInstanceOf<ClearlyDefinedStorageConfiguration>()
                    cdStorage.serverUrl shouldBe "https://api.clearlydefined.io"
                }

                options shouldNot beNull()
                storageReaders shouldContainExactly listOf("local", "postgres", "http", "clearlyDefined")
                storageWriters shouldContainExactly listOf("postgres")

                ignorePatterns shouldContainExactly listOf("**/META-INF/DEPENDENCIES")
            }

            ortConfig.licenseFilePatterns shouldNotBeNull {
                licenseFilenames shouldContainExactly listOf("license*")
                patentFilenames shouldContainExactly listOf("patents")
                rootLicenseFilenames shouldContainExactly listOf("readme*")
            }
        }

        "correctly prioritize the sources" {
            val configFile = createTestConfig(
                """
                ort {
                  scanner {
                    storages {
                      postgresStorage {
                        url = "postgresql://your-postgresql-server:5444/your-database"
                        schema = schema
                        username = username
                        password = password
                      }
                    }
                  }
                }
                """.trimIndent()
            )

            val env = mapOf("ort.scanner.storages.postgresStorage.password" to "envPassword")

            withEnvironment(env) {
                val config = OrtConfiguration.load(
                    args = mapOf(
                        "ort.scanner.storages.postgresStorage.schema" to "argsSchema",
                        "ort.scanner.storages.postgresStorage.password" to "argsPassword",
                        "other.property" to "someValue"
                    ),
                    configFile = configFile
                )

                config.scanner?.storages shouldNotBeNull {
                    val postgresStorage = this["postgresStorage"]
                    postgresStorage.shouldBeInstanceOf<PostgresStorageConfiguration>()
                    postgresStorage.username shouldBe "username"
                    postgresStorage.schema shouldBe "argsSchema"
                    postgresStorage.password shouldBe "envPassword"
                }
            }
        }

        "fail for an invalid configuration" {
            val configFile = createTestConfig(
                """
                ort {
                  scanner {
                    storages {
                      foo = baz
                    }
                  }
                }
                """.trimIndent()
            )

            shouldThrow<IllegalArgumentException> {
                OrtConfiguration.load(configFile = configFile)
            }
        }

        "fail for invalid properties in the map with arguments" {
            val file = File("anotherNonExistingConfig.conf")
            val args = mapOf("ort.scanner.storages.new" to "test")

            shouldThrow<IllegalArgumentException> {
                OrtConfiguration.load(configFile = file, args = args)
            }
        }

        "ignore a non-existing configuration file" {
            val args = mapOf("foo" to "bar")
            val file = File("nonExistingConfig.conf")

            val config = OrtConfiguration.load(configFile = file, args = args)

            config.scanner should beNull()
        }

        "support references to environment variables" {
            val configFile = createTestConfig(
                """
                ort {
                  scanner {
                    storages {
                      postgresStorage {
                        url = "postgresql://your-postgresql-server:5444/your-database"
                        schema = schema
                        username = ${'$'}{POSTGRES_USERNAME}
                        password = ${'$'}{POSTGRES_PASSWORD}
                      }
                    }
                  }
                }
                """.trimIndent()
            )
            val user = "scott"
            val password = "tiger"
            val env = mapOf("POSTGRES_USERNAME" to user, "POSTGRES_PASSWORD" to password)

            withEnvironment(env) {
                val config = OrtConfiguration.load(configFile = configFile)

                config.scanner?.storages shouldNotBeNull {
                    val postgresStorage = this["postgresStorage"]
                    postgresStorage.shouldBeInstanceOf<PostgresStorageConfiguration>()
                    postgresStorage.username shouldBe user
                    postgresStorage.password shouldBe password
                }
            }
        }

        "support environmental variables" {
            val user = "user"
            val password = "password"
            val url = "url"
            val schema = "schema"
            val env = mapOf(
                "ort.scanner.storages.postgresStorage.username" to user,
                "ort.scanner.storages.postgresStorage.url" to url,
                "ort__scanner__storages__postgresStorage__schema" to schema,
                "ort__scanner__storages__postgresStorage__password" to password
            )

            withEnvironment(env) {
                val config = OrtConfiguration.load(configFile = File("dummyPath"))

                config.scanner?.storages shouldNotBeNull {
                    val postgresStorage = this["postgresStorage"]
                    postgresStorage.shouldBeInstanceOf<PostgresStorageConfiguration>()
                    postgresStorage.username shouldBe user
                    postgresStorage.password shouldBe password
                    postgresStorage.url shouldBe url
                    postgresStorage.schema shouldBe schema
                }
            }
        }
    }
})

/**
 * Create a test configuration with the [data] specified.
 */
private fun createTestConfig(data: String): File =
    createTempFile(ORT_NAME, ".conf").toFile().apply {
        writeText(data)
        deleteOnExit()
    }
