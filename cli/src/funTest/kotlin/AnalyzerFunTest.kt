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

package org.ossreviewtoolkit.cli

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.concurrent.shouldCompleteWithin

import java.util.concurrent.TimeUnit

import org.ossreviewtoolkit.analyzer.Analyzer
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.utils.test.createTestTempDir

class AnalyzerFunTest : WordSpec({
    "A globally configured 'mustRunAfter'" should {
        "not block when depending on a package manager for which no definition files have been found" {
            val inputDir = createTestTempDir()
            val gradleDefinitionFile = inputDir.resolve("gradle.build").apply { writeText("// Dummy file") }

            val gradleConfig = PackageManagerConfiguration(mustRunAfter = listOf("NPM"))
            val analyzerConfig = AnalyzerConfiguration().copy(packageManagers = mapOf("Gradle" to gradleConfig))
            val repoConfig = RepositoryConfiguration()

            val analyzer = Analyzer(analyzerConfig)
            val gradleFactory = PackageManager.ALL.getValue("Gradle")
            val gradle = gradleFactory.create(inputDir, analyzerConfig, repoConfig)
            val info = Analyzer.ManagedFileInfo(
                inputDir,
                mapOf(gradle to listOf(gradleDefinitionFile)),
                repoConfig
            )

            shouldCompleteWithin(20, TimeUnit.SECONDS) {
                analyzer.analyze(info)
            }
        }
    }
})
