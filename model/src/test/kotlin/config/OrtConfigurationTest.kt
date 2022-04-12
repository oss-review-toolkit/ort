/*
 * Copyright (C) 2019 HERE Europe B.V.
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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.TestConfiguration
import io.kotest.core.spec.style.WordSpec
import io.kotest.extensions.system.withEnvironment
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.containExactly as containExactlyEntries
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.types.shouldBeInstanceOf

import java.io.File
import java.lang.IllegalArgumentException

import org.ossreviewtoolkit.model.SourceCodeOrigin
import org.ossreviewtoolkit.utils.test.createTestTempFile
import org.ossreviewtoolkit.utils.test.shouldNotBeNull

class OrtConfigurationTest : WordSpec({
    "OrtConfiguration" should {
        "be deserializable from HOCON" {
            val refConfig = File("src/main/resources/reference.conf")
            val ortConfig = OrtConfiguration.load(file = refConfig)

            with(ortConfig.analyzer) {
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

            with(ortConfig.advisor) {
                nexusIq shouldNotBeNull {
                    serverUrl shouldBe "https://rest-api-url-of-your-nexus-iq-server"
                    username shouldBe "username"
                    password shouldBe "password"
                }

                vulnerableCode shouldNotBeNull {
                    serverUrl shouldBe "http://localhost:8000"
                }

                options shouldNotBeNull {
                    this["CustomAdvisor"]?.get("apiKey") shouldBe "<some_api_key>"
                }
            }

            ortConfig.downloader shouldNotBeNull {
                includedLicenseCategories should containExactly("category-a", "category-b")
                sourceCodeOrigins should containExactly(SourceCodeOrigin.VCS, SourceCodeOrigin.ARTIFACT)
            }

            with(ortConfig.scanner) {
                archive shouldNotBeNull {
                    fileStorage shouldNotBeNull {
                        httpFileStorage should beNull()
                        localFileStorage shouldNotBeNull {
                            directory shouldBe File("~/.ort/scanner/archive")
                        }
                    }

                    postgresStorage shouldNotBeNull {
                        url shouldBe "jdbc:postgresql://your-postgresql-server:5444/your-database"
                        schema shouldBe "public"
                        username shouldBe "username"
                        password shouldBe "password"
                        sslmode shouldBe "required"
                        sslcert shouldBe "/defaultdir/postgresql.crt"
                        sslkey shouldBe "/defaultdir/postgresql.pk8"
                        sslrootcert shouldBe "/defaultdir/root.crt"
                    }
                }

                storages shouldNotBeNull {
                    keys shouldContainExactlyInAnyOrder setOf(
                        "local", "http", "clearlyDefined", "postgres", "sw360Configuration"
                    )
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
                    postgresStorage.schema shouldBe "public"
                    postgresStorage.username shouldBe "username"
                    postgresStorage.password shouldBe "password"
                    postgresStorage.sslmode shouldBe "required"
                    postgresStorage.sslcert shouldBe "/defaultdir/postgresql.crt"
                    postgresStorage.sslkey shouldBe "/defaultdir/postgresql.pk8"
                    postgresStorage.sslrootcert shouldBe "/defaultdir/root.crt"

                    val cdStorage = this["clearlyDefined"]
                    cdStorage.shouldBeInstanceOf<ClearlyDefinedStorageConfiguration>()
                    cdStorage.serverUrl shouldBe "https://api.clearlydefined.io"

                    val sw360Storage = this["sw360Configuration"]
                    sw360Storage.shouldBeInstanceOf<Sw360StorageConfiguration>()
                    sw360Storage.restUrl shouldBe "https://your-sw360-rest-url"
                    sw360Storage.authUrl shouldBe "https://your-authentication-url"
                    sw360Storage.username shouldBe "username"
                    sw360Storage.password shouldBe "password"
                    sw360Storage.clientId shouldBe "clientId"
                    sw360Storage.clientPassword shouldBe "clientPassword"
                    sw360Storage.token shouldBe "token"
                }

                options shouldNot beNull()
                storageReaders shouldContainExactly listOf("local", "postgres", "http", "clearlyDefined")
                storageWriters shouldContainExactly listOf("postgres")

                ignorePatterns shouldContainExactly listOf("**/META-INF/DEPENDENCIES")

                provenanceStorage shouldNotBeNull {
                    fileStorage shouldNotBeNull {
                        httpFileStorage should beNull()
                        localFileStorage shouldNotBeNull {
                            directory shouldBe File("~/.ort/scanner/provenance")
                        }
                    }

                    postgresStorage shouldNotBeNull {
                        url shouldBe "jdbc:postgresql://your-postgresql-server:5444/your-database"
                        schema shouldBe "public"
                        username shouldBe "username"
                        password shouldBe "password"
                        sslmode shouldBe "required"
                        sslcert shouldBe "/defaultdir/postgresql.crt"
                        sslkey shouldBe "/defaultdir/postgresql.pk8"
                        sslrootcert shouldBe "/defaultdir/root.crt"
                    }
                }
            }

            with(ortConfig.notifier) {
                mail shouldNotBeNull {
                    hostName shouldBe "localhost"
                    port shouldBe 465
                    username shouldBe "user"
                    password shouldBe "secret"
                    useSsl shouldBe true
                    fromAddress shouldBe "no-reply@oss-review-toolkit.org"
                }

                jira shouldNotBeNull {
                    host shouldBe "localhost"
                    username shouldBe "user"
                    password shouldBe "secret"
                }
            }

            with(ortConfig.licenseFilePatterns) {
                licenseFilenames shouldContainExactly listOf("license*")
                patentFilenames shouldContainExactly listOf("patents")
                rootLicenseFilenames shouldContainExactly listOf("readme*")
            }

            ortConfig.enableRepositoryPackageCurations shouldBe true
            ortConfig.enableRepositoryPackageConfigurations shouldBe true
        }

        "correctly prioritize the sources" {
            val configFile = createTestConfig(
                """
                ort {
                  scanner {
                    storages {
                      postgresStorage {
                        url = "postgresql://your-postgresql-server:5444/your-database"
                        schema = "public"
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
                    file = configFile
                )

                config.scanner.storages shouldNotBeNull {
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
                OrtConfiguration.load(file = configFile)
            }
        }

        "fail for invalid properties in the map with arguments" {
            val file = File("anotherNonExistingConfig.conf")
            val args = mapOf("ort.scanner.storages.new" to "test")

            shouldThrow<IllegalArgumentException> {
                OrtConfiguration.load(file = file, args = args)
            }
        }

        "ignore a non-existing configuration file" {
            val args = mapOf("foo" to "bar")
            val file = File("nonExistingConfig.conf")

            val config = OrtConfiguration.load(file = file, args = args)

            config.scanner shouldBe ScannerConfiguration()
        }

        "support references to environment variables" {
            val configFile = createTestConfig(
                """
                ort {
                  scanner {
                    storages {
                      postgresStorage {
                        url = "postgresql://your-postgresql-server:5444/your-database"
                        schema = "public"
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
                val config = OrtConfiguration.load(file = configFile)

                config.scanner.storages shouldNotBeNull {
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
            val schema = "public"
            val env = mapOf(
                "ort.scanner.storages.postgresStorage.username" to user,
                "ort.scanner.storages.postgresStorage.url" to url,
                "ort__scanner__storages__postgresStorage__schema" to schema,
                "ort__scanner__storages__postgresStorage__password" to password
            )

            withEnvironment(env) {
                val config = OrtConfiguration.load(file = File("dummyPath"))

                config.scanner.storages shouldNotBeNull {
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
private fun TestConfiguration.createTestConfig(data: String): File =
    createTestTempFile(suffix = ".conf").apply {
        writeText(data)
    }
