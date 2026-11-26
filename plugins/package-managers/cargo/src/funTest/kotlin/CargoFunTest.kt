/*
 * Copyright (C) 2019 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.cargo

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should

import org.ossreviewtoolkit.analyzer.resolveScopes
import org.ossreviewtoolkit.analyzer.resolveSingleProject
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.collectDependencies
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.Includes
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.utils.common.getCommonParentFile
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.matchExpectedResult

@Tags("RequiresExternalTool")
class CargoFunTest : StringSpec({
    "Project dependencies are detected correctly" {
        val definitionFile = getAssetFile("projects/synthetic/cargo/Cargo.toml")
        val expectedResultFile = getAssetFile("projects/synthetic/cargo-expected-output.yml")

        val result = CargoFactory.create().resolveSingleProject(definitionFile, resolveScopes = true)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "A Git source is correctly mapped to VCS information" {
        val definitionFile = getAssetFile("projects/synthetic/cargo-git-source/Cargo.toml")
        val expectedResultFile = getAssetFile("projects/synthetic/cargo-git-source-expected-output.yml")

        val result = CargoFactory.create().resolveSingleProject(definitionFile, resolveScopes = true)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Shared dependencies keep the transitive versions locked by each project" {
        // Both projects depend on the same version of "aho-corasick", but lock different versions of its "memchr"
        // dependency.
        val definitionFiles = listOf(
            getAssetFile("projects/synthetic/cargo-separate-lockfiles/first/Cargo.toml"),
            getAssetFile("projects/synthetic/cargo-separate-lockfiles/second/Cargo.toml")
        )
        val analysisRoot = getCommonParentFile(definitionFiles)

        val result = CargoFactory.create().resolveDependencies(
            analysisRoot,
            definitionFiles,
            Excludes.EMPTY,
            Includes.EMPTY,
            AnalyzerConfiguration(),
            emptyMap()
        )

        val projectResults = definitionFiles.map { result.resolveScopes(result.projectResults.getValue(it).single()) }

        projectResults.flatMap { it.issues } should beEmpty()
        projectResults.map { it.project.scopes.collectDependencies() } should containExactlyInAnyOrder(
            setOf(Identifier("Crate::aho-corasick:0.7.20"), Identifier("Crate::memchr:2.4.1")),
            setOf(Identifier("Crate::aho-corasick:0.7.20"), Identifier("Crate::memchr:2.5.0"))
        )
    }
})
