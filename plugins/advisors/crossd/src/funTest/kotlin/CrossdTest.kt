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
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.plugins.advisors.crossd.Crossd
import org.ossreviewtoolkit.plugins.advisors.crossd.CrossdConfig

class CrossdTest : StringSpec({
    val testConfig = CrossdConfig(
        serverUrl = "https://health.crossd.tech",
        thresholdCriticalityLow = 15,
        thresholdCriticalityMedium = 25,
        thresholdCriticalityHigh = 30
    )
    val crossd = Crossd(config = testConfig)

    "retrievePackageFindings for a valid package should return findings" {
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

        result shouldNotBe null
        result.keys.shouldNotBeEmpty()
        result.values.first().advisor shouldBe crossd.details
        result.values.first().projectHealth.shouldNotBeEmpty()
    }

    "retrievePackageFindings for an unknown package should return no findings but not crash" {
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

        result shouldNotBe null
        result.keys shouldBe setOf(pkg)
        result.values.first().advisor shouldBe crossd.details
        result.values.first().projectHealth.shouldBeEmpty()
    }

    "retrievePackageFindings for a package with no vcs should return empty health data" {
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

        result.keys shouldBe setOf(pkg)
        result.values.first().advisor shouldBe crossd.details
        result.values.first().projectHealth.shouldBeEmpty()
    }

    "retrievePackageFindings for a malformed vcs url should return an issue" {
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

        result.keys shouldBe setOf(pkg)
        val issue = result.getValue(pkg).summary.issues.first()
        issue.message shouldBe "The VCS URL 'https://invalid-url' could not be mapped to a repository."
    }
})
