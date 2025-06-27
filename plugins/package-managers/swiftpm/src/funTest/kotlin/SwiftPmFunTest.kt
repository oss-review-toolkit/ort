/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.swiftpm

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.should

import org.ossreviewtoolkit.analyzer.resolveSingleProject
import org.ossreviewtoolkit.analyzer.withInvariantIssues
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.matchExpectedResult

class SwiftPmFunTest : WordSpec({
    "Analyzing a lockfile with file format version 1" should {
        "return the correct result" {
            val definitionFile = getAssetFile("projects/synthetic/only-lockfile-v1/Package.resolved")
            val expectedResultFile = getAssetFile("projects/synthetic/expected-output-only-lockfile-v1.yml")

            val result = SwiftPmFactory.create().resolveSingleProject(definitionFile)

            result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }
    }

    "Analyzing a lockfile with file format version 2" should {
        "return the correct result" {
            val definitionFile = getAssetFile("projects/synthetic/only-lockfile-v2/Package.resolved")
            val expectedResultFile = getAssetFile("projects/synthetic/expected-output-only-lockfile-v2.yml")

            val result = SwiftPmFactory.create().resolveSingleProject(definitionFile)

            result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }
    }

    "Analyzing a lockfile with file format version 3" should {
        "return the correct result" {
            val definitionFile = getAssetFile("projects/synthetic/only-lockfile-v3/Package.resolved")
            val expectedResultFile = getAssetFile("projects/synthetic/expected-output-only-lockfile-v3.yml")

            val result = SwiftPmFactory.create().resolveSingleProject(definitionFile)

            result.withInvariantIssues().toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }
    }

    "Analyzing a lockfile with unsupported file format version 4" should {
        "return the correct result" {
            val definitionFile = getAssetFile("projects/synthetic/only-lockfile-v4/Package.resolved")
            val expectedResultFile = getAssetFile("projects/synthetic/expected-output-only-lockfile-v4.yml")

            val result = SwiftPmFactory.create().resolveSingleProject(definitionFile)

            result.withInvariantIssues().toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }
    }

    @Suppress("MaxLineLength")
    "Analyzing a lockfile with a dependency loaded over SPM registry instead of source control with no registry config present" should {
        "return the correct result" {
            val definitionFile =
                getAssetFile("projects/synthetic/only-lockfile-v3-with-SPM-registry-dependency/Package.resolved")
            val expectedResultFile =
                getAssetFile("projects/synthetic/expected-output-only-lockfile-v3-with-SPM-registry-dependency.yml")

            val result = SwiftPmFactory.create().resolveSingleProject(definitionFile)

            result.withInvariantIssues().toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }
    }

    "Analyzing a lockfile with a dependency loaded over SPM registry with registry configuration" should {
        "return the correct result" {
            val definitionFile =
                getAssetFile("projects/synthetic/lockfile-v3-with-SPM-registry-configuration/Package.resolved")
            val expectedResultFile =
                getAssetFile("projects/synthetic/expected-lockfile-v3-with-SPM-registry-configuration.yml")

            val result = SwiftPmFactory.create().resolveSingleProject(definitionFile)

            result.withInvariantIssues().toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }
    }

    "Analyzing a definition file with a sibling lockfile" should {
        "return the correct result" {
            val definitionFile = getAssetFile("projects/synthetic/project-with-lockfile/Package.swift")
            val expectedResultFile = getAssetFile("projects/synthetic/expected-output-project-with-lockfile.yml")

            val result = SwiftPmFactory.create().resolveSingleProject(definitionFile, resolveScopes = true)

            result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }
    }

    "Analyzing a definition file without a lockfile" should {
        "return an issue if 'allowDynamicVersions' is disabled" {
            val definitionFile = getAssetFile("projects/synthetic/project-without-lockfile/Package.swift")
            val expectedResultFile = getAssetFile(
                "projects/synthetic/expected-output-project-without-lockfile.yml"
            )

            val result = SwiftPmFactory.create().resolveSingleProject(definitionFile, resolveScopes = true)

            result.withInvariantIssues().toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }
    }

    "Analyzing a definition file without dependencies" should {
        "return the correct result" {
            // Note: SwiftPM does not create a lockfile if there are no dependencies which is a corner case.
            val definitionFile = getAssetFile("projects/synthetic/project-without-deps/Package.swift")
            val expectedResultFile = getAssetFile("projects/synthetic/expected-output-project-without-deps.yml")

            val result = SwiftPmFactory.create()
                .resolveSingleProject(definitionFile, allowDynamicVersions = true, resolveScopes = true)

            result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }
    }

    "readSwiftPackageRegistryConfiguration()" should {
        "parse a valid 'registries.json' file correctly" {
            val registriesFile = getAssetFile("projects/synthetic/registry-configuration/registries.json")
            val expectedResultFile = getAssetFile("projects/synthetic/expected-output-registry-configuration.yml")

            val result = readSwiftPackageRegistryConfiguration(registriesFile)

            result.toYaml() should matchExpectedResult(expectedResultFile, registriesFile)
        }
    }
})
