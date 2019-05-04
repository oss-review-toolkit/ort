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

package com.here.ort.analyzer

import com.here.ort.analyzer.managers.*

import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.WordSpec

import java.io.File

class PackageManagerTest : WordSpec({
    val projectDir = File("src/funTest/assets/projects/synthetic/all-managers").absoluteFile

    "findManagedFiles" should {
        "find all managed files" {
            val managedFiles = PackageManager.findManagedFiles(projectDir)

            // The test project contains at least one file per package manager, so the result should also contain an
            // entry for each package manager.
            managedFiles.keys shouldBe PackageManager.ALL.toSet()

            // The keys in expected and actual maps of definition files are different instances of package manager
            // factories. So to compare values use the package manager names as keys instead.
            val managedFilesByName = managedFiles.mapKeys { (manager, _) ->
                manager.managerName
            }

            managedFilesByName["Bower"] shouldBe listOf(File(projectDir, "bower.json"))
            managedFilesByName["Bundler"] shouldBe listOf(File(projectDir, "Gemfile"))
            managedFilesByName["Cargo"] shouldBe listOf(File(projectDir, "Cargo.toml"))
            managedFilesByName["DotNet"] shouldBe listOf(File(projectDir, "test.csproj"))
            managedFilesByName["GoDep"] shouldBe listOf(File(projectDir, "Gopkg.toml"))
            managedFilesByName["Gradle"] shouldBe listOf(File(projectDir, "build.gradle"))
            managedFilesByName["Maven"] shouldBe listOf(File(projectDir, "pom.xml"))
            managedFilesByName["NPM"] shouldBe listOf(File(projectDir, "package.json"))
            managedFilesByName["NuGet"] shouldBe listOf(File(projectDir, "packages.config"))
            managedFilesByName["PhpComposer"] shouldBe listOf(File(projectDir, "composer.json"))
            managedFilesByName["PIP"] shouldBe listOf(File(projectDir, "setup.py"))
            managedFilesByName["SBT"] shouldBe listOf(File(projectDir, "build.sbt"))
            managedFilesByName["Stack"] shouldBe listOf(File(projectDir, "stack.yaml"))
            managedFilesByName["Yarn"] shouldBe listOf(File(projectDir, "package.json"))
        }

        "find only files for active package managers" {
            val managedFiles = PackageManager.findManagedFiles(
                projectDir,
                listOf(Gradle.Factory(), Pip.Factory(), Sbt.Factory())
            )

            managedFiles.size shouldBe 3

            // The keys in expected and actual maps of definition files are different instances of package manager
            // factories. So to compare values use the package manager names as keys instead.
            val managedFilesByName = managedFiles.mapKeys { (manager, _) ->
                manager.managerName
            }

            managedFilesByName["Gradle"] shouldBe listOf(File(projectDir, "build.gradle"))
            managedFilesByName["PIP"] shouldBe listOf(File(projectDir, "setup.py"))
            managedFilesByName["SBT"] shouldBe listOf(File(projectDir, "build.sbt"))
        }

        "find no files if no package managers are active" {
            val managedFiles = PackageManager.findManagedFiles(projectDir, emptyList())

            managedFiles.size shouldBe 0
        }

        "fail if the provided file is not a directory" {
            shouldThrow<IllegalArgumentException> {
                PackageManager.findManagedFiles(File(projectDir, "pom.xml"))
            }
        }
    }
})
