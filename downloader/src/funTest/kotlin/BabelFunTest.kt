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

package org.ossreviewtoolkit.downloader

import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File

import kotlin.io.path.createTempDirectory

import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.ORT_NAME
import org.ossreviewtoolkit.utils.normalizeVcsUrl
import org.ossreviewtoolkit.utils.safeDeleteRecursively
import org.ossreviewtoolkit.utils.test.shouldNotBeNull

class BabelFunTest : StringSpec() {
    private lateinit var outputDir: File

    override fun beforeTest(testCase: TestCase) {
        outputDir = createTempDirectory("$ORT_NAME-${javaClass.simpleName}").toFile()
    }

    override fun afterTest(testCase: TestCase, result: TestResult) {
        outputDir.safeDeleteRecursively(force = true)
    }

    init {
        "Babel packages should be correctly downloaded" {
            val vcsFromPackage = VcsInfo(
                type = VcsType.GIT,
                url = "https://github.com/babel/babel/tree/master/packages/babel-cli",
                revision = ""
            )
            val vcsFromUrl = VcsHost.toVcsInfo(normalizeVcsUrl(vcsFromPackage.url))
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
                    hash = Hash.create("502ab54874d7db88ad00b887a06383ce03d002f1")
                ),
                sourceArtifact = RemoteArtifact.EMPTY,
                vcs = vcsFromPackage,
                vcsProcessed = vcsMerged
            )

            val downloadResult = Downloader.download(pkg, outputDir)

            downloadResult.sourceArtifact should beNull()
            downloadResult.vcsInfo shouldNotBeNull {
                type shouldBe pkg.vcsProcessed.type
                url shouldBe pkg.vcsProcessed.url
                revision shouldBe "master"
                resolvedRevision shouldBe "cee4cde53e4f452d89229986b9368ecdb41e00da"
                path shouldBe pkg.vcsProcessed.path
            }

            val workingTree = VersionControlSystem.forDirectory(outputDir)

            workingTree shouldNotBeNull {
                isValid() shouldBe true
                getRevision() shouldBe "cee4cde53e4f452d89229986b9368ecdb41e00da"
            }

            val babelCliDir = outputDir.resolve("packages/babel-cli")
            babelCliDir.isDirectory shouldBe true
            babelCliDir.walk().count() shouldBe 242
        }
    }
}
