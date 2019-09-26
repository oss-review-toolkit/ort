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

            ortConfig shouldNotBe null

            ortConfig.scanner.let { scanner ->
                scanner shouldNotBe null

                scanner!!.artifactoryStorage.let { artifactory ->
                    artifactory shouldNotBe null
                    artifactory!!.url shouldBe "https://your-artifactory-server"
                    artifactory.repository shouldBe "repository"
                    artifactory.apiToken shouldBe "apiToken"
                }

                scanner.localFileStorage.let { localFile ->
                    localFile shouldNotBe null
                    localFile!!.directory shouldBe File("~/.ort/scanner")
                }

                scanner.postgresStorage.let { postgres ->
                    postgres shouldNotBe null
                    postgres!!.url shouldBe "postgresql://your-postgresql-server:5444/your-database"
                    postgres.schema shouldBe "schema"
                    postgres.username shouldBe "username"
                    postgres.password shouldBe "password"
                }
            }
        }
    }
})
