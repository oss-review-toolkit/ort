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

package org.ossreviewtoolkit.analyzer

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.analyzer.managers.*
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType

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

            managedFilesByName["Bower"] should containExactly(File(projectDir, "bower.json"))
            managedFilesByName["Bundler"] should containExactly(File(projectDir, "Gemfile"))
            managedFilesByName["Cargo"] should containExactly(File(projectDir, "Cargo.toml"))
            managedFilesByName["Conan"] should containExactly(File(projectDir, "conanfile.py"))
            managedFilesByName["DotNet"] should containExactly(File(projectDir, "test.csproj"))
            managedFilesByName["GoDep"] should containExactly(File(projectDir, "Gopkg.toml"))
            managedFilesByName["GoMod"] should containExactly(File(projectDir, "go.mod"))
            managedFilesByName["Gradle"] should containExactly(File(projectDir, "build.gradle"))
            managedFilesByName["Maven"] should containExactly(File(projectDir, "pom.xml"))
            managedFilesByName["NPM"] should containExactly(File(projectDir, "package.json"))
            managedFilesByName["NuGet"] should containExactly(File(projectDir, "packages.config"))
            managedFilesByName["PhpComposer"] should containExactly(File(projectDir, "composer.json"))
            managedFilesByName["PIP"] should containExactly(File(projectDir, "setup.py"))
            managedFilesByName["Pipenv"] should containExactly(File(projectDir, "Pipfile.lock"))
            managedFilesByName["Pub"] should containExactly(File(projectDir, "pubspec.yaml"))
            managedFilesByName["SBT"] should containExactly(File(projectDir, "build.sbt"))
            managedFilesByName["Stack"] should containExactly(File(projectDir, "stack.yaml"))
            managedFilesByName["Yarn"] should containExactly(File(projectDir, "package.json"))
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

            managedFilesByName["Gradle"] should containExactly(File(projectDir, "build.gradle"))
            managedFilesByName["PIP"] should containExactly(File(projectDir, "setup.py"))
            managedFilesByName["SBT"] should containExactly(File(projectDir, "build.sbt"))
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

    "processPackageVcs" should {
        "split a GitHub browsing URL into its components" {
            val vcsFromPackage = VcsInfo(
                type = VcsType.GIT,
                url = "https://github.com/hamcrest/JavaHamcrest/hamcrest-core",
                revision = "",
                path = ""
            )

            PackageManager.processPackageVcs(vcsFromPackage) shouldBe VcsInfo(
                type = VcsType.GIT,
                url = "https://github.com/hamcrest/JavaHamcrest.git",
                revision = "",
                path = "hamcrest-core"
            )
        }

        "maintain a known VCS type" {
            val vcsFromPackage = VcsInfo(
                type = VcsType.SUBVERSION,
                url = "http://svn.apache.org/repos/asf/commons/proper/codec/trunk",
                revision = ""
            )
            val homepageUrl = "http://commons.apache.org/proper/commons-codec/"

            PackageManager.processPackageVcs(vcsFromPackage, homepageUrl) shouldBe VcsInfo(
                type = VcsType.SUBVERSION,
                url = "http://svn.apache.org/repos/asf/commons/proper/codec",
                revision = "trunk"
            )
        }

        "maintain an unknown VCS type" {
            val vcsFromPackage = VcsInfo(
                type = VcsType(listOf("darcs")),
                url = "http://hub.darcs.net/ross/transformers",
                revision = ""
            )

            PackageManager.processPackageVcs(vcsFromPackage) shouldBe VcsInfo(
                type = VcsType(listOf("darcs")),
                url = "http://hub.darcs.net/ross/transformers",
                revision = ""
            )
        }
    }
})
