/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

package com.here.ort.util

import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.managers.*

import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.WordSpec

import java.io.File

class PackageManagerTest : WordSpec({
    val projectDir = File("../analyzer/src/funTest/assets/projects/synthetic/all-managers")

    "findManagedFiles" should {
        "find all managed files" {
            val result = PackageManager.findManagedFiles(projectDir)

            // The test project contains at least one file per package manager, so the result should also contain an
            // entry for each package manager.
            result.keys shouldBe PackageManager.ALL.toSet()

            result[Gradle] shouldBe listOf(File(projectDir, "build.gradle"))
            result[Maven] shouldBe listOf(File(projectDir, "pom.xml"))
            result[SBT] shouldBe listOf(File(projectDir, "build.sbt"))
            result[NPM] shouldBe listOf(File(projectDir, "package.json"))
            result[PIP] shouldBe listOf(File(projectDir, "setup.py"))
            result[GoDep] shouldBe listOf(File(projectDir, "Gopkg.toml"))
            result[Bundler] shouldBe listOf(File(projectDir, "Gemfile"))
            result[PhpComposer] shouldBe listOf(File(projectDir, "composer.json"))
        }

        "find only files for active package managers" {
            val result = PackageManager.findManagedFiles(projectDir, listOf(Gradle, SBT, PIP))

            result.size shouldBe 3
            result[Gradle] shouldBe listOf(File(projectDir, "build.gradle"))
            result[Maven] shouldBe null
            result[SBT] shouldBe listOf(File(projectDir, "build.sbt"))
            result[NPM] shouldBe null
            result[PIP] shouldBe listOf(File(projectDir, "setup.py"))
            result[PhpComposer] shouldBe null
        }

        "find no files if no package managers are active" {
            val result = PackageManager.findManagedFiles(projectDir, emptyList())

            result.size shouldBe 0
        }

        "fail if the provided file is not a directory" {
            shouldThrow<IllegalArgumentException> {
                PackageManager.findManagedFiles(File(projectDir, "pom.xml"))
            }
        }
    }
})
