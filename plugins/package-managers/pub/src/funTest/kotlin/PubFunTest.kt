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

package org.ossreviewtoolkit.plugins.packagemanagers.pub

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.string.haveSubstring

import org.ossreviewtoolkit.analyzer.analyze
import org.ossreviewtoolkit.analyzer.getAnalyzerResult
import org.ossreviewtoolkit.analyzer.resolveSingleProject
import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.collectDependencies
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.matchExpectedResult

class PubFunTest : WordSpec({
    "Pub" should {
        "resolve dart http dependencies correctly" {
            val definitionFile = getAssetFile("projects/external/dart-http/pubspec.yaml")
            val expectedResultFile = getAssetFile("projects/external/dart-http-expected-output.yml")
            val lockfile = definitionFile.resolveSibling("pubspec.lock").also {
                getAssetFile("projects/external/dart-http-pubspec.lock").copyTo(it, overwrite = true)
            }

            val result = try {
                PubFactory.create().resolveSingleProject(definitionFile, allowDynamicVersions = true)
            } finally {
                lockfile.delete()
            }

            result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }

        "resolve dependencies for a project with dependencies without a static version" {
            val definitionFile = getAssetFile("projects/synthetic/any-version/pubspec.yaml")
            val expectedResultFile = getAssetFile("projects/synthetic/pub-expected-output-any-version.yml")

            val result = PubFactory.create().resolveSingleProject(definitionFile)

            result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }

        "resolve multi-module dependencies correctly" {
            val definitionFile = getAssetFile("projects/synthetic/multi-module/pubspec.yaml")
            val expectedResultFile = getAssetFile("projects/synthetic/pub-expected-output-multi-module.yml")

            val result = analyze(definitionFile.parentFile).getAnalyzerResult()

            result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }

        "resolve dependencies for a project with Flutter, Android and Cocoapods" {
            val definitionFile = getAssetFile(
                "projects/synthetic/flutter-project-with-android-and-cocoapods/pubspec.yaml"
            )
            val expectedResultFile = getAssetFile(
                "projects/synthetic/pub-expected-output-with-flutter-android-and-cocoapods.yml"
            )

            val result = analyze(
                definitionFile.parentFile,
                packageManagerConfiguration = mapOf(
                    "GradleInspector" to PackageManagerConfiguration(options = mapOf("javaVersion" to "17"))
                )
            ).getAnalyzerResult()

            result.patchPackages().reduceToPubProjects().toYaml() should
                matchExpectedResult(expectedResultFile, definitionFile)
        }

        "show an error if no lockfile is present" {
            val definitionFile = getAssetFile("projects/synthetic/no-lockfile/pubspec.yaml")

            val result = PubFactory.create().resolveSingleProject(definitionFile)

            with(result) {
                packages should beEmpty()
                issues shouldHaveSize 1
                issues.first().message should haveSubstring("IllegalArgumentException: No lockfile found in")
            }
        }
    }
})

private fun AnalyzerResult.reduceToPubProjects(): AnalyzerResult {
    val pubProjects = projects.filterTo(mutableSetOf()) { it.id.type == "Pub" }
    val scopes = pubProjects.flatMap { it.scopes }
    val dependencies = scopes.collectDependencies()

    return AnalyzerResult(
        projects = pubProjects,
        packages = packages.filterTo(mutableSetOf()) { it.id in dependencies },
        issues = issues
    )
}

private fun AnalyzerResult.patchPackages(): AnalyzerResult {
    val patchedPackages = packages.mapTo(mutableSetOf()) { pkg ->
        // Replace aapt2 URL and hash value with dummy values, as these are platform dependent.
        pkg.takeUnless { it.id.toCoordinates().startsWith("Maven:com.android.tools.build:aapt2:") }
            ?: pkg.copy(
                binaryArtifact = pkg.binaryArtifact.copy(
                    url = "https://example.com/",
                    hash = Hash.NONE
                )
            )
    }

    return copy(packages = patchedPackages)
}
