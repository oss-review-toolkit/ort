/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.bundler

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.haveSubstring

import java.io.File

import org.ossreviewtoolkit.analyzer.managers.resolveSingleProject
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.patchExpectedResult2
import org.ossreviewtoolkit.utils.test.toYaml

class BundlerFunTest : WordSpec({
    "Bundler" should {
        "resolve dependencies correctly" {
            val definitionFile = getAssetFile("projects/synthetic/lockfile/Gemfile")
            val expectedResultFile = getAssetFile("projects/synthetic/bundler-expected-output-lockfile.yml")

            val actualResult = resolveSingleProject(definitionFile)

            actualResult.toYaml() shouldBe patchExpectedResult2(expectedResultFile, definitionFile)
        }

        "show error if no lockfile is present" {
            val definitionFile = getAssetFile("projects/synthetic/no-lockfile/Gemfile")
            val actualResult = resolveSingleProject(definitionFile)

            with(actualResult) {
                project.id shouldBe
                        Identifier("Bundler::src/funTest/assets/projects/synthetic/no-lockfile/Gemfile:")
                project.definitionFilePath shouldBe
                        "plugins/package-managers/bundler/src/funTest/assets/projects/synthetic/no-lockfile/Gemfile"
                packages should beEmpty()
                issues.size shouldBe 1
                issues.first().message should haveSubstring("IllegalArgumentException: No lockfile found in")
            }
        }

        "resolve dependencies correctly when the project is a Gem" {
            val definitionFile = getAssetFile("projects/synthetic/gemspec/Gemfile")
            val expectedResultFile = getAssetFile("projects/synthetic/bundler-expected-output-gemspec.yml")

            val actualResult = resolveSingleProject(definitionFile)

            actualResult.toYaml() shouldBe patchExpectedResult2(expectedResultFile, definitionFile)
        }
    }
})

private fun resolveSingleProject(definitionFile: File): ProjectAnalyzerResult =
    try {
        val bundler = Bundler("Bundler", USER_DIR, AnalyzerConfiguration(), RepositoryConfiguration())
        bundler.resolveSingleProject(definitionFile)
    } finally {
        definitionFile.resolveSibling(".bundle").safeDeleteRecursively(force = true)
    }
