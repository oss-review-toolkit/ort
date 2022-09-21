/*
 * Copyright (C) 2022 EPAM Systems, Inc.
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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.helper.utils.PathExcludeGenerator.createExcludePatterns
import org.ossreviewtoolkit.helper.utils.PathExcludeGenerator.generateDirectoryExcludes

class PathExcludeGeneratorTest : WordSpec({
    "generateDirectoryExcludes()" should {
        fun generateDirectoryExcludes(vararg files: String): Set<String> =
            generateDirectoryExcludes(files.toList()).mapTo(mutableSetOf()) { it.pattern }

        "return the expected excludes for directories" {
            generateDirectoryExcludes(
                "docs/file.ext",
                "src/main/file.ext",
                "src/test/file.ext"
            ) should containExactlyInAnyOrder(
                "docs/**",
                "src/test/**"
            )
        }

        "exclude only the topmost possible directory" {
            generateDirectoryExcludes(
                "build/m4/file.ext"
            ) should containExactlyInAnyOrder(
                "build/**"
            )
        }

        "return excludes for a directory which contains regex special characters, e.g. the dot" {
            generateDirectoryExcludes(
                ".github/file.ext"
            ) should containExactlyInAnyOrder(
                ".github/**"
            )
        }

        "exclude the expected directories for a large data set" {
            val files = getAssetFile("directory-paths.txt").readLines().map { "$it/file.ext" }
            val expectedPatterns = getAssetFile("expected-directory-exclude-patterns.txt").readText()

            val patterns = generateDirectoryExcludes(*files.toTypedArray())

            patterns.joinToString("\n") shouldBe expectedPatterns
        }
    }

    "createExcludePatterns()" should {
        fun getPatternsForFiles(vararg files: String) =
            createExcludePatterns(
                filenamePattern = "*_test.go",
                files = files.mapTo(mutableSetOf()) { File(it) }
            )

        "return the expected pattern if only a single file matches" {
            getPatternsForFiles(
                "src/some_test.go"
            ) should containExactlyInAnyOrder(
                "src/some_test.go"
            )

            getPatternsForFiles(
                "some_test.go"
            ) should containExactlyInAnyOrder(
                "some_test.go"
            )
        }

        "return the expected pattern if matching files have identical names but are in different directories" {
            getPatternsForFiles(
                "src/some_test.go",
                "src/module/some_test.go"
            ) should containExactlyInAnyOrder(
                "src/**/some_test.go"
            )

            getPatternsForFiles(
                "some_test.go",
                "module/some_test.go"
            ) should containExactlyInAnyOrder(
                "**/some_test.go"
            )
        }

        "return the expected pattern if matching files have different names but are in the same directory" {
            getPatternsForFiles(
                "src/some_test.go",
                "src/other_test.go"
            ) should containExactlyInAnyOrder(
                "src/*_test.go"
            )

            getPatternsForFiles(
                "some_test.go",
                "other_test.go"
            ) should containExactlyInAnyOrder(
                "*_test.go"
            )
        }

        "return the expected pattern if matching file have different names and are in different directories" {
            getPatternsForFiles(
                "src/some_test.go",
                "src/module/other_test.go"
            ) should containExactlyInAnyOrder(
                "src/**/*_test.go"
            )

            getPatternsForFiles(
                "some_test.go",
                "module/other_test.go"
            ) should containExactlyInAnyOrder(
                "**/*_test.go"
            )
        }
    }
})

private fun getAssetFile(path: String) = File("src/test/assets/path-exclude-gen").resolve(path)
