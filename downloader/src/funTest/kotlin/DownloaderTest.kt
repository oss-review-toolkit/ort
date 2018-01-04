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

import com.here.ort.downloader.vcs.Git
import com.here.ort.downloader.vcs.Mercurial
import com.here.ort.model.Package
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.VcsInfo
import com.here.ort.utils.Expensive

import io.kotlintest.Spec
import io.kotlintest.matchers.beGreaterThan
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import io.kotlintest.specs.StringSpec

import java.io.File

class DownloaderTest : StringSpec() {

    private val outputDir = createTempDir()

    override fun interceptSpec(context: Spec, spec: () -> Unit) {
        spec()
        outputDir.deleteRecursively()
    }

    init {
        "Downloads subpath of package at specified revision from git" {
            val submoduleName = "model"
            val revision = "4d842ea6eda2d1a21db161ceb7ceabbcc23d8a85"
            val pkg = Package(
                    packageManager = "Gradle",
                    namespace = "",
                    name = "project :model",
                    version = "",
                    declaredLicenses = sortedSetOf(),
                    description = "",
                    homepageUrl = "",
                    binaryArtifact = RemoteArtifact.EMPTY,
                    sourceArtifact = RemoteArtifact.EMPTY,
                    vcs = VcsInfo("Git", "https://github.com/heremaps/oss-review-toolkit.git", revision, submoduleName)
            )
            Main.download(pkg, outputDir)

            val workingTree = getWorkingTree(Git, pkg)
            val outputDirList = workingTree.workingDir.list()

            workingTree.isValid() shouldBe true
            workingTree.getRevision() shouldBe revision
            outputDirList.indexOf(submoduleName) should beGreaterThan(-1)
            outputDirList.indexOf("downloader") shouldBe -1
        }.config(tags = setOf(Expensive))

        "Downloads whole package from git" {
            val pkg = Package(
                    packageManager = "Gradle",
                    namespace = "",
                    name = "oss-review-toolkit",
                    version = "",
                    declaredLicenses = sortedSetOf(),
                    description = "",
                    homepageUrl = "",
                    binaryArtifact = RemoteArtifact.EMPTY,
                    sourceArtifact = RemoteArtifact.EMPTY,
                    vcs = VcsInfo("Git", "https://github.com/heremaps/oss-review-toolkit.git", "", "")
            )
            Main.download(pkg, outputDir)

            val workingTree = getWorkingTree(Git, pkg)

            workingTree.isValid() shouldBe true
        }.config(tags = setOf(Expensive))

        "Downloads revision for package version from mercurial" {
            val version = "1.1"
            val revisionForVersion = "562fed42b4f3"
            val pkg = Package(
                    packageManager = "",
                    namespace = "",
                    name = "mercurial-xcode-plugin",
                    version = version,
                    declaredLicenses = sortedSetOf(),
                    description = "",
                    homepageUrl = "",
                    binaryArtifact = RemoteArtifact.EMPTY,
                    sourceArtifact = RemoteArtifact.EMPTY,
                    vcs = VcsInfo("Mercurial", "https://bitbucket.org/creaceed/mercurial-xcode-plugin", "", "")
            )
            Main.download(pkg, outputDir)

            val workingTree = getWorkingTree(Mercurial, pkg)

            workingTree.getRevision() shouldBe revisionForVersion
        }.config(tags = setOf(Expensive))

        "Downloads and unpacks JAR source package" {
            val pkg = Package(
                    packageManager = "Gradle",
                    namespace = "junit",
                    name = "junit",
                    version = "4.12",
                    declaredLicenses = sortedSetOf(),
                    description = "",
                    homepageUrl = "",
                    binaryArtifact = RemoteArtifact.EMPTY,
                    sourceArtifact = RemoteArtifact(
                            url = "https://repo.maven.apache.org/maven2/junit/junit/4.12/junit-4.12-sources.jar",
                            hash = "a6c32b40bf3d76eca54e3c601e5d1470c86fcdfa",
                            hashAlgorithm = "SHA-1"
                    ),
                    vcs = VcsInfo.EMPTY
            )

            Main.download(pkg, outputDir)

            val licenseFile = File(outputDir, "LICENSE-junit.txt")
            licenseFile.exists() shouldBe true
            licenseFile.length() shouldBe 11376L

            outputDir.walkTopDown().count() shouldBe 235
        }

        "Download of JAR source package fails when hash is incorrect" {
            val pkg = Package(
                    packageManager = "Gradle",
                    namespace = "junit",
                    name = "junit",
                    version = "4.12",
                    declaredLicenses = sortedSetOf(),
                    description = "",
                    homepageUrl = "",
                    binaryArtifact = RemoteArtifact.EMPTY,
                    sourceArtifact = RemoteArtifact(
                            url = "https://repo.maven.apache.org/maven2/junit/junit/4.12/junit-4.12-sources.jar",
                            hash = "0123456789abcdef0123456789abcdef01234567",
                            hashAlgorithm = "SHA-1"
                    ),
                    vcs = VcsInfo.EMPTY
            )

            val exception = shouldThrow<DownloadException> {
                Main.download(pkg, outputDir)
            }

            exception.message shouldBe "Calculated SHA-1 hash 'a6c32b40bf3d76eca54e3c601e5d1470c86fcdfa' differs " +
                    "from expected hash '0123456789abcdef0123456789abcdef01234567'."
        }
    }

    private fun getWorkingTree(vcs: VersionControlSystem, pkg: Package) =
            vcs.getWorkingTree(File(outputDir, "${pkg.normalizedName}/${pkg.version}"))
}
