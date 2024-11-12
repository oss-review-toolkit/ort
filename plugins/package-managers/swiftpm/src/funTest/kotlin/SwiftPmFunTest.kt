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

import org.ossreviewtoolkit.analyzer.create
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

            val result = create("SwiftPM").resolveSingleProject(definitionFile)

            result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }
    }

    "Analyzing a lockfile with file format version 2" should {
        "return the correct result" {
            val definitionFile = getAssetFile("projects/synthetic/only-lockfile-v2/Package.resolved")
            val expectedResultFile = getAssetFile("projects/synthetic/expected-output-only-lockfile-v2.yml")

            val result = create("SwiftPM").resolveSingleProject(definitionFile)

            result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }
    }

    "Analyzing a lockfile with file format version 3" should {
        "return the correct result" {
            val definitionFile = getAssetFile("projects/synthetic/only-lockfile-v3/Package.resolved")
            val expectedResultFile = getAssetFile("projects/synthetic/expected-output-only-lockfile-v3.yml")

            val result = create("SwiftPM").resolveSingleProject(definitionFile)

            result.withInvariantIssues().toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }
    }

    "Analyzing a lockfile with unsupported file format version 4" should {
        "return the correct result" {
            val definitionFile = getAssetFile("projects/synthetic/only-lockfile-v4/Package.resolved")
            val expectedResultFile = getAssetFile("projects/synthetic/expected-output-only-lockfile-v4.yml")

            val result = create("SwiftPM").resolveSingleProject(definitionFile)

            result.withInvariantIssues().toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }
    }

    "Analyzing a definition file with a sibling lockfile" should {
        "return the correct result" {
            val definitionFile = getAssetFile("projects/synthetic/project-with-lockfile/Package.swift")
            val expectedResultFile = getAssetFile("projects/synthetic/expected-output-project-with-lockfile.yml")

            val result = create("SwiftPM").resolveSingleProject(definitionFile, resolveScopes = true)

            result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }
    }

    "Analyzing a definition file without a lockfile" should {
        "return an issue if 'allowDynamicVersions' is disabled" {
            val definitionFile = getAssetFile("projects/synthetic/project-without-lockfile/Package.swift")
            val expectedResultFile = getAssetFile(
                "projects/synthetic/expected-output-project-without-lockfile.yml"
            )

            val result = create("SwiftPM").resolveSingleProject(definitionFile, resolveScopes = true)

            result.withInvariantIssues().toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }
    }

    "Analyzing a definition file without dependencies" should {
        "return the correct result" {
            // Note: SwiftPM does not create a lockfile if there are no dependencies which is a corner case.
            val definitionFile = getAssetFile("projects/synthetic/project-without-deps/Package.swift")
            val expectedResultFile = getAssetFile("projects/synthetic/expected-output-project-without-deps.yml")

            val result = create("SwiftPM", allowDynamicVersions = true)
                .resolveSingleProject(definitionFile, resolveScopes = true)

            result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }
    }
})
