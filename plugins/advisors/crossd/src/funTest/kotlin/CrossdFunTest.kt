/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.advisors.crossd

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldBeSingle
import io.kotest.matchers.maps.beEmpty as beEmptyMap
import io.kotest.matchers.maps.matchExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import io.ktor.client.HttpClient

import io.mockk.coEvery
import io.mockk.mockkStatic

import java.io.IOException

import org.ossreviewtoolkit.clients.crossd.getMetrics
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType

class CrossdFunTest : StringSpec({
    val crossd by lazy { CrossdFactory.create() }

    "A valid package should return findings" {
        val pkg = Package(
            id = Identifier("NPM::babel-code-frame:6.26.0"),
            declaredLicenses = setOf("MIT"),
            description = "Generate errors that contain a code frame that point to source locations.",
            homepageUrl = "https://babeljs.io/",
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = RemoteArtifact.EMPTY,
            vcs = VcsInfo(
                type = VcsType.GIT,
                url = "https://github.com/babel/babel/tree/master/packages/babel-code-frame",
                revision = ""
            ),
            vcsProcessed = VcsInfo(
                type = VcsType.GIT,
                url = "https://github.com/babel/babel.git",
                revision = "master"
            )
        )

        val result = crossd.retrievePackageFindings(setOf(pkg))

        result should matchExactly(
            pkg to {
                it.advisor shouldBe crossd.details
                it.healthMetrics shouldNot beEmpty()
            }
        )
    }

    "An unknown package should return no findings" {
        val pkg = Package(
            id = Identifier("NPM::iojawdoiwajdiowajgdwaoijwadoiwadjwadowadji:1.33.7"),
            declaredLicenses = setOf("MIT"),
            description = "",
            homepageUrl = "",
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = RemoteArtifact.EMPTY,
            vcs = VcsInfo(
                type = VcsType.GIT,
                url = "https://github.com/iojawdoiwajdiowajgdwaoijwadoiwadjwadowadji/" +
                    "test/tree/master/packages/whatever",
                revision = ""
            ),
            vcsProcessed = VcsInfo(
                type = VcsType.GIT,
                url = "https://github.com/iojawdoiwajdiowajgdwaoijwadoiwadjwadowadji/test.git",
                revision = "master"
            )
        )

        val result = crossd.retrievePackageFindings(setOf(pkg))

        result should beEmptyMap()
    }

    "A package with no VCS info should return no findings" {
        val pkg = Package(
            id = Identifier("Maven:org.apache.logging.log4j:log4j-api:2.14.1"),
            declaredLicenses = emptySet(),
            description = "",
            homepageUrl = "",
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = RemoteArtifact.EMPTY,
            vcs = VcsInfo.EMPTY,
            vcsProcessed = VcsInfo.EMPTY
        )

        val result = crossd.retrievePackageFindings(setOf(pkg))

        result should beEmptyMap()
    }

    "A malformed VCS URL should return no findings" {
        val pkg = Package(
            id = Identifier("Maven:org.apache.logging.log4j:log4j-api:2.14.1"),
            declaredLicenses = emptySet(),
            description = "",
            homepageUrl = "",
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = RemoteArtifact.EMPTY,
            vcs = VcsInfo.EMPTY,
            vcsProcessed = VcsInfo(
                type = VcsType.GIT,
                url = "https://invalid-url",
                revision = "ddde192"
            )
        )

        val result = crossd.retrievePackageFindings(setOf(pkg))

        result should beEmptyMap()
    }

    "Issues should only be reported for the affected packages" {
        val validPkg = Package.EMPTY.copy(
            id = Identifier("NPM::babel-code-frame:6.26.0"),
            vcsProcessed = VcsInfo(
                type = VcsType.GIT,
                url = "https://github.com/babel/babel.git",
                revision = "master"
            )
        )
        val invalidPkg = Package.EMPTY.copy(
            id = Identifier("NPM::invalid:1.0.0"),
            vcsProcessed = VcsInfo(
                type = VcsType.GIT,
                url = "https://github.com/invalid/invalid.git",
                revision = "master"
            )
        )

        mockkStatic("org.ossreviewtoolkit.clients.crossd.CrossdKt") {
            coEvery { any<HttpClient>().getMetrics("invalid/invalid") } throws IOException("Simulated failure")

            val result = crossd.retrievePackageFindings(setOf(validPkg, invalidPkg))

            result should matchExactly(
                validPkg to {
                    it.summary.issues should beEmpty()
                    it.healthMetrics shouldNot beEmpty()
                },
                invalidPkg to {
                    it.summary.issues.shouldBeSingle().message shouldBe
                        "Unable to get metrics for 'NPM::invalid:1.0.0': IOException: Simulated failure"
                }
            )
        }
    }
})
