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

package org.ossreviewtoolkit.cli

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.analyzer.ManagedProjectFiles
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.managers.*
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.PathExcludeReason
import org.ossreviewtoolkit.utils.test.createSpecTempDir
import org.ossreviewtoolkit.utils.test.createTestTempDir

class PackageManagerFunTest : WordSpec({
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
        definitionFiles.writeFiles(projectDir)
    }

    "findManagedFiles" should {
        "find all managed files" {
            val managedFiles = PackageManager.findManagedFiles(projectDir)

            // The test project contains at least one file per package manager, so the result should also contain an
            // entry for each package manager.
            managedFiles.keys shouldContainExactlyInAnyOrder PackageManager.ALL.values.filterNot {
                it is Unmanaged.Factory
            }

            val managedFilesByName = managedFiles.groupByName(projectDir)

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
                setOf(
                    PackageManager.ALL.getValue("Gradle"),
                    PackageManager.ALL.getValue("Pip"),
                    PackageManager.ALL.getValue("Sbt")
                )
            )

            managedFiles.size shouldBe 3

            val managedFilesByName = managedFiles.groupByName(projectDir)

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

        "take path excludes into account" {
            val tempDir = "test/"
            val definitionFilesWithExcludes = definitionFiles +
                    listOf("pom.xml", "build.gradle", "build.sbt").map { "$tempDir$it" }
            val rootDir = createTestTempDir()
            definitionFilesWithExcludes.writeFiles(rootDir)

            val pathExclude = PathExclude("$tempDir**", PathExcludeReason.TEST_OF)
            val excludes = Excludes(paths = listOf(pathExclude))

            val managedFilesByName = PackageManager.findManagedFiles(rootDir, excludes = excludes).groupByName(rootDir)

            managedFilesByName["Gradle"] should containExactlyInAnyOrder(
                "gradle-groovy/build.gradle",
                "gradle-kotlin/build.gradle.kts"
            )
            managedFilesByName["Maven"] should containExactly("maven/pom.xml")
            managedFilesByName["SBT"] should containExactly("sbt/build.sbt")
        }

        "handle specific excluded definition files" {
            val pathExclude = PathExclude("gradle-groovy/build.gradle", PathExcludeReason.OTHER)
            val excludes = Excludes(paths = listOf(pathExclude))

            val managedFiles = PackageManager.findManagedFiles(projectDir, excludes = excludes)
            val managedFilesByName = managedFiles.groupByName(projectDir)

            managedFilesByName["Gradle"] should containExactly(
                "gradle-kotlin/build.gradle.kts"
            )
        }

        "fail if the provided file is not a directory" {
            shouldThrow<IllegalArgumentException> {
                PackageManager.findManagedFiles(projectDir.resolve("pom.xml"))
            }
        }
    }
})

/**
 * Transform this map with definition files grouped by package manager factories, so that the results of specific
 * package managers can be easily accessed. The keys in expected and actual maps of definition files are different
 * instances of package manager factories. So to compare values use the package manager types as keys instead.
 */
private fun ManagedProjectFiles.groupByName(projectDir: File) =
    map { (manager, files) ->
        manager.type to files.map { it.relativeTo(projectDir).invariantSeparatorsPath }
    }.toMap()

/**
 * Create files with a dummy content in the given [directory] for all the path names in this collection.
 */
private fun Collection<String>.writeFiles(directory: File) {
    forEach { file ->
        directory.resolve(file).also { dir ->
            dir.parentFile.mkdirs()
        }.writeText("Dummy text to avoid the file to be empty, as empty files are skipped.")
    }
}
