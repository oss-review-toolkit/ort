/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.downloader

import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.VcsInfo
import com.here.ort.utils.fileSystemEncode
import com.here.ort.utils.safeDeleteRecursively

import io.kotlintest.TestCase
import io.kotlintest.TestResult
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec

import java.io.File

class DirectoryTest : StringSpec() {
    private lateinit var outputDir: File

    override fun beforeTest(testCase: TestCase) {
        outputDir = createTempDir()
    }

    override fun afterTest(testCase: TestCase, result: TestResult) {
        outputDir.safeDeleteRecursively(force = true)
    }

    init {
        "Creates directories for Gradle submodules" {
            val pkg = Package(
                id = Identifier(
                    type = "type",
                    namespace = "namespace",
                    name = "name",
                    version = "version"
                ),
                declaredLicenses = sortedSetOf(),
                description = "",
                homepageUrl = "",
                binaryArtifact = RemoteArtifact.EMPTY,
                sourceArtifact = RemoteArtifact.EMPTY,
                vcs = VcsInfo("Git", "", "")
            )

            // No download source specified, we expect exception in this case.
            shouldThrow<DownloadException> {
                Downloader().download(pkg, outputDir)
            }

            outputDir.list().size shouldBe 1
            outputDir.list().first() shouldBe pkg.id.type.fileSystemEncode()

            val namespaceDir = File(outputDir, outputDir.list().first())
            namespaceDir.list().size shouldBe 1
            namespaceDir.list().first() shouldBe pkg.id.namespace.fileSystemEncode()

            val nameDir = File(namespaceDir, namespaceDir.list().first())
            nameDir.list().size shouldBe 1
            nameDir.list().first() shouldBe pkg.id.name.fileSystemEncode()

            val versionDir = File(nameDir, nameDir.list().first())
            versionDir.list().size shouldBe 1
            versionDir.list().first() shouldBe pkg.id.version.fileSystemEncode()
        }
    }
}
