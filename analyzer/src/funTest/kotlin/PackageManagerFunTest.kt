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
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.beEmpty
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.should

import java.io.File

import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.Includes
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.PathExcludeReason
import org.ossreviewtoolkit.model.config.PathInclude
import org.ossreviewtoolkit.model.config.PathIncludeReason
import org.ossreviewtoolkit.plugins.api.PluginConfig
import org.ossreviewtoolkit.utils.common.div

class PackageManagerFunTest : WordSpec({
    val definitionFiles = listOf(
        "bazel/MODULE.bazel",
        "bower/bower.json",
        "bundler/Gemfile",
        "cargo/Cargo.toml",
        "carthage/Cartfile.resolved",
        "cocoapods/Podfile",
        "composer/composer.json",
        "conan-py/conanfile.py",
        "conan-txt/conanfile.txt",
        "dotnet/test.csproj",
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
        "spm-app/Package.resolved",
        "spm-lib/Package.swift",
        "stack/stack.yaml"
    )

    val projectDir = tempdir()
    val packageManagers = PackageManagerFactory.ALL.values.map { it.create(PluginConfig.EMPTY) }

    beforeSpec {
        definitionFiles.writeFiles(projectDir)
    }

    "findManagedFiles()" should {
        "find all managed files" {
            val managedFiles = PackageManager.findManagedFiles(projectDir, packageManagers)

            // The test project contains at least one file per package manager, so the result should also contain an
            // entry for each package manager.
            managedFiles.keys.map { it.descriptor.id } shouldContainExactlyInAnyOrder
                PackageManagerFactory.ALL.values.map { it.descriptor.id }.filterNot { it == "Unmanaged" }

            val managedFilesById = managedFiles.groupById(projectDir).toSortedMap(String.CASE_INSENSITIVE_ORDER)

            assertSoftly {
                managedFilesById["Bazel"] should containExactly("bazel/MODULE.bazel")
                managedFilesById["Bower"] should containExactly("bower/bower.json")
                managedFilesById["Bundler"] should containExactly("bundler/Gemfile")
                managedFilesById["Cargo"] should containExactly("cargo/Cargo.toml")
                managedFilesById["Carthage"] should containExactly("carthage/Cartfile.resolved")
                managedFilesById["CocoaPods"] should containExactly("cocoapods/Podfile")
                managedFilesById["Composer"] should containExactly("composer/composer.json")
                managedFilesById["Conan"] should containExactlyInAnyOrder(
                    "conan-py/conanfile.py",
                    "conan-txt/conanfile.txt"
                )
                managedFilesById["GoMod"] should containExactly("gomod/go.mod")
                managedFilesById["GradleInspector"] should containExactlyInAnyOrder(
                    "gradle-groovy/build.gradle",
                    "gradle-kotlin/build.gradle.kts"
                )
                managedFilesById["Maven"] should containExactly("maven/pom.xml")
                managedFilesById["NPM"] should containExactly("npm-pnpm-and-yarn/package.json")
                managedFilesById["NuGet"] should containExactlyInAnyOrder(
                    "dotnet/test.csproj",
                    "nuget/packages.config"
                )
                managedFilesById["PIP"] should containExactlyInAnyOrder(
                    "pip-requirements/requirements.txt",
                    "pip-setup/setup.py"
                )
                managedFilesById["Pipenv"] should containExactly("pipenv/Pipfile.lock")
                managedFilesById["PNPM"] should containExactly("npm-pnpm-and-yarn/package.json")
                managedFilesById["Poetry"] should containExactly("poetry/poetry.lock")
                managedFilesById["Pub"] should containExactly("pub/pubspec.yaml")
                managedFilesById["SBT"] should containExactly("sbt/build.sbt")
                managedFilesById["SpdxDocumentFile"] should containExactlyInAnyOrder(
                    "spdx-package/package.spdx.yml",
                    "spdx-project/project.spdx.yml"
                )
                managedFilesById["SwiftPM"] should containExactlyInAnyOrder(
                    "spm-app/Package.resolved",
                    "spm-lib/Package.swift"
                )
                managedFilesById["Stack"] should containExactly("stack/stack.yaml")
                managedFilesById["Yarn"] should containExactly("npm-pnpm-and-yarn/package.json")
            }
        }

        "find only files for active package managers" {
            val managedFiles = PackageManager.findManagedFiles(
                projectDir,
                packageManagers.filter { it.descriptor.id.uppercase() in setOf("GRADLEINSPECTOR", "PIP", "SBT") }
            )

            managedFiles shouldHaveSize 3

            val managedFilesById = managedFiles.groupById(projectDir).toSortedMap(String.CASE_INSENSITIVE_ORDER)

            managedFilesById["GradleInspector"] should containExactlyInAnyOrder(
                "gradle-groovy/build.gradle",
                "gradle-kotlin/build.gradle.kts"
            )
            managedFilesById["PIP"] should containExactlyInAnyOrder(
                "pip-requirements/requirements.txt",
                "pip-setup/setup.py"
            )
            managedFilesById["SBT"] should containExactly("sbt/build.sbt")
        }

        "find no files if no package managers are active" {
            val managedFiles = PackageManager.findManagedFiles(projectDir, emptySet())

            managedFiles should beEmpty()
        }

        "take path excludes into account" {
            val tempDir = "test/"
            val definitionFilesWithExcludes = definitionFiles +
                listOf("pom.xml", "build.gradle", "build.sbt").map { "$tempDir$it" }
            val rootDir = tempdir()
            definitionFilesWithExcludes.writeFiles(rootDir)

            val pathExclude = PathExclude("$tempDir**", PathExcludeReason.TEST_OF)
            val excludes = Excludes(paths = listOf(pathExclude))

            val managedFilesById = PackageManager.findManagedFiles(rootDir, packageManagers, excludes = excludes)
                .groupById(rootDir).toSortedMap(String.CASE_INSENSITIVE_ORDER)

            managedFilesById["GradleInspector"] should containExactlyInAnyOrder(
                "gradle-groovy/build.gradle",
                "gradle-kotlin/build.gradle.kts"
            )
            managedFilesById["Maven"] should containExactly("maven/pom.xml")
            managedFilesById["SBT"] should containExactly("sbt/build.sbt")
        }

        "take path includes into account" {
            val tempDir = "test/"
            val definitionFilesWithExcludes = definitionFiles +
                listOf("pom.xml", "build.gradle", "build.sbt").map { "$tempDir$it" }
            val rootDir = tempdir()
            definitionFilesWithExcludes.writeFiles(rootDir)

            val pathInclude = PathInclude("$tempDir**", PathIncludeReason.SOURCE_OF)
            val includes = Includes(paths = listOf(pathInclude))

            val managedFilesById = PackageManager.findManagedFiles(rootDir, packageManagers, includes = includes)
                .groupById(rootDir).toSortedMap(String.CASE_INSENSITIVE_ORDER)

            managedFilesById["GradleInspector"] should containExactly(
                "test/build.gradle"
            )
            managedFilesById["Maven"] should containExactly("test/pom.xml")
            managedFilesById["SBT"] should containExactly("test/build.sbt")
        }

        "handle specific excluded definition files" {
            val pathExclude = PathExclude("gradle-groovy/build.gradle", PathExcludeReason.OTHER)
            val excludes = Excludes(paths = listOf(pathExclude))

            val managedFiles = PackageManager.findManagedFiles(projectDir, packageManagers, excludes = excludes)
            val managedFilesById = managedFiles.groupById(projectDir)

            managedFilesById["GradleInspector"] should containExactly(
                "gradle-kotlin/build.gradle.kts"
            )
        }

        "handle specific included definition files" {
            val pathInclude = PathInclude("gradle-groovy/build.gradle", PathIncludeReason.SOURCE_OF)
            val includes = Includes(paths = listOf(pathInclude))

            val managedFiles = PackageManager.findManagedFiles(projectDir, packageManagers, includes = includes)
            val managedFilesById = managedFiles.groupById(projectDir)

            managedFilesById["GradleInspector"] should containExactly(
                "gradle-groovy/build.gradle"
            )
        }

        "fail if the provided file is not a directory" {
            shouldThrow<IllegalArgumentException> {
                PackageManager.findManagedFiles(projectDir / "pom.xml", packageManagers)
            }
        }
    }
})

/**
 * Transform this map with definition files grouped by package manager factories, so that the results of specific
 * package managers can be easily accessed. The keys in expected and actual maps of definition files are different
 * instances of package manager factories. So to compare values use the package manager types as keys instead.
 */
private fun ManagedProjectFiles.groupById(projectDir: File) =
    map { (manager, files) ->
        manager.descriptor.id to files.map { it.relativeTo(projectDir).invariantSeparatorsPath }
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
