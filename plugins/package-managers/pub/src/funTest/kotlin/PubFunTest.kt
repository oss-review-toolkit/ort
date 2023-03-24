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
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.haveSubstring

import java.io.File

import org.ossreviewtoolkit.analyzer.Analyzer
import org.ossreviewtoolkit.analyzer.managers.resolveSingleProject
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.patchExpectedResult
import org.ossreviewtoolkit.utils.test.toYaml

class PubFunTest : WordSpec() {
    private val projectsDir = getAssetFile("projects/synthetic")
    private val projectsDirExternal = getAssetFile("projects/external")
    private val vcsDir = VersionControlSystem.forDirectory(projectsDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    init {
        "Pub" should {
            "resolve dart http dependencies correctly" {
                val workingDir = projectsDirExternal.resolve("dart-http")
                val lockFile = workingDir.resolve("pubspec.lock")
                projectsDirExternal.resolve("dart-http-pubspec.lock").copyTo(lockFile, overwrite = true)

                try {
                    val definitionFile = workingDir.resolve("pubspec.yaml")
                    val expectedResult = getExpectedResult("dart-http-expected-output.yml", workingDir)

                    val result = createPub(
                        AnalyzerConfiguration(allowDynamicVersions = true)
                    ).resolveSingleProject(definitionFile)

                    result.toYaml() shouldBe expectedResult
                } finally {
                    lockFile.delete()
                }
            }

            "resolve dependencies for a project with dependencies without a static version" {
                val workingDir = projectsDir.resolve("any-version")
                val definitionFile = workingDir.resolve("pubspec.yaml")
                val expectedResult = getExpectedResult("pub-expected-output-any-version.yml", workingDir)

                val result = createPub().resolveSingleProject(definitionFile)

                result.toYaml() shouldBe expectedResult
            }

            "resolve multi-module dependencies correctly" {
                val workingDir = projectsDir.resolve("multi-module")
                val expectedResult = getExpectedResult("pub-expected-output-multi-module.yml", workingDir)

                val analyzerResult = analyze(workingDir).patchPackages()

                analyzerResult.toYaml() shouldBe expectedResult
            }

            "resolve dependencies for a project with Flutter, Android and Cocoapods" {
                val workingDir = projectsDir.resolve("flutter-project-with-android-and-cocoapods")
                val expectedResult = getExpectedResult(
                    "pub-expected-output-with-flutter-android-and-cocoapods.yml",
                    workingDir
                )

                val analyzerResult = analyze(workingDir).patchPackages().reduceToPubProjects()

                analyzerResult.toYaml() shouldBe expectedResult
            }

            "show an error if no lockfile is present" {
                val workingDir = projectsDir.resolve("no-lockfile")
                val definitionFile = workingDir.resolve("pubspec.yaml")

                val result = createPub().resolveSingleProject(definitionFile)

                with(result) {
                    packages should beEmpty()
                    issues.size shouldBe 1
                    issues.first().message should haveSubstring("IllegalArgumentException: No lockfile found in")
                }
            }
        }
    }

    private fun getExpectedResult(expectedResultFilename: String, workingDir: File): String {
        val vcsPath = vcsDir.getPathToRoot(workingDir)
        val expectedResultDir = if (workingDir.startsWith(projectsDirExternal)) {
            projectsDirExternal
        } else {
            projectsDir
        }

        return patchExpectedResult(
            expectedResultDir.resolve(expectedResultFilename),
            url = normalizeVcsUrl(vcsUrl),
            revision = vcsRevision,
            path = vcsPath
        )
    }
}

private fun AnalyzerResult.reduceToPubProjects(): AnalyzerResult {
    val pubProjects = projects.filterTo(mutableSetOf()) { it.id.type == "Pub" }
    val scopes = pubProjects.flatMap { it.scopes }
    val dependencies = scopes.flatMap { it.collectDependencies() }

    return AnalyzerResult(
        projects = pubProjects,
        packages = packages.filterTo(mutableSetOf()) { it.id in dependencies },
        issues = issues
    )
}

private fun analyze(workingDir: File): AnalyzerResult {
    val analyzer = Analyzer(AnalyzerConfiguration())
    val managedFiles = analyzer.findManagedFiles(workingDir)
    val analyzerRun = analyzer.analyze(managedFiles).analyzer

    return checkNotNull(analyzerRun).result.withResolvedScopes()
}

private fun createPub(config: AnalyzerConfiguration = AnalyzerConfiguration()) =
    Pub("Pub", USER_DIR, config, RepositoryConfiguration())

/**
 * Replace aapt2 URL and hash value with dummy values, as these are platform dependent.
 */
private fun AnalyzerResult.patchPackages(): AnalyzerResult {
    val patchedPackages = packages.mapTo(mutableSetOf()) { pkg ->
        pkg.takeUnless { it.id.toCoordinates().startsWith("Maven:com.android.tools.build:aapt2:") }
            ?: pkg.copy(
                binaryArtifact = pkg.binaryArtifact.copy(
                    url = "***",
                    hash = Hash("***", HashAlgorithm.SHA1)
                )
            )
    }

    return copy(packages = patchedPackages)
}
