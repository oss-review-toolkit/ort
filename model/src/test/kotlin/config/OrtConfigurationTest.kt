/*
 * Copyright (C) 2019 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import com.sksamuel.hoplite.ConfigException

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.TestConfiguration
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempfile
import io.kotest.extensions.system.withEnvironment
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.maps.beEmpty
import io.kotest.matchers.maps.containExactly as containExactlyEntries
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

import java.io.File

import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.SourceCodeOrigin
import org.ossreviewtoolkit.plugins.api.PluginConfig
import org.ossreviewtoolkit.utils.common.EnvironmentVariableFilter
import org.ossreviewtoolkit.utils.ort.ORT_REFERENCE_CONFIG_FILENAME

class OrtConfigurationTest : WordSpec({
    "OrtConfiguration" should {
        "be deserializable from YAML" {
            val refConfig = File("src/main/resources/$ORT_REFERENCE_CONFIG_FILENAME")
            val ortConfig = OrtConfiguration.load(file = refConfig)

            ortConfig.allowedProcessEnvironmentVariableNames should containExactlyInAnyOrder("PASSPORT", "USER_HOME")
            ortConfig.deniedProcessEnvironmentVariablesSubstrings should containExactlyInAnyOrder(
                "PASS", "SECRET", "TOKEN", "USER"
            )

            ortConfig.enableRepositoryPackageConfigurations shouldBe true
            ortConfig.enableRepositoryPackageCurations shouldBe true

            ortConfig.forceOverwrite shouldBe true

            ortConfig.licenseFactProviders should containExactlyEntries(
                "spdx" to PluginConfig.EMPTY,
                "scancode" to PluginConfig(mapOf("scanCodeLicenseTextDir" to "/path/to/scancode/license/text/dir")),
                "defaultdir" to PluginConfig.EMPTY,
                "dir" to PluginConfig(mapOf("licenseTextDir" to "/path/to/license/text/dir"))
            )

            with(ortConfig.licenseFilePatterns) {
                licenseFilenames should containExactly("license*")
                patentFilenames should containExactly("patents")
                otherLicenseFilenames should containExactly("readme*")
            }

            ortConfig.packageCurationProviders should containExactly(
                ProviderPluginConfiguration(type = "DefaultFile"),
                ProviderPluginConfiguration(type = "DefaultDir"),
                ProviderPluginConfiguration(
                    type = "File",
                    id = "SomeCurationsFile",
                    options = mapOf("path" to "/some-path/curations.yml", "mustExist" to "true")
                ),
                ProviderPluginConfiguration(
                    type = "File",
                    id = "SomeCurationsDir",
                    options = mapOf("path" to "/some-path/curations-dir", "mustExist" to "false")
                ),
                ProviderPluginConfiguration(type = "OrtConfig", enabled = true),
                ProviderPluginConfiguration(
                    type = "ClearlyDefined",
                    options = mapOf("serverUrl" to "https://api.clearlydefined.io", "minTotalLicenseScore" to "80")
                ),
                ProviderPluginConfiguration(
                    type = "SW360",
                    options = mapOf(
                        "restUrl" to "https://your-sw360-rest-url",
                        "authUrl" to "https://your-authentication-url"
                    ),
                    secrets = mapOf(
                        "username" to "username",
                        "password" to "password",
                        "clientId" to "clientId",
                        "clientPassword" to "clientPassword",
                        "token" to "token"
                    )
                )
            )

            ortConfig.severeIssueThreshold shouldBe Severity.ERROR
            ortConfig.severeRuleViolationThreshold shouldBe Severity.ERROR

            with(ortConfig.analyzer) {
                allowDynamicVersions shouldBe true
                skipExcluded shouldBe true

                enabledPackageManagers should containExactlyInAnyOrder("Gradle")
                disabledPackageManagers should containExactlyInAnyOrder("Maven", "NPM")

                packageManagers shouldNotBeNull {
                    get("Gradle") shouldNotBeNull {
                        mustRunAfter should containExactly("NPM")
                    }

                    get("Yarn2") shouldNotBeNull {
                        options shouldNotBeNull {
                            this should containExactlyEntries("disableRegistryCertificateVerification" to "false")
                        }
                    }

                    getPackageManagerConfiguration("gradle") shouldNotBeNull {
                        this shouldBe get("Gradle")
                    }
                }
            }

            with(ortConfig.advisor) {
                advisors shouldNotBeNull {
                    get("OssIndex") shouldNotBeNull {
                        options should containExactlyEntries(
                            "serverUrl" to "https://ossindex.sonatype.org/"
                        )

                        secrets should containExactlyEntries(
                            "username" to "username",
                            "password" to "password"
                        )
                    }

                    get("OSV") shouldNotBeNull {
                        options should containExactlyEntries(
                            "serverUrl" to "https://api.osv.dev"
                        )
                    }

                    get("VulnerableCode") shouldNotBeNull {
                        options should containExactlyEntries(
                            "serverUrl" to "http://localhost:8000",
                            "readTimeout" to "40"
                        )

                        secrets should containExactlyEntries(
                            "apiKey" to "0123456789012345678901234567890123456789"
                        )
                    }
                }
            }

            with(ortConfig.downloader) {
                allowMovingRevisions shouldBe true
                includedLicenseCategories should containExactly("category-a", "category-b")
                sourceCodeOrigins should containExactly(SourceCodeOrigin.VCS, SourceCodeOrigin.ARTIFACT)
            }

            with(ortConfig.scanner) {
                skipConcluded shouldBe true

                archive shouldNotBeNull {
                    enabled shouldBe true

                    fileStorage shouldNotBeNull {
                        s3FileStorage should beNull()
                        httpFileStorage should beNull()
                        localFileStorage shouldNotBeNull {
                            directory shouldBe File("~/.ort/scanner/archive")
                        }
                    }

                    postgresStorage shouldNotBeNull {
                        with(connection) {
                            url shouldBe "jdbc:postgresql://your-postgresql-server:5444/your-database"
                            schema shouldBe "public"
                            username shouldBe "username"
                            password shouldBe "password"
                            sslmode shouldBe "require"
                            sslcert shouldBe "/defaultdir/postgresql.crt"
                            sslkey shouldBe "/defaultdir/postgresql.pk8"
                            sslrootcert shouldBe "/defaultdir/root.crt"
                        }

                        type shouldBe StorageType.PROVENANCE_BASED
                    }
                }

                detectedLicenseMapping should containExactlyEntries(
                    "BSD (Three Clause License)" to "BSD-3-clause",
                    "LicenseRef-scancode-generic-cla" to "NOASSERTION"
                )

                fileListStorage shouldNotBeNull {
                    fileStorage shouldNotBeNull {
                        s3FileStorage should beNull()
                        httpFileStorage should beNull()
                        localFileStorage shouldNotBeNull {
                            directory shouldBe File("~/.ort/scanner/file-lists")
                        }
                    }

                    postgresStorage shouldNotBeNull {
                        with(connection) {
                            url shouldBe "jdbc:postgresql://your-postgresql-server:5444/your-database"
                            schema shouldBe "public"
                            username shouldBe "username"
                            password shouldBe "password"
                            sslmode shouldBe "require"
                            sslcert shouldBe "/defaultdir/postgresql.crt"
                            sslkey shouldBe "/defaultdir/postgresql.pk8"
                            sslrootcert shouldBe "/defaultdir/root.crt"
                        }

                        type shouldBe StorageType.PROVENANCE_BASED
                    }
                }

                scanners shouldNotBeNull {
                    get("ScanCode") shouldNotBeNull {
                        options should containExactlyEntries(
                            "commandLine" to "--copyright,--license,--info,--strip-root,--timeout,300",
                            "commandLineNonConfig" to "--processes,4",
                            "preferFileLicense" to "false",
                            "minVersion" to "3.2.1-rc2",
                            "maxVersion" to "32.0.0"
                        )
                    }

                    get("FossId") shouldNotBeNull {
                        val urlMapping = "https://my-repo.example.org(?<repoPath>.*) -> " +
                            "ssh://my-mapped-repo.example.org\${repoPath}"

                        options should containExactlyEntries(
                            "serverUrl" to "https://fossid.example.com/instance/",
                            "projectName" to "My Project",
                            "namingScanPattern" to "#projectName_#repositoryName_#currentTimestamp_#deltaTag_#branch",
                            "waitForResult" to "false",
                            "keepFailedScans" to "false",
                            "deltaScans" to "true",
                            "deltaScanLimit" to "10",
                            "detectLicenseDeclarations" to "true",
                            "detectCopyrightStatements" to "true",
                            "timeout" to "60",
                            "urlMappings" to urlMapping,
                            "sensitivity" to "10",
                            "treatPendingIdentificationsAsError" to "false",
                            "deleteUploadedArchiveAfterScan" to "true"
                        )

                        secrets should containExactlyEntries(
                            "user" to "user",
                            "apiKey" to "XYZ"
                        )
                    }

                    get("SCANOSS") shouldNotBeNull {
                        options should containExactlyEntries("apiUrl" to "https://api.osskb.org/")
                        secrets should containExactlyEntries("apiKey" to "your API key")
                    }
                }

                storages shouldNotBeNull {
                    keys should containExactlyInAnyOrder(
                        "local", "http", "aws", "clearlyDefined", "postgres", "sw360Configuration"
                    )

                    val localStorage = this["local"]
                    localStorage.shouldBeInstanceOf<FileBasedStorageConfiguration>()
                    localStorage.backend.localFileStorage shouldNotBeNull {
                        directory shouldBe File("~/.ort/scanner/results")
                        compression shouldBe false
                    }

                    val httpStorage = this["http"]
                    httpStorage.shouldBeInstanceOf<FileBasedStorageConfiguration>()
                    httpStorage.backend.httpFileStorage shouldNotBeNull {
                        url shouldBe "https://your-http-server"
                        query shouldBe "?username=user&password=123"
                        headers should containExactlyEntries("key1" to "value1", "key2" to "value2")
                    }

                    val s3Storage = this["aws"]
                    s3Storage.shouldBeInstanceOf<FileBasedStorageConfiguration>()
                    s3Storage.backend.s3FileStorage shouldNotBeNull {
                        bucketName shouldBe "ort-scan-results"
                        awsRegion shouldBe "us-east-1"
                        accessKeyId shouldBe "accessKey"
                        secretAccessKey shouldBe "secret"
                        compression shouldBe false
                    }

                    val cdStorage = this["clearlyDefined"]
                    cdStorage.shouldBeInstanceOf<ClearlyDefinedStorageConfiguration>()
                    cdStorage.serverUrl shouldBe "https://api.clearlydefined.io"

                    val postgresStorage = this["postgres"]
                    postgresStorage.shouldBeInstanceOf<PostgresStorageConfiguration>()
                    with(postgresStorage.connection) {
                        url shouldBe "jdbc:postgresql://your-postgresql-server:5444/your-database"
                        schema shouldBe "public"
                        username shouldBe "username"
                        password shouldBe "password"
                        sslmode shouldBe "require"
                        sslcert shouldBe "/defaultdir/postgresql.crt"
                        sslkey shouldBe "/defaultdir/postgresql.pk8"
                        sslrootcert shouldBe "/defaultdir/root.crt"
                    }

                    postgresStorage.type shouldBe StorageType.PROVENANCE_BASED

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

                storageReaders should containExactly("local", "postgres", "http", "aws", "clearlyDefined")
                storageWriters should containExactly("postgres")

                ignorePatterns should containExactly("**/META-INF/DEPENDENCIES")

                provenanceStorage shouldNotBeNull {
                    fileStorage shouldNotBeNull {
                        httpFileStorage should beNull()
                        s3FileStorage should beNull()
                        localFileStorage shouldNotBeNull {
                            directory shouldBe File("~/.ort/scanner/provenance")
                            compression shouldBe false
                        }
                    }

                    postgresStorage shouldNotBeNull {
                        with(connection) {
                            url shouldBe "jdbc:postgresql://your-postgresql-server:5444/your-database"
                            schema shouldBe "public"
                            username shouldBe "username"
                            password shouldBe "password"
                            sslmode shouldBe "require"
                            sslcert shouldBe "/defaultdir/postgresql.crt"
                            sslkey shouldBe "/defaultdir/postgresql.pk8"
                            sslrootcert shouldBe "/defaultdir/root.crt"
                        }
                    }
                }
            }

            with(ortConfig.reporter) {
                reporters shouldNotBeNull {
                    keys should containExactlyInAnyOrder("CycloneDx", "FossId", "CtrlXAutomation")

                    get("CycloneDx") shouldNotBeNull {
                        options should containExactlyEntries(
                            "schema.version" to "1.6"
                        )
                        secrets should beEmpty()
                    }

                    get("FossId") shouldNotBeNull {
                        options should containExactlyEntries(
                            "serverUrl" to "https://fossid.example.com/instance/"
                        )
                        secrets should containExactlyEntries(
                            "user" to "user",
                            "apiKey" to "XYZ"
                        )
                    }

                    get("CtrlXAutomation") shouldNotBeNull {
                        options should containExactlyEntries(
                            "licenseCategoriesToInclude" to "include-in-disclosure-document"
                        )
                        secrets should beEmpty()
                    }
                }
            }

            with(ortConfig.notifier) {
                mail shouldNotBeNull {
                    hostName shouldBe "localhost"
                    port shouldBe 465
                    username shouldBe "user"
                    password shouldBe "password"
                    useSsl shouldBe true
                    fromAddress shouldBe "no-reply@oss-review-toolkit.org"
                }

                jira shouldNotBeNull {
                    host shouldBe "http://localhost"
                    username shouldBe "user"
                    password shouldBe "password"
                }
            }
        }

        "correctly prioritize the sources" {
            val configFile = createTestConfig(
                """
                ort:
                  scanner:
                    storages:
                      postgres:
                        connection:
                          url: "postgresql://your-postgresql-server:5444/your-database"
                          schema: public
                          username: username
                          password: password
                    provenanceStorage:
                      postgresStorage:
                        connection:
                          url: "postgresql://your-postgresql-server:5444/your-database"
                          schema: public
                          username: username
                          password: password
                """.trimIndent()
            )

            val env = mapOf(
                "ort.scanner.storages.postgres.connection.password" to "envPassword",
                "ort.scanner.provenanceStorage.postgresStorage.connection.password" to "envPassword"
            )

            withEnvironment(env) {
                val config = OrtConfiguration.load(
                    args = mapOf(
                        "ort.scanner.storages.postgres.connection.schema" to "argsSchema",
                        "ort.scanner.storages.postgres.connection.password" to "argsPassword",
                        "other.property" to "someValue"
                    ),
                    file = configFile
                )

                config.scanner.storages shouldNotBeNull {
                    val postgresStorage = this["postgres"]
                    postgresStorage.shouldBeInstanceOf<PostgresStorageConfiguration>()
                    with(postgresStorage.connection) {
                        username shouldBe "username"
                        schema shouldBe "argsSchema"
                        password shouldBe "envPassword"
                    }
                }

                config.scanner.provenanceStorage shouldNotBeNull {
                    postgresStorage.shouldBeInstanceOf<PostgresStorageConfiguration>().also { postgresStorage ->
                        with(postgresStorage.connection) {
                            username shouldBe "username"
                            schema shouldBe "public"
                            password shouldBe "envPassword"
                        }
                    }
                }
            }
        }

        "fail for an invalid configuration" {
            val configFile = createTestConfig(
                """
                ort:
                  scanner:
                    storages:
                      foo: baz
                """.trimIndent()
            )

            shouldThrow<ConfigException> {
                OrtConfiguration.load(file = configFile)
            }
        }

        "fail for invalid properties in the map with arguments" {
            val file = File("anotherNonExistingConfig.conf")
            val args = mapOf("ort.scanner.storages.new" to "test")

            shouldThrow<ConfigException> {
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
                ort:
                  scanner:
                    storages:
                      postgresStorage:
                        connection:
                          url: "postgresql://your-postgresql-server:5444/your-database"
                          schema: public
                          username: ${'$'}{POSTGRES_USERNAME}
                          password: ${'$'}{POSTGRES_PASSWORD}
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
                    with(postgresStorage.connection) {
                        username shouldBe user
                        password shouldBe password
                    }
                }
            }
        }

        "support environment variables" {
            val user = "user"
            val password = "password"
            val url = "url"
            val schema = "public"
            val env = mapOf(
                "ort.scanner.storages.postgresStorage.connection.username" to user,
                "ort.scanner.storages.postgresStorage.connection.url" to url,
                "ort__scanner__storages__postgresStorage__connection__schema" to schema,
                "ort__scanner__storages__postgresStorage__connection__password" to password
            )

            withEnvironment(env) {
                val config = OrtConfiguration.load(file = File("dummyPath"))

                config.scanner.storages shouldNotBeNull {
                    val postgresStorage = this["postgresStorage"]
                    postgresStorage.shouldBeInstanceOf<PostgresStorageConfiguration>()
                    with(postgresStorage.connection) {
                        username shouldBe user
                        password shouldBe password
                        url shouldBe url
                        schema shouldBe schema
                    }
                }
            }
        }

        "use defaults for propagating environment variables to child processes" {
            val config = OrtConfiguration()

            with(config) {
                deniedProcessEnvironmentVariablesSubstrings shouldBe EnvironmentVariableFilter.DEFAULT_DENY_SUBSTRINGS
                allowedProcessEnvironmentVariableNames shouldBe EnvironmentVariableFilter.DEFAULT_ALLOW_NAMES
            }
        }
    }
})

/**
 * Create a test configuration with the [data] specified.
 */
private fun TestConfiguration.createTestConfig(data: String): File =
    tempfile(suffix = ".yml").apply {
        writeText(data)
    }
