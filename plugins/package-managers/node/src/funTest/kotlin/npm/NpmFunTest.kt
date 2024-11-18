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

package org.ossreviewtoolkit.plugins.packagemanagers.node.npm

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.inspectors.forAtLeastOne
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import org.ossreviewtoolkit.analyzer.create
import org.ossreviewtoolkit.analyzer.resolveSingleProject
import org.ossreviewtoolkit.analyzer.withInvariantIssues
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.fromYaml
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.matchExpectedResult
import org.ossreviewtoolkit.utils.test.patchActualResult
import org.ossreviewtoolkit.utils.test.patchExpectedResult

class NpmFunTest : WordSpec({
    "NPM" should {
        "resolve dependencies for a project with a 'shrinkwrap.json' correctly" {
            val definitionFile = getAssetFile("projects/synthetic/npm/shrinkwrap/package.json")
            val expectedResultFile = getAssetFile("projects/synthetic/npm/shrinkwrap-expected-output.yml")

            val result = create("NPM").resolveSingleProject(definitionFile, resolveScopes = true)

            patchActualResult(result.toYaml()) should matchExpectedResult(
                expectedResultFile,
                definitionFile,
                custom = mapOf(
                    "<REPLACE_PROJECT_NAME>" to "npm-${definitionFile.parentFile.name}",
                    "<REPLACE_LOCKFILE_NAME>" to "npm-shrinkwrap.json"
                )
            )
        }

        "exclude scopes if configured" {
            val definitionFile = getAssetFile("projects/synthetic/npm/shrinkwrap/package.json")
            val expectedResultFile = getAssetFile(
                "projects/synthetic/npm/shrinkwrap-skip-excluded-scopes-expected-output.yml"
            )

            val result = create("NPM", excludedScopes = setOf("devDependencies"))
                .resolveSingleProject(definitionFile, resolveScopes = true)

            patchActualResult(result.toYaml()) should matchExpectedResult(expectedResultFile, definitionFile)
        }

        "resolve dependencies for a project with lockfile correctly" {
            val definitionFile = getAssetFile("projects/synthetic/npm/project-with-lockfile/package.json")
            val expectedResultFile = getAssetFile("projects/synthetic/npm/project-with-lockfile-expected-output.yml")

            val result = create("NPM").resolveSingleProject(definitionFile, resolveScopes = true)

            patchActualResult(result.toYaml()) should matchExpectedResult(
                expectedResultFile,
                definitionFile,
                custom = mapOf("<REPLACE_LOCKFILE_NAME>" to "package-lock.json")
            )
        }

        "show an error if no lockfile is present" {
            val definitionFile = getAssetFile("projects/synthetic/npm/no-lockfile/package.json")
            val expectedResultFile = getAssetFile("projects/synthetic/npm/no-lockfile-expected-output.yml")

            val result = create("NPM").resolveSingleProject(definitionFile)

            patchActualResult(result.toYaml()) should matchExpectedResult(expectedResultFile, definitionFile)
        }

        "show an error if the 'package.json' file is invalid" {
            val workingDir = tempdir()
            val definitionFile = workingDir.resolve("package.json").apply { writeText("<>") }

            val result = create("NPM", allowDynamicVersions = true).resolveSingleProject(definitionFile)

            result.issues.forAtLeastOne {
                it.source shouldBe "NPM"
                it.severity shouldBe Severity.ERROR
                it.message shouldContain "Unexpected token"
            }
        }

        "resolve dependencies even if the 'node_modules' directory already exists" {
            val definitionFile = getAssetFile("projects/synthetic/npm/node-modules/package.json")
            val expectedResultFile = getAssetFile("projects/synthetic/npm/node-modules-expected-output.yml")

            val result = create("NPM").resolveSingleProject(definitionFile, resolveScopes = true)

            patchActualResult(result.toYaml()) should matchExpectedResult(
                expectedResultFile,
                definitionFile,
                custom = mapOf(
                    "<REPLACE_PROJECT_NAME>" to "npm-${definitionFile.parentFile.name}",
                    "<REPLACE_LOCKFILE_NAME>" to "package-lock.json"
                )
            )
        }

        "resolve Babel dependencies correctly" {
            val definitionFile = getAssetFile("projects/synthetic/npm/babel/package.json")
            val expectedResultFile = getAssetFile("projects/synthetic/npm/babel-expected-output.yml")
            val expectedResult = patchExpectedResult(expectedResultFile, definitionFile)
                .fromYaml<ProjectAnalyzerResult>()

            val result = create("NPM").resolveSingleProject(definitionFile, resolveScopes = true)

            result.withInvariantIssues().toYaml() shouldBe expectedResult.withInvariantIssues().toYaml()
        }

        "resolve dependencies with URLs as versions correctly" {
            val definitionFile = getAssetFile("projects/synthetic/npm/version-urls/package.json")
            val expectedResultFile = getAssetFile("projects/synthetic/npm/version-urls-expected-output.yml")
            val expectedResult = patchExpectedResult(expectedResultFile, definitionFile)
                .fromYaml<ProjectAnalyzerResult>()

            val result = create("NPM", allowDynamicVersions = true)
                .resolveSingleProject(definitionFile, resolveScopes = true)

            result.withInvariantIssues().toYaml() shouldBe expectedResult.withInvariantIssues().toYaml()
        }
    }
})
