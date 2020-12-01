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

            managedFilesByName["Bower"] should containExactly(projectDir.resolve("bower.json"))
            managedFilesByName["Bundler"] should containExactly(projectDir.resolve("Gemfile"))
            managedFilesByName["Cargo"] should containExactly(projectDir.resolve("Cargo.toml"))
            managedFilesByName["Carthage"] should containExactly(projectDir.resolve("Cartfile.resolved"))
            managedFilesByName["Composer"] should containExactly(projectDir.resolve("composer.json"))
            managedFilesByName["Conan"] should containExactly(projectDir.resolve("conanfile.py"))
            managedFilesByName["DotNet"] should containExactly(projectDir.resolve("test.csproj"))
            managedFilesByName["GoDep"] should containExactly(projectDir.resolve("Gopkg.toml"))
            managedFilesByName["GoMod"] should containExactly(projectDir.resolve("go.mod"))
            managedFilesByName["Gradle"] should containExactly(projectDir.resolve("build.gradle"))
            managedFilesByName["Maven"] should containExactly(projectDir.resolve("pom.xml"))
            managedFilesByName["NPM"] should containExactly(projectDir.resolve("package.json"))
            managedFilesByName["NuGet"] should containExactly(projectDir.resolve("packages.config"))
            managedFilesByName["PIP"] should containExactly(projectDir.resolve("setup.py"))
            managedFilesByName["Pipenv"] should containExactly(projectDir.resolve("Pipfile.lock"))
            managedFilesByName["Pub"] should containExactly(projectDir.resolve("pubspec.yaml"))
            managedFilesByName["SBT"] should containExactly(projectDir.resolve("build.sbt"))
            managedFilesByName["SpdxDocumentFile"] should containExactly(projectDir.resolve("project.spdx.yml"))
            managedFilesByName["Stack"] should containExactly(projectDir.resolve("stack.yaml"))
            managedFilesByName["Yarn"] should containExactly(projectDir.resolve("package.json"))
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

            managedFilesByName["Gradle"] should containExactly(projectDir.resolve("build.gradle"))
            managedFilesByName["PIP"] should containExactly(projectDir.resolve("setup.py"))
            managedFilesByName["SBT"] should containExactly(projectDir.resolve("build.sbt"))
        }

        "find no files if no package managers are active" {
            val managedFiles = PackageManager.findManagedFiles(projectDir, emptyList())

            managedFiles.size shouldBe 0
        }

        "fail if the provided file is not a directory" {
            shouldThrow<IllegalArgumentException> {
                PackageManager.findManagedFiles(projectDir.resolve("pom.xml"))
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
