/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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
import com.here.ort.utils.normalizeVcsUrl
import com.here.ort.utils.safeDeleteRecursively

import io.kotlintest.Description
import io.kotlintest.TestResult
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.StringSpec

import java.io.File

class BabelTest : StringSpec() {
    private lateinit var outputDir: File

    override fun beforeTest(description: Description) {
        outputDir = createTempDir()
    }

    override fun afterTest(description: Description, result: TestResult) {
        outputDir.safeDeleteRecursively(true)
    }

    init {
        "Babel packages should be correctly downloaded" {
            val vcsFromPackage = VcsInfo(
                    type = "git",
                    url = "https://github.com/babel/babel/tree/master/packages/babel-cli",
                    revision = ""
            )
            val vcsFromUrl = VersionControlSystem.splitUrl(normalizeVcsUrl(vcsFromPackage.url))
            val vcsMerged = vcsFromUrl.merge(vcsFromPackage)

            val pkg = Package(
                    id = Identifier(
                            type = "NPM",
                            namespace = "",
                            name = "babel-cli",
                            version = "6.26.0"
                    ),
                    declaredLicenses = sortedSetOf("MIT"),
                    description = "Babel command line.",
                    homepageUrl = "https://babeljs.io/",
                    binaryArtifact = RemoteArtifact(
                            url = "https://registry.npmjs.org/babel-cli/-/babel-cli-6.26.0.tgz",
                            hash = "502ab54874d7db88ad00b887a06383ce03d002f1",
                            hashAlgorithm = HashAlgorithm.SHA1
                    ),
                    sourceArtifact = RemoteArtifact.EMPTY,
                    vcs = vcsFromPackage,
                    vcsProcessed = vcsMerged
            )

            val downloadResult = Downloader().download(pkg, outputDir)

            downloadResult.sourceArtifact shouldBe null
            downloadResult.vcsInfo shouldNotBe null
            downloadResult.vcsInfo!!.type.toLowerCase() shouldBe pkg.vcsProcessed.type
            downloadResult.vcsInfo!!.url shouldBe pkg.vcsProcessed.url
            downloadResult.vcsInfo!!.revision shouldBe "master"
            downloadResult.vcsInfo!!.resolvedRevision shouldBe "cee4cde53e4f452d89229986b9368ecdb41e00da"
            downloadResult.vcsInfo!!.path shouldBe pkg.vcsProcessed.path

            val workingTree = VersionControlSystem.forDirectory(downloadResult.downloadDirectory)

            workingTree shouldNotBe null
            workingTree!!.isValid() shouldBe true
            workingTree.getRevision() shouldBe "cee4cde53e4f452d89229986b9368ecdb41e00da"

            val babelCliDir = File(downloadResult.downloadDirectory, "packages/babel-cli")
            babelCliDir.isDirectory shouldBe true
            babelCliDir.walkTopDown().count() shouldBe 242
        }
    }
}
