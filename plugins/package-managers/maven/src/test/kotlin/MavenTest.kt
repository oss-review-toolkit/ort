/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.maven

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should

import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.plugins.packagemanagers.maven.tycho.addTychoExtension

class MavenTest : WordSpec({
    "mapDefinitionFiles()" should {
        "filter out Tycho definition files" {
            val tychoProjectDir1 = tempdir()
            tychoProjectDir1.addTychoExtension()
            val tychoDefinitionFile1 = tychoProjectDir1.resolve("pom.xml").apply { writeText("pom-tycho1") }
            val tychoSubProjectDir = tychoProjectDir1.resolve("subproject")
            val tychoSubModule = tychoSubProjectDir.resolve("pom.xml")
            val tychoProjectDir2 = tempdir()
            tychoProjectDir2.addTychoExtension()
            val tychoDefinitionFile2 = tychoProjectDir2.resolve("pom.xml").apply { writeText("pom-tycho2") }

            val mavenProjectDir = tempdir()
            val mavenDefinitionFile = mavenProjectDir.resolve("pom.xml").apply { writeText("pom-maven") }
            val mavenSubProjectDir = mavenProjectDir.resolve("subproject")
            val mavenSubModule = mavenSubProjectDir.resolve("pom.xml")

            val definitionFiles = listOf(
                tychoDefinitionFile1,
                mavenDefinitionFile,
                tychoDefinitionFile2,
                mavenSubModule,
                tychoSubModule
            )

            val maven = Maven()
            val mappedDefinitionFiles = maven.mapDefinitionFiles(tempdir(), definitionFiles, AnalyzerConfiguration())

            mappedDefinitionFiles should containExactlyInAnyOrder(mavenDefinitionFile, mavenSubModule)
        }
    }
})
