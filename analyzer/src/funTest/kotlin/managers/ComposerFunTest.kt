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

package org.ossreviewtoolkit.analyzer.managers

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.haveSubstring

import java.io.File

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import org.ossreviewtoolkit.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.patchExpectedResult

class ComposerFunTest : StringSpec() {
    private val projectsDir = File("src/funTest/assets/projects/synthetic/composer").absoluteFile
    private val vcsDir = VersionControlSystem.forDirectory(projectsDir)!!
    private val vcsRevision = vcsDir.getRevision()
    private val vcsUrl = vcsDir.getRemoteUrl()

    init {
        "Project dependencies are detected correctly" {
            val definitionFile = projectsDir.resolve("lockfile/composer.json")

            val result = createComposer().resolveSingleProject(definitionFile)
            val expectedResults = patchExpectedResult(
                projectsDir.parentFile.resolve("composer-expected-output.yml"),
                url = normalizeVcsUrl(vcsUrl),
                revision = vcsRevision,
                path = vcsDir.getPathToRoot(definitionFile.parentFile)
            )

            result.toYaml() shouldBe expectedResults
        }

        "Error is shown when no lockfile is present" {
            val definitionFile = projectsDir.resolve("no-lockfile/composer.json")
            val result = createComposer().resolveSingleProject(definitionFile)

            with(result) {
                project.id shouldBe Identifier(
                    "Composer::src/funTest/assets/projects/synthetic/" +
                            "composer/no-lockfile/composer.json:"
                )
                project.definitionFilePath shouldBe
                        "analyzer/src/funTest/assets/projects/synthetic/composer/no-lockfile/composer.json"
                packages.size shouldBe 0
                issues.size shouldBe 1
                issues.first().message should haveSubstring("IllegalArgumentException: No lockfile found in")
            }
        }

        "No composer.lock is required for projects without dependencies" {
            val definitionFile = projectsDir.resolve("no-deps/composer.json")

            val result = createComposer().resolveSingleProject(definitionFile)
            val expectedResults = patchExpectedResult(
                projectsDir.parentFile.resolve("composer-expected-output-no-deps.yml"),
                definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                url = normalizeVcsUrl(vcsUrl),
                revision = vcsRevision,
                path = vcsDir.getPathToRoot(definitionFile.parentFile)
            )

            result.toYaml() shouldBe expectedResults
        }

        "No composer.lock is required for projects with empty dependencies" {
            val definitionFile = projectsDir.resolve("empty-deps/composer.json")

            val result = createComposer().resolveSingleProject(definitionFile)
            val expectedResults = patchExpectedResult(
                projectsDir.parentFile.resolve("composer-expected-output-no-deps.yml"),
                definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                url = normalizeVcsUrl(vcsUrl),
                revision = vcsRevision,
                path = vcsDir.getPathToRoot(definitionFile.parentFile)
            )

            result.toYaml() shouldBe expectedResults
        }

        "Packages defined as provided are not reported as missing" {
            val definitionFile = projectsDir.resolve("with-provide/composer.json")

            val result = createComposer().resolveSingleProject(definitionFile)
            val expectedResults = patchExpectedResult(
                projectsDir.parentFile.resolve("composer-expected-output-with-provide.yml"),
                url = normalizeVcsUrl(vcsUrl),
                revision = vcsRevision,
                path = vcsDir.getPathToRoot(definitionFile.parentFile)
            )

            result.toYaml() shouldBe expectedResults
        }

        "Packages defined as replaced are not reported as missing" {
            val definitionFile = projectsDir.resolve("with-replace/composer.json")

            val result = createComposer().resolveSingleProject(definitionFile)
            val expectedResults = patchExpectedResult(
                projectsDir.parentFile.resolve("composer-expected-output-with-replace.yml"),
                url = normalizeVcsUrl(vcsUrl),
                revision = vcsRevision,
                path = vcsDir.getPathToRoot(definitionFile.parentFile)
            )

            result.toYaml() shouldBe expectedResults
        }
    }

    private fun createComposer() =
        Composer("Composer", USER_DIR, DEFAULT_ANALYZER_CONFIGURATION, DEFAULT_REPOSITORY_CONFIGURATION)
}
