/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.downloader

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.matchers.file.aFile
import io.kotest.matchers.file.haveFileSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

import java.io.File

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.SourceCodeOrigin
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.utils.test.ExpensiveTag
import org.ossreviewtoolkit.utils.test.createTestTempDir

class DownloaderFunTest : StringSpec() {
    private lateinit var outputDir: File

    override suspend fun beforeTest(testCase: TestCase) {
        outputDir = createTestTempDir()
    }

    init {
        "Downloads and unpacks JAR source package".config(tags = setOf(ExpensiveTag)) {
            val pkg = Package(
                id = Identifier(
                    type = "Maven",
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
                    hash = Hash.create("a6c32b40bf3d76eca54e3c601e5d1470c86fcdfa")
                ),
                vcs = VcsInfo.EMPTY
            )

            val provenance = Downloader(DownloaderConfiguration()).download(pkg, outputDir)
            val licenseFile = outputDir.resolve("LICENSE-junit.txt")

            provenance.shouldBeTypeOf<ArtifactProvenance>().apply {
                sourceArtifact.url shouldBe pkg.sourceArtifact.url
                sourceArtifact.hash shouldBe pkg.sourceArtifact.hash
            }

            licenseFile shouldBe aFile()
            licenseFile should haveFileSize(11376L)

            outputDir.walk().count() shouldBe 234
        }

        "Download of JAR source package fails when hash is incorrect".config(tags = setOf(ExpensiveTag)) {
            val pkg = Package(
                id = Identifier(
                    type = "Maven",
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
                    hash = Hash.create("0123456789abcdef0123456789abcdef01234567")
                ),
                vcs = VcsInfo.EMPTY
            )

            val exception = shouldThrow<DownloadException> {
                Downloader(DownloaderConfiguration()).download(pkg, outputDir)
            }

            exception.suppressed.size shouldBe 2
            exception.suppressed[0]!!.message shouldBe "No VCS URL provided for 'Maven:junit:junit:4.12'. " +
                    "Please define the \"connection\" tag within the \"scm\" tag in the POM file, " +
                    "see: http://maven.apache.org/pom.html#SCM"
            exception.suppressed[1]!!.message shouldBe "Source artifact does not match expected " +
                    "Hash(value=0123456789abcdef0123456789abcdef01234567, algorithm=SHA1)."
        }

        "Falls back to downloading source package when download from VCS fails".config(tags = setOf(ExpensiveTag)) {
            val downloaderConfiguration = DownloaderConfiguration(
                sourceCodeOrigins = listOf(SourceCodeOrigin.VCS, SourceCodeOrigin.ARTIFACT)
            )

            val pkg = Package(
                id = Identifier(
                    type = "Maven",
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
                    hash = Hash.create("a6c32b40bf3d76eca54e3c601e5d1470c86fcdfa")
                ),
                vcs = VcsInfo(
                    type = VcsType.GIT,
                    url = "https://example.com/invalid-repo-url",
                    revision = "8964880d9bac33f0a7f030a74c7c9299a8f117c8"
                )
            )

            val provenance = Downloader(downloaderConfiguration).download(pkg, outputDir)
            val licenseFile = outputDir.resolve("LICENSE-junit.txt")

            provenance.shouldBeTypeOf<ArtifactProvenance>().apply {
                sourceArtifact.url shouldBe pkg.sourceArtifact.url
                sourceArtifact.hash shouldBe pkg.sourceArtifact.hash
            }

            licenseFile shouldBe aFile()
            licenseFile should haveFileSize(11376L)

            outputDir.walk().count() shouldBe 234
        }

        "Falls back to downloading from VCS when source package download fails".config(tags = setOf(ExpensiveTag)) {
            val downloaderConfiguration = DownloaderConfiguration(
                sourceCodeOrigins = listOf(SourceCodeOrigin.ARTIFACT, SourceCodeOrigin.VCS)
            )

            val pkg = Package(
                id = Identifier(
                    type = "Maven",
                    namespace = "junit",
                    name = "junit",
                    version = "4.12"
                ),
                declaredLicenses = sortedSetOf(),
                description = "",
                homepageUrl = "",
                binaryArtifact = RemoteArtifact.EMPTY,
                sourceArtifact = RemoteArtifact(
                    url = "https://repo.example.com/invalid.jar",
                    hash = Hash.create("a6c32b40bf3d76eca54e3c601e5d1470c86fcdfa")
                ),
                vcs = VcsInfo(
                    type = VcsType.GIT,
                    url = "https://github.com/junit-team/junit4.git",
                    revision = "64155f8a9babcfcf4263cf4d08253a1556e75481"
                )
            )

            val provenance = Downloader(downloaderConfiguration).download(pkg, outputDir)
            val licenseFile = outputDir.resolve("LICENSE-junit.txt")

            provenance.shouldBeTypeOf<RepositoryProvenance>().apply {
                vcsInfo.url shouldBe pkg.vcs.url
                vcsInfo.revision shouldBe pkg.vcs.revision
            }

            licenseFile shouldBe aFile()
            licenseFile should haveFileSize(11376L)

            outputDir.walk().count() shouldBe 608
        }

        "Can download a TGZ source artifact from SourceForge".config(tags = setOf(ExpensiveTag)) {
            val artifactUrl = "https://master.dl.sourceforge.net/project/tyrex/tyrex/Tyrex%201.0.1/tyrex-1.0.1-src.tgz"
            val pkg = Package(
                id = Identifier(
                    type = "Maven",
                    namespace = "tyrex",
                    name = "tyrex",
                    version = "1.0.1"
                ),
                declaredLicenses = sortedSetOf(),
                description = "",
                homepageUrl = "",
                binaryArtifact = RemoteArtifact.EMPTY,
                sourceArtifact = RemoteArtifact(
                    url = artifactUrl,
                    hash = Hash.create("49fe486f44197c8e5106ed7487526f77b597308f")
                ),
                vcs = VcsInfo.EMPTY
            )

            val provenance = Downloader(DownloaderConfiguration()).download(pkg, outputDir)
            val tyrexDir = outputDir.resolve("tyrex-1.0.1")

            provenance.shouldBeTypeOf<ArtifactProvenance>().apply {
                sourceArtifact.url shouldBe pkg.sourceArtifact.url
                sourceArtifact.hash shouldBe pkg.sourceArtifact.hash
            }

            tyrexDir.isDirectory shouldBe true
            tyrexDir.walk().count() shouldBe 409
        }

        "Can download a ZIP source artifact from GitHub".config(tags = setOf(ExpensiveTag)) {
            val artifactUrl = "https://github.com/microsoft/tslib/archive/1.10.0.zip"
            val pkg = Package(
                id = Identifier(
                    type = "NPM",
                    namespace = "",
                    name = "tslib",
                    version = "1.10.0"
                ),
                declaredLicenses = sortedSetOf(),
                description = "",
                homepageUrl = "",
                binaryArtifact = RemoteArtifact.EMPTY,
                sourceArtifact = RemoteArtifact(
                    url = artifactUrl,
                    hash = Hash.create("7f7994408f130dd138a59a625eeef3be1ab40f7b")
                ),
                vcs = VcsInfo.EMPTY
            )

            val provenance = Downloader(DownloaderConfiguration()).download(pkg, outputDir)
            val tslibDir = outputDir.resolve("tslib-1.10.0")

            provenance.shouldBeTypeOf<ArtifactProvenance>().apply {
                sourceArtifact.url shouldBe pkg.sourceArtifact.url
                sourceArtifact.hash shouldBe pkg.sourceArtifact.hash
            }

            tslibDir.isDirectory shouldBe true
            tslibDir.walk().count() shouldBe 16
        }
    }
}
