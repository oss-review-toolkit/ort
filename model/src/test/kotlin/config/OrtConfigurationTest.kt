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

package com.here.ort.model.config

import com.here.ort.utils.ORT_NAME

import com.typesafe.config.ConfigFactory

import io.github.config4k.extract

import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.WordSpec

import java.io.File

class OrtConfigurationTest : WordSpec({
    "OrtConfiguration" should {
        "be deserializable from HOCON" {
            val hocon = javaClass.getResource("/reference.conf").readText()

            val config = ConfigFactory.parseString(hocon)
            val ortConfig = config.extract<OrtConfiguration>("ort")

            ortConfig.scanner.let { scanner ->
                scanner shouldNotBe null

                scanner!!.archive shouldNotBe null
                scanner.archive!!.let { archive ->
                    archive.patterns shouldBe listOf("LICENSE*", "COPYING*")
                    archive.storage.let { storage ->
                        storage.httpFileStorage shouldBe null
                        storage.localFileStorage shouldNotBe null
                        storage.localFileStorage!!.let { localFileStorage ->
                            localFileStorage.directory shouldBe File("~/.ort/scanner/archive")
                        }
                    }
                }

                scanner.fileBasedStorage.let { fileBased ->
                    fileBased shouldNotBe null
                    fileBased!!.backend.let { backend ->
                        backend shouldNotBe null

                        backend.httpFileStorage.let { httpFileStorage ->
                            httpFileStorage shouldNotBe null
                            httpFileStorage!!.url shouldBe "https://your-http-server"
                            httpFileStorage.headers shouldBe mapOf("key1" to "value1", "key2" to "value2")
                        }

                        backend.localFileStorage.let { localFileStorage ->
                            localFileStorage shouldNotBe null
                            localFileStorage!!.directory shouldBe File("~/.ort/scanner/results")
                        }
                    }
                }

                scanner.postgresStorage.let { postgres ->
                    postgres shouldNotBe null
                    postgres!!.url shouldBe "postgresql://your-postgresql-server:5444/your-database"
                    postgres.schema shouldBe "schema"
                    postgres.username shouldBe "username"
                    postgres.password shouldBe "password"
                    postgres.sslmode shouldBe "required"
                    postgres.sslcert shouldBe "/defaultdir/postgresql.crt"
                    postgres.sslkey shouldBe "/defaultdir/postgresql.pk8"
                    postgres.sslrootcert shouldBe "/defaultdir/root.crt"
                }

                scanner.options shouldNotBe null
            }
        }

        "correctly prioritize the sources" {
            val configFile = createTempFile(ORT_NAME, javaClass.simpleName)
            configFile.deleteOnExit()
            configFile.writeText(
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
                args = mapOf("ort.scanner.postgresStorage.schema" to "argsSchema"),
                configFile = configFile
            )

            config.scanner shouldNotBe null
            config.scanner!!.postgresStorage shouldNotBe null
            config.scanner!!.postgresStorage!!.username shouldBe "username"
            config.scanner!!.postgresStorage!!.schema shouldBe "argsSchema"
        }
    }
})
