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

package org.ossreviewtoolkit.analyzer.managers

import com.fasterxml.jackson.module.kotlin.readValue

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import java.io.File

import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.ScopeExclude
import org.ossreviewtoolkit.model.config.ScopeExcludeReason
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.createTestTempDir
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.patchActualResult
import org.ossreviewtoolkit.utils.test.patchExpectedResult2
import org.ossreviewtoolkit.utils.test.toYaml

class NpmFunTest : WordSpec({
    "NPM" should {
        "resolve shrinkwrap dependencies correctly" {
            val definitionFile = getAssetFile("projects/synthetic/npm/shrinkwrap/package.json")
            val expectedResultFile = getAssetFile("projects/synthetic/npm-expected-output.yml")

            val result = resolveSingleProject(definitionFile, resolveScopes = true)

            patchActualResult(result.toYaml()) shouldBe patchExpectedResult2(
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
            val expectedResultFile = getAssetFile("projects/synthetic/npm-expected-output-scope-excludes.yml")

            val result = resolveSingleProject(
                definitionFile,
                excludedScopes = setOf("devDependencies"),
                resolveScopes = true
            )

            patchActualResult(result.toYaml()) shouldBe patchExpectedResult2(
                expectedResultFile,
                definitionFile,
                custom = mapOf("<REPLACE_LOCKFILE_NAME>" to "npm-shrinkwrap.json")
            )
        }

        "resolve package-lock dependencies correctly" {
            val definitionFile = getAssetFile("projects/synthetic/npm/package-lock/package.json")
            val expectedResultFile = getAssetFile("projects/synthetic/npm-expected-output.yml")

            val result = resolveSingleProject(definitionFile, resolveScopes = true)

            patchActualResult(result.toYaml()) shouldBe patchExpectedResult2(
                expectedResultFile,
                definitionFile,
                custom = mapOf(
                    "<REPLACE_PROJECT_NAME>" to "npm-${definitionFile.parentFile.name}",
                    "<REPLACE_LOCKFILE_NAME>" to "package-lock.json"
                )
            )
        }

        "show an error if no lockfile is present" {
            val definitionFile = getAssetFile("projects/synthetic/npm/no-lockfile/package.json")
            val expectedResultFile = getAssetFile("projects/synthetic/npm-expected-output-no-lockfile.yml")

            val result = resolveSingleProject(definitionFile)

            patchActualResult(result.toYaml()) shouldBe patchExpectedResult2(expectedResultFile, definitionFile)
        }

        "show an error if the 'package.json' file is invalid" {
            val workingDir = createTestTempDir()
            val definitionFile = workingDir.resolve("package.json").apply { writeText("<>") }

            val result = resolveSingleProject(definitionFile, allowDynamicVersions = true)

            result.issues shouldHaveSize 1
            with(result.issues.first()) {
                source shouldBe "NPM"
                severity shouldBe Severity.ERROR
                message shouldContain "Unexpected token \"<\" (0x3C) in JSON at position 0 while parsing \"<>\""
            }
        }

        "resolve dependencies even if the 'node_modules' directory already exists" {
            val definitionFile = getAssetFile("projects/synthetic/npm/node-modules/package.json")
            val expectedResultFile = getAssetFile("projects/synthetic/npm-expected-output.yml")

            val result = resolveSingleProject(definitionFile, resolveScopes = true)

            patchActualResult(result.toYaml()) shouldBe patchExpectedResult2(
                expectedResultFile,
                definitionFile,
                custom = mapOf(
                    "<REPLACE_PROJECT_NAME>" to "npm-${definitionFile.parentFile.name}",
                    "<REPLACE_LOCKFILE_NAME>" to "package-lock.json"
                )
            )
        }

        "resolve Babel dependencies correctly" {
            val definitionFile = getAssetFile("projects/synthetic/npm-babel/package.json")
            val expectedResultFile = getAssetFile("projects/synthetic/npm-babel-expected-output.yml")
            val expectedResult = patchExpectedResult2(expectedResultFile, definitionFile).let {
                yamlMapper.readValue<ProjectAnalyzerResult>(it)
            }

            val actualResult = resolveSingleProject(definitionFile, resolveScopes = true)

            actualResult.withInvariantIssues() shouldBe expectedResult.withInvariantIssues()
        }
    }
})

private fun resolveSingleProject(
    definitionFile: File,
    excludedScopes: Collection<String> = emptySet(),
    allowDynamicVersions: Boolean = false,
    resolveScopes: Boolean = false
): ProjectAnalyzerResult {
    val analyzerConfig = AnalyzerConfiguration(
        allowDynamicVersions = allowDynamicVersions,
        skipExcluded = excludedScopes.isNotEmpty()
    )
    val repoConfig = RepositoryConfiguration(
        excludes = Excludes(
            scopes = excludedScopes.map { ScopeExclude(it, ScopeExcludeReason.TEST_DEPENDENCY_OF) }
        )
    )

    val npm = Npm("NPM", USER_DIR, analyzerConfig, repoConfig)

    return npm.resolveSingleProject(definitionFile, resolveScopes)
}
