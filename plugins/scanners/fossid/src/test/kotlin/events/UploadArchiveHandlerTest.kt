/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.fossid.events

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.file.aDirectory
import io.kotest.matchers.file.aFile
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import io.mockk.mockk

import java.io.File

import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.Includes
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.PathExcludeReason
import org.ossreviewtoolkit.model.config.PathInclude
import org.ossreviewtoolkit.model.config.PathIncludeReason
import org.ossreviewtoolkit.plugins.scanners.fossid.createConfig
import org.ossreviewtoolkit.plugins.scanners.fossid.createServiceMock
import org.ossreviewtoolkit.scanner.provenance.NestedProvenance
import org.ossreviewtoolkit.utils.common.div

class UploadArchiveHandlerTest : WordSpec({
    "deleteExcludedFiles()" should {
        "delete files that match exclusion patterns" {
            val tempDir = tempdir().createFiles("include.txt", "exclude.txt", "subdir/subfile.txt")
            val excludes = createExcludes("**/exclude.txt")
            val handler = createHandler()

            handler.deleteExcludedFiles(tempDir, null, excludes)

            tempDir / "include.txt" shouldBe aFile()
            tempDir / "exclude.txt" shouldNotBe aFile()
            tempDir / "subdir" shouldBe aDirectory()
            tempDir / "subdir" / "subfile.txt" shouldBe aFile()
        }

        "delete directories that match exclusion patterns" {
            val tempDir = tempdir().createDirectories("include", "node_modules")
            tempDir.createFiles("include/file.txt", "node_modules/package.json")
            val excludes = createExcludes("**/node_modules/**", reason = PathExcludeReason.BUILD_TOOL_OF)
            val handler = createHandler()

            handler.deleteExcludedFiles(tempDir, null, excludes)

            tempDir / "include" shouldBe aDirectory()
            tempDir / "include" / "file.txt" shouldBe aFile()
            tempDir / "node_modules" shouldNotBe aDirectory()
            tempDir / "node_modules" / "package.json" shouldNotBe aFile()
        }

        "exclude files in subdirectories" {
            val tempDir = tempdir().createDirectories("src", "src/main")
            tempDir.createFiles(
                "keep.txt",
                "src/keep.kt",
                "src/test.log",
                "src/main/debug.tmp",
                "src/main/Main.kt"
            )
            val excludes = createExcludes("**/*.log", "**/*.tmp")
            val handler = createHandler()

            handler.deleteExcludedFiles(tempDir, null, excludes)

            tempDir / "keep.txt" shouldBe aFile()
            tempDir / "src" / "keep.kt" shouldBe aFile()
            tempDir / "src" / "main" / "Main.kt" shouldBe aFile()
            tempDir / "src" / "test.log" shouldNotBe aFile()
            tempDir / "src" / "main" / "debug.tmp" shouldNotBe aFile()
            tempDir / "src" shouldBe aDirectory()
            tempDir / "src" / "main" shouldBe aDirectory()
        }

        "include only files that match inclusion patterns" {
            val tempDir = tempdir().createDirectories("src", "build")
            tempDir.createFiles(
                "src/main.kt",
                "build/output.jar",
                "src/test.kt",
                "README.md"
            )
            val includes = createIncludes("src/**/*.kt")
            val handler = createHandler()

            handler.deleteExcludedFiles(tempDir, includes, null)

            tempDir / "src" / "main.kt" shouldBe aFile()
            tempDir / "src" / "test.kt" shouldBe aFile()
            tempDir / "build" / "output.jar" shouldNotBe aFile()
            tempDir / "README.md" shouldNotBe aFile()
        }

        "handle both includes and excludes together" {
            val tempDir = tempdir().createDirectories("src", "build")
            tempDir.createFiles(
                "src/main.kt",
                "src/test.kt",
                "src/debug.log",
                "build/output.jar"
            )
            val includes = createIncludes("src/**")
            val excludes = createExcludes("**/*.log")
            val handler = createHandler()

            handler.deleteExcludedFiles(tempDir, includes, excludes)

            tempDir / "src" / "main.kt" shouldBe aFile()
            tempDir / "src" / "test.kt" shouldBe aFile()
            tempDir / "build" / "output.jar" shouldNotBe aFile()
            tempDir / "src" / "debug.log" shouldNotBe aFile()
        }

        "handle directory inclusion patterns" {
            val tempDir = tempdir().createDirectories("src", "test", "build")
            tempDir.createFiles(
                "src/main.kt",
                "test/spec.kt",
                "build/output.jar",
                "README.md"
            )
            val includes = createIncludes("src/**", "test/**")
            val handler = createHandler()

            handler.deleteExcludedFiles(tempDir, includes, null)

            tempDir / "src" / "main.kt" shouldBe aFile()
            tempDir / "test" / "spec.kt" shouldBe aFile()
            tempDir / "build" / "output.jar" shouldNotBe aFile()
            tempDir / "README.md" shouldNotBe aFile()
            tempDir / "src" shouldBe aDirectory()
            tempDir / "test" shouldBe aDirectory()
        }

        "handle overlapping includes and excludes" {
            val tempDir = tempdir()
                .createDirectories("src/main", "src/test", "src/build", "src/build/subdir", "build")
            tempDir.createFiles(
                "src/main/Main.kt",
                "src/test/Test.kt",
                "src/build/output.jar",
                "src/build/config.properties",
                "src/build/subdir/nested.class",
                "README.md"
            )
            val includes = createIncludes("src/**")
            val excludes = createExcludes("src/build/**", reason = PathExcludeReason.BUILD_TOOL_OF)
            val handler = createHandler()

            handler.deleteExcludedFiles(tempDir, includes, excludes)

            tempDir / "src" / "main" / "Main.kt" shouldBe aFile()
            tempDir / "src" / "test" / "Test.kt" shouldBe aFile()
            tempDir / "src" / "build" / "output.jar" shouldNotBe aFile()
            tempDir / "src" / "build" / "config.properties" shouldNotBe aFile()
            tempDir / "src" / "build" / "subdir" / "nested.class" shouldNotBe aFile()
            tempDir / "README.md" shouldNotBe aFile()
            tempDir / "build" shouldNotBe aDirectory()
            tempDir / "src" / "build" shouldNotBe aDirectory()
            tempDir / "src" / "build" / "subdir" shouldNotBe aDirectory()
            tempDir / "src" / "main" shouldBe aDirectory()
            tempDir / "src" / "test" shouldBe aDirectory()
            tempDir / "src" shouldBe aDirectory()
        }
    }
})

private fun File.createFiles(vararg paths: String): File {
    paths.forEach { path ->
        resolve(path).apply {
            parentFile.mkdirs()
            writeText("test content")
        }
    }

    return this
}

private fun File.createDirectories(vararg paths: String): File {
    paths.forEach { path -> resolve(path).mkdirs() }
    return this
}

private fun createExcludes(
    vararg patterns: String,
    reason: PathExcludeReason = PathExcludeReason.TEST_TOOL_OF
): Excludes {
    val pathExcludes = patterns.map { pattern ->
        PathExclude(pattern = pattern, reason = reason)
    }

    return Excludes(paths = pathExcludes)
}

private fun createIncludes(vararg patterns: String, reason: PathIncludeReason = PathIncludeReason.OTHER): Includes {
    val pathIncludes = patterns.map { pattern ->
        PathInclude(pattern = pattern, reason = reason)
    }

    return Includes(paths = pathIncludes)
}

private fun createHandler(): UploadArchiveHandler =
    UploadArchiveHandler(
        createConfig(),
        createServiceMock(),
        mockk<NestedProvenance>(relaxed = true)
    )
