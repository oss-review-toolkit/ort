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

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.ORT_NAME
import org.ossreviewtoolkit.utils.safeDeleteRecursively
import org.ossreviewtoolkit.utils.test.shouldNotBeNull

class BeanUtilsFunTest : StringSpec() {
    private lateinit var outputDir: File

    override fun beforeTest(testCase: TestCase) {
        outputDir = createTempDirectory("$ORT_NAME-${javaClass.simpleName}").toFile()
    }

    override fun afterTest(testCase: TestCase, result: TestResult) {
        outputDir.safeDeleteRecursively(force = true)
    }

    init {
        "BeanUtils SVN tag should be correctly downloaded" {
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
                declaredLicenses = sortedSetOf("The Apache Software License, Version 2.0"),
                description = "",
                homepageUrl = "http://commons.apache.org/beanutils/",
                binaryArtifact = RemoteArtifact.EMPTY,
                sourceArtifact = RemoteArtifact.EMPTY,
                vcs = vcsFromCuration
            )

            val provenance = Downloader.download(pkg, outputDir)

            outputDir.walk().onEnter { it.name != ".svn" }.count() shouldBe 302
            provenance.sourceArtifact should beNull()

            provenance.vcsInfo shouldNotBeNull {
                type shouldBe VcsType.SUBVERSION
                url shouldBe vcsFromCuration.url
                revision shouldBe "928490"
                resolvedRevision shouldBe "928490"
                path shouldBe vcsFromCuration.path
            }
        }
    }
}
