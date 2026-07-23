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

package org.ossreviewtoolkit.plugins.packagemanagers.githubaction

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.should

import org.ossreviewtoolkit.analyzer.resolveSingleProject
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.matchExpectedResult

@Tags("RequiresExternalTool")
class GitHubActionFunTest : StringSpec({
    "in-org dependencies are detected correctly" {
        val definitionFile = getAssetFile("projects/synthetic/sha-pinning/.github/workflows/inorgexample.yml")
        val expectedResultFile = getAssetFile("projects/synthetic/inorgexample-expected-output.yml")

        val result = GitHubActionFactory.create().resolveSingleProject(definitionFile, resolveScopes = true)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "out-of-org dependencies are detected correctly" {
        val definitionFile = getAssetFile("projects/synthetic/sha-pinning/.github/workflows/outoforgexample.yml")
        val expectedResultFile = getAssetFile("projects/synthetic/outoforgexample-expected-output.yml")

        val result = GitHubActionFactory.create().resolveSingleProject(definitionFile, resolveScopes = true)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "out-of-org-exploited dependencies are detected correctly" {
        val definitionFile = getAssetFile("projects/synthetic/sha-pinning/.github/workflows/outoforgexploited.yml")
        val expectedResultFile = getAssetFile("projects/synthetic/outoforgexploited-expected-output.yml")

        val result = GitHubActionFactory.create().resolveSingleProject(definitionFile, resolveScopes = true)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "local dependencies are detected correctly" {
        val definitionFile = getAssetFile("projects/synthetic/ort-like/.github/workflows/local.yml")
        val expectedResultFile = getAssetFile("projects/synthetic/local-expected-output.yml")

        val result = GitHubActionFactory.create().resolveSingleProject(definitionFile, resolveScopes = true)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "transitive dependencies are detected correctly" {
        val definitionFile = getAssetFile("projects/synthetic/ort-like/.github/workflows/transitive.yml")
        val expectedResultFile = getAssetFile("projects/synthetic/transitive-expected-output.yml")

        val result = GitHubActionFactory.create().resolveSingleProject(definitionFile, resolveScopes = true)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }
})
