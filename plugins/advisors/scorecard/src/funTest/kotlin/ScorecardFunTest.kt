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
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.maps.matchExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType

class ScorecardFunTest : StringSpec({
    val scorecard by lazy { ScorecardFactory.create() }

    "retrievePackageFindings for a valid package should return findings" {
        val vcs = VcsInfo(
            type = VcsType.GIT,
            url = "https://github.com/oss-review-toolkit/ort.git",
            revision = "dbd50cd755093305319ca85f9aa2a3f5d20320fe"
        )
        val pkg = Package.EMPTY.copy(
            id = Identifier("Maven:org.ossreviewtoolkit:cli:99.0.0"),
            vcs = vcs,
            vcsProcessed = vcs
        )

        val findings = scorecard.retrievePackageFindings(setOf(pkg))

        findings should matchExactly(
            pkg to { result ->
                result.advisor shouldBe scorecard.details
                result.healthMetrics shouldNot beEmpty()
            }
        )
    }

    "retrievePackageFindings for a package not known by scorecard should return empty health data" {
        val nonExistingVcs = VcsInfo(
            type = VcsType.GIT,
            // This repository does not exist and never will, because its URL contains disallowed characters.
            url = "https://github.com/täßt/this-will-never-exist.git",
            revision = "dbd50cd755093305319ca85f9aa2a3f5d20320fe"
        )
        val pkg = Package.EMPTY.copy(
            id = Identifier("Maven:org.ossreviewtoolkit:cli:99.0.0"),
            vcs = nonExistingVcs,
            vcsProcessed = nonExistingVcs
        )

        val findings = scorecard.retrievePackageFindings(setOf(pkg))

        findings should matchExactly(
            pkg to { result ->
                result.advisor shouldBe scorecard.details
                result.healthMetrics should beEmpty()
            }
        )
    }

    "retrievePackageFindings for a package with no vcs should return empty health data" {
        val pkg = Package.EMPTY.copy(
            id = Identifier("Maven:org.apache.logging.log4j:log4j-api:2.14.1")
        )

        val findings = scorecard.retrievePackageFindings(setOf(pkg))

        findings should matchExactly(
            pkg to { result ->
                result.advisor shouldBe scorecard.details
                result.healthMetrics should beEmpty()
            }
        )
    }

    "retrievePackageFindings for a malformed vcs url should return empty health data" {
        val invalidVcs = VcsInfo(
            type = VcsType.GIT,
            url = "https://invalid-url",
            revision = HashAlgorithm.SHA1.emptyValue
        )
        val pkg = Package.EMPTY.copy(
            id = Identifier("Maven:org.apache.logging.log4j:log4j-api:2.14.1"),
            vcs = invalidVcs,
            vcsProcessed = invalidVcs
        )

        val findings = scorecard.retrievePackageFindings(setOf(pkg))

        findings should matchExactly(
            pkg to { result ->
                result.advisor shouldBe scorecard.details
                result.healthMetrics should beEmpty()
            }
        )
    }
})
