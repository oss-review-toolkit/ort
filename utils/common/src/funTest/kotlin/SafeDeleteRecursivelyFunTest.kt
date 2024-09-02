/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.utils.common

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe

import java.io.IOException

import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.plugins.versioncontrolsystems.git.Git

class SafeDeleteRecursivelyFunTest : WordSpec({
    "File.safeDeleteRecursively()" should {
        "be able to delete files that are not writable" {
            val dir = tempdir().apply {
                resolve("read-only.txt").apply {
                    writeText("Hello!")
                    check(setWritable(false))
                }
            }

            shouldNotThrow<IOException> {
                dir.safeDeleteRecursively()
            }

            dir.exists() shouldBe false
        }

        "be able to delete files with non-UTF8 characters in their name" {
            val pkg = Package.EMPTY.copy(
                vcsProcessed = VcsInfo(
                    VcsType.GIT,
                    "https://github.com/oss-review-toolkit/ort-test-fork-node-dir.git",
                    "a57c3b1b571dd91f464ae398090ba40f64ba38a2"
                )
            )

            val nodeDir = tempdir().resolve("node-dir")
            Git().download(pkg, nodeDir)

            shouldNotThrow<IOException> {
                nodeDir.safeDeleteRecursively()
            }

            nodeDir.exists() shouldBe false
        }

        "delete empty parent directories below a base directory" {
            val tempDir = tempdir()
            val baseDir = tempDir.resolve("a/basedir")
            val deleteDir = baseDir.resolve("c/delete").apply { safeMkdirs() }

            shouldNotThrow<IOException> {
                deleteDir.safeDeleteRecursively(baseDir)
            }

            deleteDir.exists() shouldBe false
            deleteDir.parentFile.exists() shouldBe false
            baseDir.exists() shouldBe true
        }
    }
})
