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

package com.here.ort.model

import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

import java.io.File

class OrtResultTest : WordSpec({
    "collectAllDependencies" should {
        "be able to get all direct dependencies of a package" {
            val expectedDependencies = listOf(
                    "Maven:com.typesafe.akka:akka-actor_2.12:2.5.6",
                    "Maven:com.typesafe:ssl-config-core_2.12:0.2.2",
                    "Maven:org.reactivestreams:reactive-streams:1.0.1"
            )

            val projectsDir = File("../analyzer/src/funTest/assets/projects")
            val resultFile = projectsDir.resolve("external/sbt-multi-project-example-expected-output.yml")
            val result = resultFile.readValue<OrtResult>()

            val pkg = Package.EMPTY.copy(id = Identifier("Maven:com.typesafe.akka:akka-stream_2.12:2.5.6"))
            result.collectAllDependencies(pkg, 1).map { it.id.toCoordinates() } shouldBe expectedDependencies
        }
    }
})
