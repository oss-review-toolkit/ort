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

import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

import java.io.File

import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.utils.core.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.createTestTempDir
import org.ossreviewtoolkit.utils.test.shouldNotBeNull

class BabelDownloaderFunTest : StringSpec() {
    private lateinit var outputDir: File

    override suspend fun beforeTest(testCase: TestCase) {
        outputDir = createTestTempDir()
    }

    init {
        "Babel packages should be correctly downloaded" {
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

            val provenance = Downloader(DownloaderConfiguration()).download(pkg, outputDir)
            val workingTree = VersionControlSystem.forDirectory(outputDir)
            val babelCliDir = outputDir.resolve("packages/babel-cli")

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
    }
}
