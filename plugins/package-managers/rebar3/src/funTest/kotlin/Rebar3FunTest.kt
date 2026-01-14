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

package org.ossreviewtoolkit.plugins.packagemanagers.rebar3

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot

import org.ossreviewtoolkit.analyzer.resolveSingleProject
import org.ossreviewtoolkit.analyzer.withInvariantIssues
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.matchExpectedResult

@Tags("RequiresExternalTool")
class Rebar3FunTest : StringSpec({
    "Resolve dependencies for a sample project correctly" {
        val definitionFile = getAssetFile("projects/synthetic/sample-project/rebar.config")
        val expectedResultFile = getAssetFile("projects/synthetic/sample-project-expected-output.yml")

        val result = Rebar3Factory.create().resolveSingleProject(definitionFile, resolveScopes = true)

        result.withInvariantIssues().toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Resolve dependencies for a project with no dependencies correctly" {
        val definitionFile = getAssetFile("projects/synthetic/no-deps/rebar.config")
        val expectedResultFile = getAssetFile("projects/synthetic/no-deps-expected-output.yml")

        val result = Rebar3Factory.create().resolveSingleProject(definitionFile, resolveScopes = true)

        result.withInvariantIssues().toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "A project with dependencies should resolve packages" {
        val definitionFile = getAssetFile("projects/synthetic/sample-project/rebar.config")

        val result = Rebar3Factory.create().resolveSingleProject(definitionFile, resolveScopes = true)

        result.packages shouldNot beEmpty()
    }
})
