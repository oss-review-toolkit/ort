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

import com.typesafe.config.ConfigFactory

import io.github.config4k.extract
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.utils.ORT_NAME
import org.ossreviewtoolkit.utils.test.shouldNotBeNull
import org.ossreviewtoolkit.utils.test.containExactly as containExactlyEntries

class OrtConfigurationTest : WordSpec({
    "OrtConfiguration" should {
        "be deserializable from HOCON" {
            val hocon = javaClass.getResource("/reference.conf").readText()

            val config = ConfigFactory.parseString(hocon)
            val ortConfig = config.extract<OrtConfiguration>("ort")

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
            val configFile = createTempFile(ORT_NAME, javaClass.simpleName).apply { deleteOnExit() }

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

            config.scanner shouldNotBeNull {
                postgresStorage shouldNotBeNull {
                    username shouldBe "username"
                    schema shouldBe "argsSchema"
                }
            }
        }
    }
})
