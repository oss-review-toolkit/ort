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

package org.ossreviewtoolkit.plugins.packagemanagers.cocoapods

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.should

import org.ossreviewtoolkit.analyzer.analyze
import org.ossreviewtoolkit.analyzer.getAnalyzerResult
import org.ossreviewtoolkit.analyzer.resolveSingleProject
import org.ossreviewtoolkit.analyzer.withInvariantIssues
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.plugins.packagemanagers.node.npm.NpmFactory
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.matchExpectedResult

class CocoaPodsFunTest : WordSpec({
    "resolveSingleProject()" should {
        "determine dependencies from a Podfile without a dependency tree" {
            val definitionFile = getAssetFile("projects/synthetic/regular/Podfile")
            val expectedResultFile = getAssetFile("projects/synthetic/regular-expected-output.yml")

            val result = CocoaPodsFactory.create().resolveSingleProject(definitionFile, resolveScopes = true)

            result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }

        "determine dependencies from a Podfile with a dependency tree" {
            val definitionFile = getAssetFile("projects/synthetic/dep-tree/Podfile")
            val expectedResultFile = getAssetFile("projects/synthetic/dep-tree-expected-output.yml")

            val result = CocoaPodsFactory.create().resolveSingleProject(definitionFile, resolveScopes = true)

            result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }

        "determine dependencies from a Podfile with a external sources" {
            val definitionFile = getAssetFile("projects/synthetic/external-sources/Podfile")
            val expectedResultFile = getAssetFile("projects/synthetic/external-sources-expected-output.yml")

            val result = CocoaPodsFactory.create().resolveSingleProject(definitionFile, resolveScopes = true)

            result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }

        "determine dependencies from a Podfile that upgrades a version during resolution" {
            val definitionFile = getAssetFile("projects/synthetic/version-resolution/Podfile")
            val expectedResultFile = getAssetFile("projects/synthetic/version-resolution-expected-output.yml")

            val result = CocoaPodsFactory.create().resolveSingleProject(definitionFile, resolveScopes = true)

            result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }

        "return no dependencies along with an issue if the lockfile is absent" {
            val definitionFile = getAssetFile("projects/synthetic/no-lockfile/Podfile")
            val expectedResultFile = getAssetFile("projects/synthetic/no-lockfile-expected-output.yml")

            val result = CocoaPodsFactory.create().resolveSingleProject(definitionFile, resolveScopes = true)

            result.withInvariantIssues().toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }

        "determine dependencies from a project which is using React Native" {
            // The NPM package manager is run first to initialize the node_modules directory, which is required to
            // parse the Podfile successfully. Note that any Node manager can be used as CocoaPods just requires the
            // 'node_modules' directory to be present.
            val npmDefinitionFile = getAssetFile("projects/synthetic/react-native/package.json")
            val definitionFile = getAssetFile("projects/synthetic/react-native/ios/Podfile")
            val expectedResultFile = getAssetFile("projects/synthetic/react-native-expected-output.yml")

            val result = analyze(
                projectDir = npmDefinitionFile.parentFile,
                allowDynamicVersions = true,
                packageManagers = listOf(NpmFactory(), CocoaPodsFactory()),
                packageManagerConfiguration = mapOf(
                    "CocoaPods" to PackageManagerConfiguration(mustRunAfter = listOf("NPM"))
                )
            ).getAnalyzerResult()

            // The NPM-related results are not relevant, because this test checks if the Pod packages can be filled with
            // information coming from the podspec files present in the 'node_modules' directory.
            val analyzerResult = result.copy(
                projects = result.projects.filterTo(mutableSetOf()) { it.id.type == "CocoaPods" },
                packages = result.packages.filterTo(mutableSetOf()) { it.id.type == "Pod" },
                issues = emptyMap()
            )

            analyzerResult.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }
    }
})
