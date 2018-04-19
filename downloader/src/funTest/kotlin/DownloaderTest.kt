/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

import com.here.ort.model.HashAlgorithm
import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.VcsInfo
import com.here.ort.utils.ExpensiveTag
import com.here.ort.utils.safeDeleteRecursively

import io.kotlintest.TestCaseContext
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldNotBe
import io.kotlintest.matchers.shouldThrow
import io.kotlintest.specs.StringSpec

import java.io.File

class DownloaderTest : StringSpec() {
    private lateinit var outputDir: File

    // Required to make lateinit of outputDir work.
    override val oneInstancePerTest = false

    override fun interceptTestCase(context: TestCaseContext, test: () -> Unit) {
        outputDir = createTempDir()
        try {
            super.interceptTestCase(context, test)
        } finally {
            outputDir.safeDeleteRecursively()
        }
    }

    init {
        "Downloads and unpacks JAR source package" {
            val pkg = Package(
                    id = Identifier(
                            provider = "Maven",
                            namespace = "junit",
                            name = "junit",
                            version = "4.12"
                    ),
                    declaredLicenses = sortedSetOf(),
                    description = "",
                    homepageUrl = "",
                    binaryArtifact = RemoteArtifact.EMPTY,
                    sourceArtifact = RemoteArtifact(
                            url = "https://repo.maven.apache.org/maven2/junit/junit/4.12/junit-4.12-sources.jar",
                            hash = "a6c32b40bf3d76eca54e3c601e5d1470c86fcdfa",
                            hashAlgorithm = HashAlgorithm.SHA1
                    ),
                    vcs = VcsInfo.EMPTY
            )

            val downloadResult = Main.download(pkg, outputDir)
            downloadResult.vcsInfo shouldBe null
            downloadResult.sourceArtifact shouldNotBe null
            downloadResult.sourceArtifact!!.url shouldBe pkg.sourceArtifact.url
            downloadResult.sourceArtifact!!.hash shouldBe pkg.sourceArtifact.hash
            downloadResult.sourceArtifact!!.hashAlgorithm shouldBe pkg.sourceArtifact.hashAlgorithm

            val licenseFile = File(downloadResult.downloadDirectory, "LICENSE-junit.txt")
            licenseFile.isFile shouldBe true
            licenseFile.length() shouldBe 11376L

            downloadResult.downloadDirectory.walkTopDown().count() shouldBe 234
        }.config(tags = setOf(ExpensiveTag))

        "Download of JAR source package fails when hash is incorrect" {
            val pkg = Package(
                    id = Identifier(
                            provider = "Maven",
                            namespace = "junit",
                            name = "junit",
                            version = "4.12"
                    ),
                    declaredLicenses = sortedSetOf(),
                    description = "",
                    homepageUrl = "",
                    binaryArtifact = RemoteArtifact.EMPTY,
                    sourceArtifact = RemoteArtifact(
                            url = "https://repo.maven.apache.org/maven2/junit/junit/4.12/junit-4.12-sources.jar",
                            hash = "0123456789abcdef0123456789abcdef01234567",
                            hashAlgorithm = HashAlgorithm.SHA1
                    ),
                    vcs = VcsInfo.EMPTY
            )

            val exception = shouldThrow<DownloadException> {
                Main.download(pkg, outputDir)
            }

            exception.message shouldBe "Calculated SHA-1 hash 'a6c32b40bf3d76eca54e3c601e5d1470c86fcdfa' differs " +
                    "from expected hash '0123456789abcdef0123456789abcdef01234567'."
        }.config(tags = setOf(ExpensiveTag))

        "Falls back to downloading source package when download from VCS fails" {
            val pkg = Package(
                    id = Identifier(
                            provider = "Maven",
                            namespace = "junit",
                            name = "junit",
                            version = "4.12"
                    ),
                    declaredLicenses = sortedSetOf(),
                    description = "",
                    homepageUrl = "",
                    binaryArtifact = RemoteArtifact.EMPTY,
                    sourceArtifact = RemoteArtifact(
                            url = "https://repo.maven.apache.org/maven2/junit/junit/4.12/junit-4.12-sources.jar",
                            hash = "a6c32b40bf3d76eca54e3c601e5d1470c86fcdfa",
                            hashAlgorithm = HashAlgorithm.SHA1
                    ),
                    vcs = VcsInfo(
                            type = "Git",
                            url = "https://example.com/invalid-repo-url",
                            revision = "8964880d9bac33f0a7f030a74c7c9299a8f117c8"
                    )
            )

            val downloadResult = Main.download(pkg, outputDir)
            downloadResult.vcsInfo shouldBe null
            downloadResult.sourceArtifact shouldNotBe null
            downloadResult.sourceArtifact!!.url shouldBe pkg.sourceArtifact.url
            downloadResult.sourceArtifact!!.hash shouldBe pkg.sourceArtifact.hash
            downloadResult.sourceArtifact!!.hashAlgorithm shouldBe pkg.sourceArtifact.hashAlgorithm

            val licenseFile = File(downloadResult.downloadDirectory, "LICENSE-junit.txt")
            licenseFile.isFile shouldBe true
            licenseFile.length() shouldBe 11376L

            downloadResult.downloadDirectory.walkTopDown().count() shouldBe 234
        }.config(tags = setOf(ExpensiveTag))

        "Can download source artifact from SourceForce" {
            val url = "https://master.dl.sourceforge.net/project/tyrex/tyrex/Tyrex%201.0.1/tyrex-1.0.1-src.tgz"
            val pkg = Package(
                    id = Identifier(
                            provider = "Maven",
                            namespace = "tyrex",
                            name = "tyrex",
                            version = "1.0.1"
                    ),
                    declaredLicenses = sortedSetOf(),
                    description = "",
                    homepageUrl = "",
                    binaryArtifact = RemoteArtifact.EMPTY,
                    sourceArtifact = RemoteArtifact(
                            url = url,
                            hash = "49fe486f44197c8e5106ed7487526f77b597308f",
                            hashAlgorithm = HashAlgorithm.SHA1
                    ),
                    vcs = VcsInfo.EMPTY
            )

            val downloadResult = Main.download(pkg, outputDir)
            downloadResult.vcsInfo shouldBe null
            downloadResult.sourceArtifact shouldNotBe null
            downloadResult.sourceArtifact!!.url shouldBe pkg.sourceArtifact.url
            downloadResult.sourceArtifact!!.hash shouldBe pkg.sourceArtifact.hash
            downloadResult.sourceArtifact!!.hashAlgorithm shouldBe pkg.sourceArtifact.hashAlgorithm

            val tyrexDir = File(downloadResult.downloadDirectory, "tyrex-1.0.1")

            tyrexDir.isDirectory shouldBe true
            tyrexDir.walkTopDown().count() shouldBe 409
        }.config(tags = setOf(ExpensiveTag))
    }
}
