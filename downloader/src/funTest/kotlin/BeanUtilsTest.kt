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

import com.here.ort.downloader.vcs.Subversion
import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.VcsInfo
import com.here.ort.utils.safeDeleteRecursively

import io.kotlintest.Description
import io.kotlintest.TestResult
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.StringSpec

import java.io.File

class BeanUtilsTest : StringSpec() {
    private lateinit var outputDir: File

    override fun beforeTest(description: Description) {
        outputDir = createTempDir()
    }

    override fun afterTest(description: Description, result: TestResult) {
        outputDir.safeDeleteRecursively()
    }

    init {
        "BeanUtils SVN tag should be correctly downloaded".config(enabled = Subversion.isInPath()) {
            val vcsFromCuration = VcsInfo(
                    type = "svn",
                    url = "http://svn.apache.org/repos/asf/commons/proper/beanutils",
                    revision = ""
            )

            val pkg = Package(
                    id = Identifier(
                            provider = "Maven",
                            namespace = "commons-beanutils",
                            name = "commons-beanutils-bean-collections",
                            version = "1.8.3"
                    ),
                    declaredLicenses = sortedSetOf("The Apache Software License, Version 2.0"),
                    description = "",
                    homepageUrl = "http://commons.apache.org/beanutils/",
                    binaryArtifact = RemoteArtifact.EMPTY,
                    sourceArtifact = RemoteArtifact.EMPTY,
                    vcs = vcsFromCuration
            )

            val downloadResult = Main.download(pkg, outputDir)
            downloadResult.sourceArtifact shouldBe null
            downloadResult.vcsInfo shouldNotBe null
            downloadResult.vcsInfo!!.type shouldBe "Subversion"
            downloadResult.vcsInfo!!.url shouldBe vcsFromCuration.url
            downloadResult.vcsInfo!!.revision shouldBe "928490"
            downloadResult.vcsInfo!!.resolvedRevision shouldBe "928490"
            downloadResult.vcsInfo!!.path shouldBe vcsFromCuration.path

            val tagsBeanUtils183Dir = File(downloadResult.downloadDirectory, "tags/BEANUTILS_1_8_3")

            tagsBeanUtils183Dir.isDirectory shouldBe true
            tagsBeanUtils183Dir.walkTopDown().count() shouldBe 302

            val workingTree = VersionControlSystem.forDirectory(tagsBeanUtils183Dir)

            workingTree shouldNotBe null
            workingTree!!.isValid() shouldBe true
            workingTree.getRevision() shouldBe "928490"
        }
    }
}
