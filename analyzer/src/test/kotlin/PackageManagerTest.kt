/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.analyzer

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.analyzer.managers.*
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.test.createSpecTempDir

class PackageManagerTest : WordSpec({
    val definitionFiles = listOf(
        "bower/bower.json",
        "bundler/Gemfile",
        "cargo/Cargo.toml",
        "carthage/Cartfile.resolved",
        "cocoapods/Podfile",
        "composer/composer.json",
        "conan-py/conanfile.py",
        "conan-txt/conanfile.txt",
        "dotnet/dotnet.csproj",
        "glide/glide.yaml",
        "godep/Gopkg.toml",
        "godeps/Godeps.json",
        "gomod/go.mod",
        "gradle-groovy/build.gradle",
        "gradle-kotlin/build.gradle.kts",
        "maven/pom.xml",

        // Note that the NPM, PNPM and Yarn implementations share code. Internal logic decides dynamically whether to
        // process "package.json" with NPM, PNPM or Yarn.
        "npm-pnpm-and-yarn/package.json",

        "nuget/packages.config",
        "pip-requirements/requirements.txt",
        "pip-setup/setup.py",
        "pipenv/Pipfile.lock",
        "poetry/poetry.lock",
        "pub/pubspec.yaml",
        "sbt/build.sbt",
        "spdx-package/package.spdx.yml",
        "spdx-project/project.spdx.yml",
        "stack/stack.yaml"
    )
    val projectDir = createSpecTempDir()

    beforeSpec {
        definitionFiles.forEach { file ->
            projectDir.resolve(file).also { dir ->
                dir.parentFile.mkdirs()
            }.writeText("Dummy text to avoid the file to be empty, as empty files are skipped.")
        }
    }

    "findManagedFiles" should {
        "find all managed files" {
            val managedFiles = PackageManager.findManagedFiles(projectDir)

            // The test project contains at least one file per package manager, so the result should also contain an
            // entry for each package manager.
            managedFiles.keys shouldContainExactlyInAnyOrder PackageManager.ALL.values.filterNot {
                it is Unmanaged.Factory
            }

            // The keys in expected and actual maps of definition files are different instances of package manager
            // factories. So to compare values use the package manager types as keys instead.
            val managedFilesByName = managedFiles.map { (manager, files) ->
                manager.type to files.map { it.relativeTo(projectDir).invariantSeparatorsPath }
            }.toMap()

            assertSoftly {
                managedFilesByName["Bower"] should containExactly("bower/bower.json")
                managedFilesByName["Bundler"] should containExactly("bundler/Gemfile")
                managedFilesByName["Cargo"] should containExactly("cargo/Cargo.toml")
                managedFilesByName["Carthage"] should containExactly("carthage/Cartfile.resolved")
                managedFilesByName["CocoaPods"] should containExactly("cocoapods/Podfile")
                managedFilesByName["Composer"] should containExactly("composer/composer.json")
                managedFilesByName["Conan"] should containExactlyInAnyOrder(
                    "conan-py/conanfile.py",
                    "conan-txt/conanfile.txt"
                )
                managedFilesByName["DotNet"] should containExactly("dotnet/dotnet.csproj")
                managedFilesByName["GoDep"] should containExactlyInAnyOrder(
                    "glide/glide.yaml",
                    "godep/Gopkg.toml", "godeps/Godeps.json"
                )
                managedFilesByName["GoMod"] should containExactly("gomod/go.mod")
                managedFilesByName["Gradle"] should containExactlyInAnyOrder(
                    "gradle-groovy/build.gradle",
                    "gradle-kotlin/build.gradle.kts"
                )
                managedFilesByName["Maven"] should containExactly("maven/pom.xml")
                managedFilesByName["NPM"] should containExactly("npm-pnpm-and-yarn/package.json")
                managedFilesByName["NuGet"] should containExactly("nuget/packages.config")
                managedFilesByName["PIP"] should containExactlyInAnyOrder(
                    "pip-requirements/requirements.txt",
                    "pip-setup/setup.py"
                )
                managedFilesByName["Pipenv"] should containExactly("pipenv/Pipfile.lock")
                managedFilesByName["PNPM"] should containExactly("npm-pnpm-and-yarn/package.json")
                managedFilesByName["Poetry"] should containExactly("poetry/poetry.lock")
                managedFilesByName["Pub"] should containExactly("pub/pubspec.yaml")
                managedFilesByName["SBT"] should containExactly("sbt/build.sbt")
                managedFilesByName["SpdxDocumentFile"] should containExactlyInAnyOrder(
                    "spdx-package/package.spdx.yml",
                    "spdx-project/project.spdx.yml"
                )
                managedFilesByName["Stack"] should containExactly("stack/stack.yaml")
                managedFilesByName["Yarn"] should containExactly("npm-pnpm-and-yarn/package.json")
            }
        }

        "find only files for active package managers" {
            val managedFiles = PackageManager.findManagedFiles(
                projectDir,
                setOf(Gradle.Factory(), Pip.Factory(), Sbt.Factory())
            )

            managedFiles.size shouldBe 3

            // The keys in expected and actual maps of definition files are different instances of package manager
            // factories. So to compare values use the package manager types as keys instead.
            val managedFilesByName = managedFiles.map { (manager, files) ->
                manager.type to files.map { it.relativeTo(projectDir).invariantSeparatorsPath }
            }.toMap()

            managedFilesByName["Gradle"] should containExactlyInAnyOrder(
                "gradle-groovy/build.gradle",
                "gradle-kotlin/build.gradle.kts"
            )
            managedFilesByName["PIP"] should containExactlyInAnyOrder(
                "pip-requirements/requirements.txt",
                "pip-setup/setup.py"
            )
            managedFilesByName["SBT"] should containExactly("sbt/build.sbt")
        }

        "find no files if no package managers are active" {
            val managedFiles = PackageManager.findManagedFiles(projectDir, emptySet())

            managedFiles should beEmpty()
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
