/*
 * Copyright (C) 2025 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.gleam

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.file
import io.kotest.property.arbitrary.single

import java.io.File

import org.ossreviewtoolkit.analyzer.analyze
import org.ossreviewtoolkit.analyzer.getAnalyzerResult
import org.ossreviewtoolkit.analyzer.resolveSingleProject
import org.ossreviewtoolkit.analyzer.withInvariantIssues
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.matchExpectedResult

@Tags("RequiresExternalTool")
class GleamFunTest : StringSpec({
    val server = WireMockServer(
        WireMockConfiguration.options()
            .dynamicPort()
            .usingFilesUnderClasspath("wiremock")
    )

    fun createGleam() = Gleam(hexApiClientFactory = { HexApiClient(server.baseUrl()) })

    beforeSpec { server.start() }
    beforeEach { server.resetAll() }
    afterSpec { server.stop() }

    "Resolve dependencies for a project with a lockfile correctly" {
        val definitionFile = getAssetFile("projects/synthetic/project-with-lockfile/gleam.toml")
        val expectedResultFile = getAssetFile("projects/synthetic/project-with-lockfile-expected-output.yml")

        val result = createGleam().resolveSingleProject(definitionFile, resolveScopes = true)

        result.withInvariantIssues().toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Resolve dependencies for a project with a lockfile and scope excludes correctly" {
        val definitionFile = getAssetFile("projects/synthetic/project-with-lockfile/gleam.toml")
        val expectedResultFile = getAssetFile(
            "projects/synthetic/project-with-lockfile-scope-excludes-expected-output.yml"
        )

        val result = createGleam().resolveSingleProject(
            definitionFile,
            excludedScopes = setOf("dev-dependencies"),
            resolveScopes = true
        )

        result.withInvariantIssues().toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Resolve dependencies for a project without a lockfile fails if 'allowDynamicVersions' is disabled" {
        val definitionFile = getAssetFile("projects/synthetic/no-lockfile/gleam.toml")
        val expectedResultFile = getAssetFile("projects/synthetic/no-lockfile-expected-output.yml")

        val result = createGleam().resolveSingleProject(definitionFile, resolveScopes = true)

        result.withInvariantIssues().toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Resolve dependencies for a project without a lockfile if 'allowDynamicVersions' is enabled" {
        val projectDir = tempdir()
        getAssetFile("projects/synthetic/no-lockfile").copyRecursively(projectDir)

        val result = createGleam().resolveSingleProject(
            projectDir.resolve("gleam.toml"),
            resolveScopes = true,
            allowDynamicVersions = true
        )

        result.packages shouldNot beEmpty()
        result.packages.map { it.id.name } shouldContain "gleam_stdlib"
    }

    "Resolve dependencies fails with error details for broken gleam.toml" {
        val projectDir = tempdir()
        getAssetFile("projects/synthetic/no-lockfile-broken").copyRecursively(projectDir)

        val result = createGleam().resolveSingleProject(
            projectDir.resolve("gleam.toml"),
            resolveScopes = true,
            allowDynamicVersions = true
        )

        result.issues.shouldBeSingleton {
            it.severity shouldBe Severity.ERROR
            it.message shouldContain "failed with exit code"
            it.message shouldContain "Dependency resolution failed"
        }

        result.packages should beEmpty()
    }

    "Resolve dependencies for a project with no dependencies correctly" {
        val definitionFile = getAssetFile("projects/synthetic/no-deps/gleam.toml")
        val expectedResultFile = getAssetFile("projects/synthetic/no-deps-expected-output.yml")

        val result = createGleam().resolveSingleProject(definitionFile, resolveScopes = true)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Resolve dependencies for a project with empty dependency sections correctly" {
        val definitionFile = getAssetFile("projects/synthetic/empty-deps/gleam.toml")
        val expectedResultFile = getAssetFile("projects/synthetic/empty-deps-expected-output.yml")

        val result = createGleam().resolveSingleProject(definitionFile, resolveScopes = true)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Project files from build directories are ignored" {
        val projectFiles = GleamFactory.create().mapDefinitionFiles(
            Arb.file().single(),
            listOf(
                "projectA/gleam.toml",
                "projectA/build/packages/gleam_stdlib/gleam.toml",
                "projectA/build/packages/gleeunit/gleam.toml",
                "projectB/gleam.toml",
                "projectB/build/packages/json/gleam.toml"
            ).map { File(it) },
            AnalyzerConfiguration()
        )

        projectFiles.map { it.path } should containExactly(
            "projectA/gleam.toml",
            "projectB/gleam.toml"
        )
    }

    "Resolve dependencies for a multi-project with cross-directory path dependencies correctly" {
        val projectDir = getAssetFile("projects/synthetic/multi-project")
        val definitionFileParent = projectDir / "gleam.toml"
        val definitionFileChild = projectDir / "child" / "gleam.toml"
        val definitionFileSibling = projectDir / "sibling" / "gleam.toml"
        val expectedResultFile = getAssetFile("projects/synthetic/multi-project-expected-output.yml")

        val result = analyze(projectDir, packageManagers = setOf(GleamFactory())).getAnalyzerResult()

        val childPath = definitionFileChild.relativeTo(projectDir).invariantSeparatorsPath
        val siblingPath = definitionFileSibling.relativeTo(projectDir).invariantSeparatorsPath

        result.toYaml() should matchExpectedResult(
            expectedResultFile,
            definitionFileParent,
            custom = mapOf(
                "<REPLACE_DEFINITION_FILE_PATH_CHILD>" to childPath,
                "<REPLACE_DEFINITION_FILE_PATH_SIBLING>" to siblingPath
            )
        )
    }
})
