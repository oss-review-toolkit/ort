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

package org.ossreviewtoolkit.plugins.packagemanagers.node.yarn

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should

import org.ossreviewtoolkit.analyzer.analyze
import org.ossreviewtoolkit.analyzer.getAnalyzerResult
import org.ossreviewtoolkit.analyzer.resolveSingleProject
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.matchExpectedResult

class YarnFunTest : StringSpec({
    "Resolve dependencies for a project with lockfile correctly" {
        val definitionFile = getAssetFile("projects/synthetic/yarn/project-with-lockfile/package.json")
        val expectedResultFile = getAssetFile("projects/synthetic/yarn/project-with-lockfile-expected-output.yml")

        val result = YarnFactory.create().resolveSingleProject(definitionFile, resolveScopes = true)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Resolve dependencies for a project with lockfile with excluded scopes correctly" {
        val definitionFile = getAssetFile("projects/synthetic/yarn/project-with-lockfile/package.json")
        val expectedResultFile = getAssetFile(
            "projects/synthetic/yarn/project-with-lockfile-skip-excluded-scopes-expected-output.yml"
        )

        val result = YarnFactory.create()
            .resolveSingleProject(definitionFile, excludedScopes = setOf("devDependencies"), resolveScopes = true)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Resolve dependencies for a project which installs a module with an invalid unused 'package.json'" {
        val definitionFile = getAssetFile("projects/synthetic/yarn/invalid-package-json/package.json")
        val expectedResultFile = getAssetFile("projects/synthetic/yarn/invalid-package-json-expected-output.yml")

        val result = YarnFactory.create().resolveSingleProject(definitionFile, resolveScopes = true)

        result.issues should beEmpty()
        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Resolve dependencies for a project depending on Babel correctly" {
        val definitionFile = getAssetFile("projects/synthetic/yarn/babel/package.json")
        val expectedResultFile = getAssetFile("projects/synthetic/yarn/babel-expected-output.yml")

        val result = YarnFactory.create().resolveSingleProject(definitionFile, resolveScopes = true)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Resolve workspace dependencies correctly" {
        // This test case illustrates the lack of Yarn workspaces support, in particular not all workspace
        // dependencies get assigned to a scope.
        val definitionFile = getAssetFile("projects/synthetic/yarn/workspaces/package.json")
        val expectedResultFile = getAssetFile("projects/synthetic/yarn/workspaces-expected-output.yml")

        val result = analyze(definitionFile.parentFile, packageManagers = setOf(YarnFactory())).getAnalyzerResult()

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Resolve dependencies for a project which has dependency alias use for transitive dependencies" {
        val definitionFile = getAssetFile("projects/synthetic/yarn/alias-use-for-transitive-deps/package.json")
        val expectedResultFile = getAssetFile(
            "projects/synthetic/yarn/alias-use-for-transitive-deps-expected-output.yml"
        )

        val result = YarnFactory.create().resolveSingleProject(definitionFile, resolveScopes = true)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Resolve dependencies with dangling linked dependency references" {
        val definitionFile = getAssetFile("projects/synthetic/yarn/dangling-linked-references/package.json")
        val expectedResultFile = getAssetFile(
            "projects/synthetic/yarn/dangling-linked-references-expected-output.yml"
        )

        val result = YarnFactory.create().resolveSingleProject(definitionFile, resolveScopes = true)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }
})
