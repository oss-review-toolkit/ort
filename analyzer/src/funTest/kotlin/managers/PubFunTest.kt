/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.analyzer.managers

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.haveSubstring

import java.io.File

import org.ossreviewtoolkit.analyzer.Analyzer
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.utils.core.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import org.ossreviewtoolkit.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import org.ossreviewtoolkit.utils.test.NoDockerTag
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.patchActualResult
import org.ossreviewtoolkit.utils.test.patchExpectedResult

class PubFunTest : WordSpec() {
    private val projectsDir = File("src/funTest/assets/projects/synthetic/pub/").absoluteFile
    private val projectsDirExternal = File("src/funTest/assets/projects/external/").absoluteFile
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
                    val packageFile = workingDir.resolve("pubspec.yaml")
                    val expectedResultFile = projectsDirExternal.resolve("dart-http-expected-output.yml")

                    val result = createPubForExternal().resolveSingleProject(packageFile)
                    val vcsPath = vcsDir.getPathToRoot(workingDir)
                    val expectedResult = patchExpectedResult(
                        expectedResultFile,
                        custom = mapOf("pub-project" to "pub-${workingDir.name}"),
                        definitionFilePath = "$vcsPath/pubspec.yaml",
                        url = normalizeVcsUrl(vcsUrl),
                        revision = vcsRevision,
                        path = vcsPath
                    )

                    result.toYaml() shouldBe expectedResult
                } finally {
                    lockFile.delete()
                }
            }

            "resolve dependencies for a project with dependencies without a static version" {
                val workingDir = projectsDir.resolve("any-version")
                val packageFile = workingDir.resolve("pubspec.yaml")
                val expectedResultFile = projectsDir.parentFile.resolve("pub-expected-output-any-version.yml")

                val result = createPub().resolveSingleProject(packageFile)
                val vcsPath = vcsDir.getPathToRoot(workingDir)
                val expectedResult = patchExpectedResult(
                    expectedResultFile,
                    custom = mapOf("pub-project" to "pub-${workingDir.name}"),
                    definitionFilePath = "$vcsPath/pubspec.yaml",
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision,
                    path = vcsPath
                )

                result.toYaml() shouldBe expectedResult
            }

            "resolve dependencies for a project with Flutter, Android and Cocoapods".config(tags = setOf(NoDockerTag)) {
                val workingDir = projectsDir.resolve("flutter-project-with-android-and-cocoapods")
                val expectedResultFile =
                    projectsDir.parentFile.resolve("pub-expected-output-with-flutter-android-and-cocoapods.yml")

                val analyzer = Analyzer(DEFAULT_ANALYZER_CONFIGURATION)
                val managedFiles = analyzer.findManagedFiles(workingDir)

                val analyzerResult = analyzer.analyze(managedFiles).patchAapt2Result()

                val vcsPath = vcsDir.getPathToRoot(workingDir)
                val expectedResult = patchExpectedResult(
                    expectedResultFile,
                    url = vcsUrl,
                    urlProcessed = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision,
                    path = vcsPath
                )

                patchActualResult(analyzerResult, true) shouldBe expectedResult
            }

            "show an error if no lockfile is present" {
                val workingDir = projectsDir.resolve("no-lockfile")
                val packageFile = workingDir.resolve("pubspec.yaml")

                val result = createPub().resolveSingleProject(packageFile)

                with(result) {
                    project.definitionFilePath shouldBe
                            "analyzer/src/funTest/assets/projects/synthetic/pub/no-lockfile/pubspec.yaml"
                    packages.size shouldBe 0
                    issues.size shouldBe 1
                    issues.first().message should haveSubstring("IllegalArgumentException: No lockfile found in")
                }
            }
        }
    }

    private fun createPub() =
        Pub("Pub", USER_DIR, DEFAULT_ANALYZER_CONFIGURATION, DEFAULT_REPOSITORY_CONFIGURATION)

    private fun createPubForExternal(): Pub {
        val config = AnalyzerConfiguration(allowDynamicVersions = true)
        return Pub("Pub", USER_DIR, config, DEFAULT_REPOSITORY_CONFIGURATION)
    }

    /**
     * Replace aapt2 URL and hash value with dummy values, as these are platform dependent.
     */
    private fun OrtResult.patchAapt2Result(): OrtResult {
        val packages = analyzer?.result?.packages?.toMutableSet()
        val aapt2Package =
            packages?.find {
                it.pkg.id.type == "Maven" &&
                        it.pkg.id.namespace == "com.android.tools.build" &&
                        it.pkg.id.name == "aapt2"
            }

        val patchedPackages = packages?.map { pkg ->
            if (pkg == aapt2Package) {
                aapt2Package?.copy(
                    pkg = aapt2Package.pkg.copy(
                        binaryArtifact = aapt2Package.pkg.binaryArtifact.copy(
                            url = "***",
                            hash = Hash("***", HashAlgorithm.SHA1)
                        )
                    )
                ) ?: pkg
            } else pkg
        }?.toSortedSet() ?: sortedSetOf()

        return copy(
            analyzer = analyzer?.result?.copy(packages = patchedPackages)?.let { analyzerResult ->
                analyzer?.copy(
                    result = analyzerResult
                )
            }
        )
    }
}
