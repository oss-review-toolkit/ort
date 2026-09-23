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

package org.ossreviewtoolkit.plugins.packagemanagers.gradleinspector

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.should

import org.ossreviewtoolkit.analyzer.AnalyzerResultBuilder
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.analyzer.withResolvedScopes
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.Includes
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.matchExpectedResult
import org.ossreviewtoolkit.utils.test.patchActualResult

class GradleNodeModulesFunTest : StringSpec({
    "A dependency substituted from inside a 'node_modules' directory is represented as a separate project" {
        // This reproduces the setup used by React Native's autolinking. Gradle resolves the autolinked artifact as
        // a project dependency pointing into "node_modules", which is not part of the analyzed Gradle build.
        // GradleInspector adds a synthetic project for such dependencies to keep the dependency graph consistent.
        // See https://github.com/react-native-community/cli/blob/main/docs/autolinking.md
        val definitionFile = getAssetFile("projects/synthetic/gradle-node-modules/build.gradle.kts").toGradle()
        val expectedResultFile = getAssetFile("projects/synthetic/gradle-node-modules-expected-output.yml")

        val managerResult = GradleInspectorFactory.create(javaVersion = "17").resolveDependencies(
            analysisRoot = definitionFile.parentFile,
            definitionFiles = listOf(definitionFile),
            excludes = Excludes.EMPTY,
            includes = Includes.EMPTY,
            analyzerConfig = AnalyzerConfiguration(),
            labels = emptyMap()
        )

        val analyzerResult = managerResult.toAnalyzerResult().withResolvedScopes()

        patchActualResult(analyzerResult.toYaml()) should matchExpectedResult(expectedResultFile, definitionFile)
    }
})

/**
 * Convert this [PackageManagerResult] to an [org.ossreviewtoolkit.model.AnalyzerResult], simulating what the
 * [org.ossreviewtoolkit.analyzer.Analyzer] does when assembling the results of a single package manager.
 */
private fun PackageManagerResult.toAnalyzerResult() =
    AnalyzerResultBuilder().apply {
        projectResults.values.flatten().forEach { addResult(it) }
        dependencyGraph?.let {
            addDependencyGraph(GradleInspectorFactory.descriptor.id, it).addPackages(sharedPackages)
        }
    }.build()
