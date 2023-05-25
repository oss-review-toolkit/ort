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

import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.model.utils.FileProvenanceFileStorage
import org.ossreviewtoolkit.utils.ort.storage.LocalFileStorage
import org.ossreviewtoolkit.utils.test.createSpecTempDir

class FileListResolverTest : StringSpec({
    "resolve() should create the expected file list" {
        val resolver = FileListResolver(
            storage = FileProvenanceFileStorage(
                storage = LocalFileStorage(createSpecTempDir()),
                filename = "bytes",
            ),
            provenanceDownloader = {
                createTestTempDirWithFiles(
                    ".git/index",
                    "LICENSE",
                    "src/cli/main.cpp"
                )
            },
        )

        val fileList = resolver.resolve(ArtifactProvenance(sourceArtifact = RemoteArtifact.EMPTY))

        fileList.toYaml() shouldBe File("src/test/assets/expected-file-list.yml").readText()
    }
})

private fun Spec.createTestTempDirWithFiles(vararg paths: String) =
    createSpecTempDir().apply {
        paths.forEachIndexed { index, path ->
            resolve(path).apply {
                parentFile.mkdirs()
                writeText("$index")
            }
        }
    }
