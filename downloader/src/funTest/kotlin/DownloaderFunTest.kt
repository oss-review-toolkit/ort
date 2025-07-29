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

package org.ossreviewtoolkit.downloader

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.file.aFile
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.endWith
import io.kotest.matchers.string.startWith
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
import org.ossreviewtoolkit.utils.common.VCS_DIRECTORIES
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl

class DownloaderFunTest : WordSpec({
    lateinit var outputDir: File

    beforeTest {
        outputDir = tempdir()
    }

    "A source artifact download" should {
        "succeed for ZIP archives from GitHub" {
            val artifactUrl = "https://github.com/microsoft/tslib/archive/1.10.0.zip"
            val pkg = Package(
                id = Identifier(
                    type = "NPM",
                    namespace = "",
                    name = "tslib",
                    version = "1.10.0"
                ),
                declaredLicenses = emptySet(),
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
            val tslibDir = outputDir / "tslib-1.10.0"

            provenance.shouldBeTypeOf<ArtifactProvenance>().apply {
                sourceArtifact.url shouldBe pkg.sourceArtifact.url
                sourceArtifact.hash shouldBe pkg.sourceArtifact.hash
            }

            tslibDir.isDirectory shouldBe true
            tslibDir.walk().count() shouldBe 16
        }

        "succeed for TGZ archives from SourceForge" {
            val artifactUrl = "https://master.dl.sourceforge.net/project/tyrex/tyrex/Tyrex%201.0.1/tyrex-1.0.1-src.tgz"
            val pkg = Package(
                id = Identifier(
                    type = "Maven",
                    namespace = "tyrex",
                    name = "tyrex",
                    version = "1.0.1"
                ),
                declaredLicenses = emptySet(),
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
            val tyrexDir = outputDir / "tyrex-1.0.1"

            provenance.shouldBeTypeOf<ArtifactProvenance>().apply {
                sourceArtifact.url shouldBe pkg.sourceArtifact.url
                sourceArtifact.hash shouldBe pkg.sourceArtifact.hash
            }

            tyrexDir.isDirectory shouldBe true
            tyrexDir.walk().count() shouldBe 409
        }

        "succeed for sources JAR artifacts" {
            val pkg = Package(
                id = Identifier(
                    type = "Maven",
                    namespace = "junit",
                    name = "junit",
                    version = "4.12"
                ),
                declaredLicenses = emptySet(),
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
            val licenseFile = outputDir / "LICENSE-junit.txt"

            provenance.shouldBeTypeOf<ArtifactProvenance>().apply {
                sourceArtifact.url shouldBe pkg.sourceArtifact.url
                sourceArtifact.hash shouldBe pkg.sourceArtifact.hash
            }

            licenseFile shouldBe aFile()

            with(licenseFile.readText().trim()) {
                this should startWith("JUnit")
                this should endWith("in any resulting litigation.")
            }

            outputDir.walk().count() shouldBe 234
        }

        "fail for sources JAR artifacts with an incorrect hash" {
            val pkg = Package(
                id = Identifier(
                    type = "Maven",
                    namespace = "junit",
                    name = "junit",
                    version = "4.12"
                ),
                declaredLicenses = emptySet(),
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

            exception.suppressed shouldHaveSize 2
            exception.suppressed[0]!!.message shouldBe "No VCS URL provided for 'Maven:junit:junit:4.12'. " +
                "Please define the \"connection\" tag within the \"scm\" tag in the POM file, " +
                "see: https://maven.apache.org/pom.html#SCM"
            exception.suppressed[1]!!.message shouldBe "Source artifact does not match expected " +
                "Hash(value=0123456789abcdef0123456789abcdef01234567, algorithm=SHA-1)."
        }

        "should be tried as a fallback when the download from VCS fails" {
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
                declaredLicenses = emptySet(),
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
            val licenseFile = outputDir / "LICENSE-junit.txt"

            provenance.shouldBeTypeOf<ArtifactProvenance>().apply {
                sourceArtifact.url shouldBe pkg.sourceArtifact.url
                sourceArtifact.hash shouldBe pkg.sourceArtifact.hash
            }

            licenseFile shouldBe aFile()

            with(licenseFile.readText().trim()) {
                this should startWith("JUnit")
                this should endWith("in any resulting litigation.")
            }

            outputDir.walk().count() shouldBe 234
        }
    }

    "A VCS download" should {
        "succeed for the Babel project hosted in Git" {
            val vcsFromPackage = VcsInfo(
                type = VcsType.GIT,
                url = "https://github.com/babel/babel/tree/master/packages/babel-cli",
                revision = ""
            )
            val vcsFromUrl = VcsHost.parseUrl(normalizeVcsUrl(vcsFromPackage.url))
            val vcsMerged = vcsFromUrl.merge(vcsFromPackage)

            val pkg = Package(
                id = Identifier(
                    type = "NPM",
                    namespace = "",
                    name = "babel-cli",
                    version = "6.26.0"
                ),
                declaredLicenses = setOf("MIT"),
                description = "Babel command line.",
                homepageUrl = "https://babeljs.io/",
                binaryArtifact = RemoteArtifact(
                    url = "https://registry.npmjs.org/babel-cli/-/babel-cli-6.26.0.tgz",
                    hash = Hash.create("502ab54874d7db88ad00b887a06383ce03d002f1")
                ),
                sourceArtifact = RemoteArtifact.EMPTY,
                vcs = vcsFromPackage,
                vcsProcessed = vcsMerged
            )

            val provenance = Downloader(DownloaderConfiguration()).download(pkg, outputDir)
            val workingTree = VersionControlSystem.forDirectory(outputDir)
            val babelCliDir = outputDir / "packages" / "babel-cli"

            provenance.shouldBeTypeOf<RepositoryProvenance>().apply {
                vcsInfo.type shouldBe pkg.vcsProcessed.type
                vcsInfo.url shouldBe pkg.vcsProcessed.url
                vcsInfo.revision shouldBe "master"
                vcsInfo.path shouldBe pkg.vcsProcessed.path
                resolvedRevision shouldBe "cee4cde53e4f452d89229986b9368ecdb41e00da"
            }

            workingTree shouldNotBeNull {
                isValid() shouldBe true
                getRevision() shouldBe "cee4cde53e4f452d89229986b9368ecdb41e00da"
            }

            babelCliDir.isDirectory shouldBe true
            babelCliDir.walk().count() shouldBe 242
        }

        "succeed for the BeanUtils project hosted in Subversion" {
            val vcsFromCuration = VcsInfo(
                type = VcsType.SUBVERSION,
                url = "https://svn.apache.org/repos/asf/commons/_moved_to_git/beanutils",
                revision = ""
            )

            val pkg = Package(
                id = Identifier(
                    type = "Maven",
                    namespace = "commons-beanutils",
                    name = "commons-beanutils-bean-collections",
                    version = "1.8.3"
                ),
                declaredLicenses = setOf("The Apache Software License, Version 2.0"),
                description = "",
                homepageUrl = "http://commons.apache.org/beanutils/",
                binaryArtifact = RemoteArtifact.EMPTY,
                sourceArtifact = RemoteArtifact.EMPTY,
                vcs = vcsFromCuration
            )

            val provenance = Downloader(DownloaderConfiguration()).download(pkg, outputDir)

            outputDir.walk().onEnter { it.name !in VCS_DIRECTORIES }.count() shouldBe 302

            provenance.shouldBeTypeOf<RepositoryProvenance>().apply {
                vcsInfo.type shouldBe VcsType.SUBVERSION
                vcsInfo.url shouldBe vcsFromCuration.url
                vcsInfo.revision shouldBe ""
                vcsInfo.path shouldBe vcsFromCuration.path
                resolvedRevision shouldBe "928490"
            }
        }

        "be tried as a fallback when the source artifact download fails" {
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
                declaredLicenses = emptySet(),
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
            val licenseFile = outputDir / "LICENSE-junit.txt"

            provenance.shouldBeTypeOf<RepositoryProvenance>().apply {
                vcsInfo.url shouldBe pkg.vcs.url
                vcsInfo.revision shouldBe pkg.vcs.revision
            }

            licenseFile shouldBe aFile()

            with(licenseFile.readText().trim()) {
                this should startWith("JUnit")
                this should endWith("in any resulting litigation.")
            }

            outputDir.walk().onEnter { it.name !in VCS_DIRECTORIES }.count() shouldBe 588
        }
    }
})
