/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.helper.utils

import io.kotest.core.TestConfiguration
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.helper.utils.PathExcludeGenerator.createExcludePatterns
import org.ossreviewtoolkit.helper.utils.PathExcludeGenerator.generateDirectoryExcludes
import org.ossreviewtoolkit.helper.utils.PathExcludeGenerator.generateFileExcludes
import org.ossreviewtoolkit.helper.utils.PathExcludeGenerator.generatePathExcludes
import org.ossreviewtoolkit.utils.test.readResource

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
            ) should containExactly(
                "build/**"
            )
        }

        "return excludes for a directory which contains regex special characters, e.g. the dot" {
            generateDirectoryExcludes(
                ".github/file.ext"
            ) should containExactly(
                ".github/**"
            )
        }

        "exclude the expected directories for a large data set" {
            val files = readPathExcludes("directory-paths.txt").lines().map { "$it/file.ext" }
            val expectedPatterns = readPathExcludes("expected-directory-exclude-patterns.txt")

            val patterns = generateDirectoryExcludes(*files.toTypedArray())

            patterns.sorted().joinToString("\n") shouldBe expectedPatterns
        }
    }

    "generateFileExcludes()" should {
        "exclude the expected files for a large data set" {
            val files = readPathExcludes("file-paths.txt").lines()
            val expectedPatterns = readPathExcludes("expected-file-exclude-patterns.txt")

            val patterns = generateFileExcludes(files).map { it.pattern }

            patterns.sorted().joinToString("\n") shouldBe expectedPatterns
        }

        "prefer the pattern with least amount of wildcards" {
            // Candidate patterns are 'build*.sh' and '*coverage*.sh' as both match.
            val files = listOf(
                "build-coverage.sh",
                "build-coverage-e2e.sh"
            )

            val patterns = generateFileExcludes(files).map { it.pattern }

            patterns should containExactly("build*.sh")
        }
    }

    "generatePathExcludes()" should {
        "return the expected patterns for a large data set" {
            val files = readPathExcludes("file-paths.txt").lines()
            val expectedPatterns = readPathExcludes("expected-exclude-patterns.txt")

            val patterns = generatePathExcludes(files).map { it.pattern }

            patterns.sorted().joinToString("\n") shouldBe expectedPatterns
        }
    }

    "createExcludePatterns()" should {
        fun getPatternsForFiles(vararg files: String) =
            createExcludePatterns(
                filenamePattern = "*_test.go",
                filePaths = files.asList()
            )

        "return the expected pattern if only a single file matches" {
            getPatternsForFiles(
                "src/some_test.go"
            ) should containExactly(
                "src/some_test.go"
            )

            getPatternsForFiles(
                "some_test.go"
            ) should containExactly(
                "some_test.go"
            )
        }

        "return the expected pattern if matching files have identical names but are in different directories" {
            getPatternsForFiles(
                "src/some_test.go",
                "src/module/some_test.go"
            ) should containExactly(
                "src/**/some_test.go"
            )

            getPatternsForFiles(
                "some_test.go",
                "module/some_test.go"
            ) should containExactly(
                "**/some_test.go"
            )
        }

        "return the expected pattern if matching files have different names but are in the same directory" {
            getPatternsForFiles(
                "src/some_test.go",
                "src/other_test.go"
            ) should containExactly(
                "src/*_test.go"
            )

            getPatternsForFiles(
                "some_test.go",
                "other_test.go"
            ) should containExactly(
                "*_test.go"
            )
        }

        "return the expected pattern if matching files have different names and are in different directories" {
            getPatternsForFiles(
                "src/some_test.go",
                "src/module/other_test.go"
            ) should containExactly(
                "src/**/*_test.go"
            )

            getPatternsForFiles(
                "some_test.go",
                "module/other_test.go"
            ) should containExactly(
                "**/*_test.go"
            )
        }
    }
})

private fun TestConfiguration.readPathExcludes(path: String) = readResource("/path-exclude-gen/$path")
