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

import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.inspectors.forAll
import io.kotest.inspectors.forAtLeastOne
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.shouldContain

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

class NpmFunTest : StringSpec({
    "Resolve dependencies for a project with a 'shrinkwrap.json' correctly" {
        val definitionFile = getAssetFile("projects/synthetic/npm/shrinkwrap/package.json")
        val expectedResultFile = getAssetFile("projects/synthetic/npm/shrinkwrap-expected-output.yml")

        val result = NpmFactory.create().resolveSingleProject(definitionFile, resolveScopes = true)

        patchActualResult(result.toYaml()) should matchExpectedResult(
            expectedResultFile,
            definitionFile,
            custom = mapOf(
                "<REPLACE_PROJECT_NAME>" to "npm-${definitionFile.parentFile.name}",
                "<REPLACE_LOCKFILE_NAME>" to "npm-shrinkwrap.json"
            )
        )
    }

    "Exclude scopes if configured" {
        val definitionFile = getAssetFile("projects/synthetic/npm/shrinkwrap/package.json")
        val expectedResultFile = getAssetFile(
            "projects/synthetic/npm/shrinkwrap-skip-excluded-scopes-expected-output.yml"
        )

        val result = NpmFactory.create()
            .resolveSingleProject(definitionFile, excludedScopes = setOf("devDependencies"), resolveScopes = true)

        patchActualResult(result.toYaml()) should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Resolve dependencies for a project with lockfile correctly" {
        val definitionFile = getAssetFile("projects/synthetic/npm/project-with-lockfile/package.json")
        val expectedResultFile = getAssetFile("projects/synthetic/npm/project-with-lockfile-expected-output.yml")

        val result = NpmFactory.create().resolveSingleProject(definitionFile, resolveScopes = true)

        patchActualResult(result.toYaml()) should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Show an error if no lockfile is present" {
        val definitionFile = getAssetFile("projects/synthetic/npm/no-lockfile/package.json")
        val expectedResultFile = getAssetFile("projects/synthetic/npm/no-lockfile-expected-output.yml")

        val result = NpmFactory.create().resolveSingleProject(definitionFile)

        patchActualResult(result.toYaml()) should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Show an error if the 'package.json' file is invalid" {
        val workingDir = tempdir()
        val definitionFile = workingDir.resolve("package.json").apply { writeText("<>") }

        val result = NpmFactory.create().resolveSingleProject(definitionFile, allowDynamicVersions = true)

        result.issues.forAtLeastOne {
            it.source shouldBe "NPM"
            it.severity shouldBe Severity.ERROR
            it.message shouldContain "Unexpected token"
        }
    }

    "Create issues for list errors" {
        val definitionFile = getAssetFile("projects/synthetic/npm/list-issues/package.json")

        val result = NpmFactory.create(legacyPeerDeps = true).resolveSingleProject(definitionFile)

        result.issues shouldNot beEmpty()
        val elsproblems = result.issues.filter { it.message.startsWith("invalid: ") }

        elsproblems shouldHaveSize 2
        elsproblems.forAll { it.severity shouldBe Severity.ERROR }

        elsproblems shouldHaveSingleElement { it.message.startsWith("invalid: react@18.2.0") }
        elsproblems shouldHaveSingleElement { it.message.startsWith("invalid: react-dom@18.2.0") }
    }

    "Resolve dependencies even if the 'node_modules' directory already exists" {
        val definitionFile = getAssetFile("projects/synthetic/npm/node-modules/package.json")
        val expectedResultFile = getAssetFile("projects/synthetic/npm/node-modules-expected-output.yml")

        val result = NpmFactory.create().resolveSingleProject(definitionFile, resolveScopes = true)

        patchActualResult(result.toYaml()) should matchExpectedResult(
            expectedResultFile,
            definitionFile,
            custom = mapOf("<REPLACE_LOCKFILE_NAME>" to "package-lock.json")
        )
    }

    "Resolve Babel dependencies correctly" {
        val definitionFile = getAssetFile("projects/synthetic/npm/babel/package.json")
        val expectedResultFile = getAssetFile("projects/synthetic/npm/babel-expected-output.yml")
        val expectedResult = patchExpectedResult(expectedResultFile.readText(), definitionFile)
            .fromYaml<ProjectAnalyzerResult>()

        val result = NpmFactory.create().resolveSingleProject(definitionFile, resolveScopes = true)

        result.withInvariantIssues().toYaml() shouldBe expectedResult.withInvariantIssues().toYaml()
    }

    "Resolve dependencies with URLs as versions correctly" {
        val definitionFile = getAssetFile("projects/synthetic/npm/version-urls/package.json")
        val expectedResultFile = getAssetFile("projects/synthetic/npm/version-urls-expected-output.yml")
        val expectedResult = patchExpectedResult(expectedResultFile.readText(), definitionFile)
            .fromYaml<ProjectAnalyzerResult>()

        val result = NpmFactory.create()
            .resolveSingleProject(definitionFile, allowDynamicVersions = true, resolveScopes = true)

        result.withInvariantIssues().toYaml() shouldBe expectedResult.withInvariantIssues().toYaml()
    }
})
