/*
 * Copyright (c) 2017 HERE Europe B.V.
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

import io.kotlintest.Spec
import io.kotlintest.matchers.beGreaterThan
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
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
                    binaryArtifact = RemoteArtifact.createEmpty(),
                    sourceArtifact = RemoteArtifact.createEmpty(),
                    vcsProvider = "Git",
                    vcsUrl = "https://github.com/heremaps/oss-review-toolkit.git",
                    vcsRevision = revision,
                    vcsPath = submoduleName
            )
            Main.download(pkg, outputDir)

            val workingDirectory = getWorkingDir(Git, pkg)
            val outputDirList = workingDirectory.workingDir.list()

            workingDirectory.isValid() shouldBe true
            workingDirectory.getRevision() shouldBe revision
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
                    binaryArtifact = RemoteArtifact.createEmpty(),
                    sourceArtifact = RemoteArtifact.createEmpty(),
                    vcsProvider = "Git",
                    vcsUrl = "https://github.com/heremaps/oss-review-toolkit.git",
                    vcsRevision = "",
                    vcsPath = ""
            )
            Main.download(pkg, outputDir)

            val workingDirectory = getWorkingDir(Git, pkg)

            workingDirectory.isValid() shouldBe true

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
                    binaryArtifact = RemoteArtifact.createEmpty(),
                    sourceArtifact = RemoteArtifact.createEmpty(),
                    vcsProvider = "Mercurial",
                    vcsUrl = "https://bitbucket.org/creaceed/mercurial-xcode-plugin",
                    vcsRevision = "",
                    vcsPath = ""
            )
            Main.download(pkg, outputDir)

            val workingDir = getWorkingDir(Mercurial, pkg)

            workingDir.getRevision() shouldBe revisionForVersion
        }.config(tags = setOf(Expensive))
    }

    private fun getWorkingDir(vcs: VersionControlSystem, pkg: Package)
            = vcs.getWorkingDirectory(File(outputDir, "${pkg.normalizedName}/${pkg.version}"))

}
