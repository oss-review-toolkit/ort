/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.analyzer.managers.*
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType

class PackageManagerTest : WordSpec({
    val projectDir = File("src/funTest/assets/projects/synthetic").absoluteFile

    "findManagedFiles" should {
        "find all managed files" {
            val managedFiles = PackageManager.findManagedFiles(projectDir)

            // The test project contains at least one file per package manager, so the result should also contain an
            // entry for each package manager.
            managedFiles.keys shouldContainExactlyInAnyOrder PackageManager.ALL

            // The keys in expected and actual maps of definition files are different instances of package manager
            // factories. So to compare values use the package manager names as keys instead.
            val managedFilesByName = managedFiles.mapKeys { (manager, _) ->
                manager.managerName
            }

            assertSoftly {
                managedFilesByName["Bower"] should containExactlyInAnyOrder(
                    EXPECTED_BOWER_DEFINITION_FILES.map { projectDir.resolve(it) }
                )

                managedFilesByName["Bundler"] should containExactlyInAnyOrder(
                    EXPECTED_BUNDLER_DEFINITION_FILES.map { projectDir.resolve(it) }
                )

                managedFilesByName["Cargo"] should containExactlyInAnyOrder(
                    EXPECTED_CARGO_DEFINITION_FILES.map { projectDir.resolve(it) }
                )

                managedFilesByName["Carthage"] should containExactlyInAnyOrder(
                    EXPECTED_CARTHAGE_DEFINITION_FILES.map { projectDir.resolve(it) }
                )

                managedFilesByName["CocoaPods"] should containExactlyInAnyOrder(
                    EXPECTED_COCOAPODS_DEFINITION_FILES.map { projectDir.resolve(it) }
                )

                managedFilesByName["Composer"] should containExactlyInAnyOrder(
                    EXPECTED_COMPOSER_DEFINITION_FILES.map { projectDir.resolve(it) }
                )

                managedFilesByName["Conan"] should containExactlyInAnyOrder(
                    EXPECTED_CONAN_DEFINITION_FILES.map { projectDir.resolve(it) }
                )

                managedFilesByName["DotNet"] should containExactlyInAnyOrder(
                    EXPECTED_DOTNET_DEFINITION_FILES.map { projectDir.resolve(it) }
                )

                managedFilesByName["GoDep"] should containExactlyInAnyOrder(
                    EXPECTED_GODEP_DEFINITION_FILES.map { projectDir.resolve(it) }
                )

                managedFilesByName["GoMod"] should containExactlyInAnyOrder(
                    EXPECTED_GOMOD_DEFINITION_FILES.map { projectDir.resolve(it) }
                )

                managedFilesByName["Gradle"] should containExactlyInAnyOrder(
                    EXPECTED_GRADLE_DEFINITION_FILES.map { projectDir.resolve(it) }
                )

                managedFilesByName["Maven"] should containExactlyInAnyOrder(
                    EXPECTED_MAVEN_DEFINITION_FILES.map { projectDir.resolve(it) }
                )

                managedFilesByName["NPM"] should containExactlyInAnyOrder(
                    EXPECTED_NPM_YARN_DEFINITION_FILES.map { projectDir.resolve(it) }
                )

                managedFilesByName["NuGet"] should containExactlyInAnyOrder(
                    EXPECTED_NUGET_DEFINITION_FILES.map { projectDir.resolve(it) }
                )

                managedFilesByName["PIP"] should containExactlyInAnyOrder(
                    EXPECTED_PIP_DEFINITION_FILES.map { projectDir.resolve(it) }
                )

                managedFilesByName["Pipenv"] should containExactlyInAnyOrder(
                    EXPECTED_PIPENV_DEFINITION_FILES.map { projectDir.resolve(it) }
                )

                managedFilesByName["Pub"] should containExactlyInAnyOrder(
                    EXPECTED_PUB_DEFINITION_FILES.map { projectDir.resolve(it) }
                )

                managedFilesByName["SBT"] should containExactlyInAnyOrder(
                    EXPECTED_SBT_DEFINITION_FILES.map { projectDir.resolve(it) }
                )

                managedFilesByName["SpdxDocumentFile"] should containExactlyInAnyOrder(
                    EXPECTED_SPDX_DEFINITION_FILES.map { projectDir.resolve(it) }
                )

                managedFilesByName["Stack"] should containExactlyInAnyOrder(
                    EXPECTED_STACK_DEFINITION_FILES.map { projectDir.resolve(it) }
                )

                managedFilesByName["Yarn"] should containExactlyInAnyOrder(
                    EXPECTED_NPM_YARN_DEFINITION_FILES.map { projectDir.resolve(it) }
                )
            }
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

            managedFilesByName["Gradle"] should containExactlyInAnyOrder(
                EXPECTED_GRADLE_DEFINITION_FILES.map { projectDir.resolve(it) }
            )

            managedFilesByName["PIP"] should containExactlyInAnyOrder(
                EXPECTED_PIP_DEFINITION_FILES.map { projectDir.resolve(it) }
            )

            managedFilesByName["SBT"] should containExactly(
                EXPECTED_SBT_DEFINITION_FILES.map { projectDir.resolve(it) }
            )
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

private val EXPECTED_BOWER_DEFINITION_FILES = listOf(
    "bower/bower.json"
)

private val EXPECTED_BUNDLER_DEFINITION_FILES = listOf(
    "bundler/gemspec/Gemfile",
    "bundler/lockfile/Gemfile",
    "bundler/no-lockfile/Gemfile"
)

private val EXPECTED_CARGO_DEFINITION_FILES = listOf(
    "cargo/Cargo.toml",
    "cargo-subcrate/Cargo.toml",
    "cargo-subcrate/client/Cargo.toml",
    "cargo-subcrate/integration/Cargo.toml"
)

private val EXPECTED_CARTHAGE_DEFINITION_FILES = listOf(
    "carthage/Cartfile.resolved"
)

private val EXPECTED_COCOAPODS_DEFINITION_FILES = listOf(
    "cocoapods/dep-tree/Podfile",
    "cocoapods/no-lockfile/Podfile",
    "cocoapods/regular/Podfile"
)

private val EXPECTED_COMPOSER_DEFINITION_FILES = listOf(
    "composer/empty-deps/composer.json",
    "composer/no-deps/composer.json",
    "composer/lockfile/composer.json",
    "composer/no-lockfile/composer.json",
    "composer/with-provide/composer.json",
    "composer/with-replace/composer.json"
)

private val EXPECTED_CONAN_DEFINITION_FILES = listOf(
    "conan-py/conanfile.py",
    "conan-txt/conanfile.txt"
)

private val EXPECTED_DOTNET_DEFINITION_FILES = listOf(
    "dotnet/subProjectTest/test.csproj",
    "dotnet/subProjectTestWithNuspec/test.csproj"
)

private val EXPECTED_GODEP_DEFINITION_FILES = listOf(
    "godep/glide/glide.yaml",
    "godep/godeps/Godeps/Godeps.json",
    "godep/lockfile/Gopkg.toml"
)

private val EXPECTED_GOMOD_DEFINITION_FILES = listOf(
    "gomod/go.mod",
    "gomod-subpkg/go.mod"
)

private val EXPECTED_GRADLE_DEFINITION_FILES = listOf(
    "gradle/build.gradle",
    "gradle/app/build.gradle",
    "gradle/lib/build.gradle",
    "gradle/lib-without-repo/build.gradle",
    "gradle-library/build.gradle",
    "gradle-library/app/build.gradle",
    "gradle-library/lib/build.gradle",
    "gradle-unsupported-version/build.gradle",
    "gradle-android/build.gradle",
    "gradle-android/app/build.gradle",
    "gradle-android/lib/build.gradle",
    "gradle-bom/build.gradle"
)

private val EXPECTED_MAVEN_DEFINITION_FILES = listOf(
    "maven/pom.xml",
    "maven/app/pom.xml",
    "maven/lib/pom.xml",
    "maven-parent/pom.xml",
    "maven-wagon/pom.xml"
)

private val EXPECTED_NPM_YARN_DEFINITION_FILES = listOf(
    // Note that the NPM and Yarn implementations share code. Internal logic decides dynamically whether to process
    // "package.json" with NPM or Yarn.
    "npm/node-modules/package.json",
    "npm/package-lock/package.json",
    "npm/shrinkwrap/package.json",
    "npm/no-lockfile/package.json",
    "npm-babel/package.json",
    "npm-version-urls/package.json",

    "yarn/package.json",
    "yarn-workspaces/package.json",
    "yarn-workspaces/packages/pkg1/package.json",
    "yarn-workspaces/packages/pkg2/package.json"
)

private val EXPECTED_NUGET_DEFINITION_FILES = listOf(
    "nuget/packages.config"
)

private val EXPECTED_PIP_DEFINITION_FILES = listOf(
    // Note that while "pip/setup.py" is also present, ORT only lists "requirements.txt" as the definition file if
    // "setup.py" is in the same directory. Still, information from both files is considered by ORT.
    "pip/requirements.txt",
    "pip-python3/requirements.txt"
)

private val EXPECTED_PIPENV_DEFINITION_FILES = listOf(
    "pipenv/Pipfile.lock",
    "pipenv-python3/Pipfile.lock"
)

private val EXPECTED_PUB_DEFINITION_FILES = listOf(
    "pub/any-version/pubspec.yaml",
    "pub/no-lockfile/pubspec.yaml",
    "pub/project-with-flutter/pubspec.yaml"
)

private val EXPECTED_SBT_DEFINITION_FILES = listOf(
    "sbt-http4s-template/build.sbt"
)

private val EXPECTED_SPDX_DEFINITION_FILES = listOf(
    "spdx/package/libs/curl/package.spdx.yml",
    "spdx/package/libs/zlib/package.spdx.yml",
    "spdx/project/project.spdx.yml"
)

private val EXPECTED_STACK_DEFINITION_FILES = listOf(
    "stack-yesodweb-simple/stack.yaml"
)
