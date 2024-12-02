/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.scanner.utils

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.file.aDirectory
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldNotBe

import java.io.IOException
import java.io.InputStream

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.utils.FileProvenanceFileStorage
import org.ossreviewtoolkit.model.utils.ProvenanceFileStorage
import org.ossreviewtoolkit.scanner.FileList
import org.ossreviewtoolkit.utils.ort.storage.LocalFileStorage

class FileListResolverTest : WordSpec({
    "resolve()" should {
        "create the expected file list" {
            val resolver = FileListResolver(
                storage = FileProvenanceFileStorage(
                    storage = LocalFileStorage(tempdir()),
                    filename = "bytes"
                ),
                provenanceDownloader = {
                    createTempDirWithFiles(
                        ".git/index",
                        "LICENSE",
                        "src/cli/main.cpp"
                    )
                }
            )

            val fileList = resolver.resolve(ArtifactProvenance(sourceArtifact = RemoteArtifact.EMPTY))

            fileList.ignorePatterns should containExactlyInAnyOrder(
                "**/.bzr",
                "**/.git",
                "**/.hg",
                "**/.repo",
                "**/.svn",
                "**/CVS",
                "**/CVSROOT"
            )

            fileList.files should containExactlyInAnyOrder(
                FileList.FileEntry("LICENSE", "356a192b7913b04c54574d18c28d46e6395428ab"),
                FileList.FileEntry("src/cli/main.cpp", "da4b9237bacccdf19c0760cab7aec4a8359010b0")
            )
        }

        "delete the temporary directory even on an exception" {
            val dir = createTempDirWithFiles(".git/index", "LICENSE", "src/cli/main.cpp")
            val resolver = FileListResolver(
                storage = object : ProvenanceFileStorage {
                    override fun hasData(provenance: KnownProvenance) = true
                    override fun getData(provenance: KnownProvenance) = null
                    override fun putData(provenance: KnownProvenance, data: InputStream, size: Long) =
                        throw IOException()
                },
                provenanceDownloader = { dir }
            )

            shouldThrow<IOException> {
                resolver.resolve(ArtifactProvenance(sourceArtifact = RemoteArtifact.EMPTY))
            }

            dir shouldNotBe aDirectory()
        }
    }

    "get()" should {
        "return the requested file list" {
            val resolver = FileListResolver(
                storage = FileProvenanceFileStorage(
                    storage = LocalFileStorage(tempdir()),
                    filename = "bytes"
                ),
                provenanceDownloader = {
                    createTempDirWithFiles(
                        ".git/index",
                        "LICENSE",
                        "src/cli/main.cpp"
                    )
                }
            )

            resolver.resolve(ArtifactProvenance(sourceArtifact = RemoteArtifact.EMPTY))

            val fileList = resolver.get(ArtifactProvenance(sourceArtifact = RemoteArtifact.EMPTY))

            fileList.shouldNotBeNull {
                ignorePatterns should containExactlyInAnyOrder(
                    "**/.bzr",
                    "**/.git",
                    "**/.hg",
                    "**/.repo",
                    "**/.svn",
                    "**/CVS",
                    "**/CVSROOT"
                )

                files should containExactlyInAnyOrder(
                    FileList.FileEntry("LICENSE", "356a192b7913b04c54574d18c28d46e6395428ab"),
                    FileList.FileEntry("src/cli/main.cpp", "da4b9237bacccdf19c0760cab7aec4a8359010b0")
                )
            }
        }

        "return null if no file list is available" {
            val resolver = FileListResolver(
                storage = object : ProvenanceFileStorage {
                    override fun hasData(provenance: KnownProvenance) = false
                    override fun getData(provenance: KnownProvenance) = null
                    override fun putData(provenance: KnownProvenance, data: InputStream, size: Long) =
                        throw IOException()
                },
                provenanceDownloader = { tempfile() }
            )

            resolver.get(ArtifactProvenance(sourceArtifact = RemoteArtifact.EMPTY)) should beNull()
        }
    }
})

private fun Spec.createTempDirWithFiles(vararg paths: String) =
    tempdir().apply {
        paths.forEachIndexed { index, path ->
            resolve(path).apply {
                parentFile.mkdirs()
                writeText("$index")
            }
        }
    }
