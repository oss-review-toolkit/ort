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

package org.ossreviewtoolkit.plugins.advisors.scorecard

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
import org.ossreviewtoolkit.plugins.advisors.scorecard.Scorecard
import org.ossreviewtoolkit.plugins.advisors.scorecard.ScorecardConfig

class ScorecardTest : StringSpec({
    val scorecard = Scorecard(config = ScorecardConfig(serverUrl = "https://api.securityscorecards.dev"))

    "retrievePackageFindings for a valid package should return findings" {
        val pkg = Package(
            id = Identifier("VCS:oss-review-toolkit:ort:2.5.0"),
            declaredLicenses = emptySet(),
            description = "",
            homepageUrl = "",
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = RemoteArtifact.EMPTY,
            vcs = VcsInfo(
                type = VcsType.GIT,
                url = "https://github.com/oss-review-toolkit/ort.git",
                revision = "ddde192"
            ),
            vcsProcessed = VcsInfo(
                type = VcsType.GIT,
                url = "https://github.com/oss-review-toolkit/ort.git",
                revision = "ddde192"
            )
        )

        val result = scorecard.retrievePackageFindings(setOf(pkg))

        result shouldNotBe null
        result.keys.shouldNotBeEmpty()
        result.values.first().advisor shouldBe scorecard.details
        result.values.first().projectHealth.shouldNotBeEmpty()
    }

    "retrievePackageFindings for a package not known by scorecard should return empty health data" {
        val pkg = Package(
            id = Identifier("VCS:oss-review-toolkit:ort:2.5.0"),
            declaredLicenses = emptySet(),
            description = "",
            homepageUrl = "",
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = RemoteArtifact.EMPTY,
            vcs = VcsInfo.EMPTY,
            vcsProcessed = VcsInfo(
                type = VcsType.GIT,
                // This repository doesn't exist, and it never will because
                // these characters aren't allowed in a GitHub username
                url = "https://github.com/täßt/this-will-never-exist.git",
                revision = "ddde192"
            )
        )

        val result = scorecard.retrievePackageFindings(setOf(pkg))

        result shouldNotBe null
        result.keys shouldBe setOf(pkg)
        result.values.first().advisor shouldBe scorecard.details
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

        val result = scorecard.retrievePackageFindings(setOf(pkg))

        result shouldNotBe null
        result.keys shouldBe setOf(pkg)
        result.values.first().advisor shouldBe scorecard.details
        result.values.first().projectHealth.shouldBeEmpty()
    }

    "retrievePackageFindings for a malformed vcs url should return empty health data" {
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

        val result = scorecard.retrievePackageFindings(setOf(pkg))

        result shouldNotBe null
        result.keys shouldBe setOf(pkg)
        result.values.first().advisor shouldBe scorecard.details
        result.values.first().projectHealth.shouldBeEmpty()
    }
})
