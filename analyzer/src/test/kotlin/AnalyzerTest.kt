/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.analyzer

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.should

import java.io.File
import java.io.IOException

import org.ossreviewtoolkit.analyzer.Analyzer.ManagedFileInfo
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.RepositoryConfiguration

class AnalyzerTest : WordSpec({
    "analyze()" should {
        "call afterResolution() even if resolveDependencies() throws" {
            val analyzerConfig = AnalyzerConfiguration()
            val repoConfig = RepositoryConfiguration()
            val analyzer = Analyzer(analyzerConfig)
            val analysisRoot = File(".").absoluteFile

            val manager = DummyPackageManager(analyzerConfig)

            val info = ManagedFileInfo(
                absoluteProjectPath = analysisRoot,
                managedFiles = mapOf(manager to listOf(analysisRoot.resolve("Dummy"))),
                repositoryConfiguration = repoConfig
            )

            analyzer.analyze(info)

            manager.calls should containExactly("beforeResolution", "resolveDependencies", "afterResolution")
        }
    }
})

private class DummyPackageManager(analyzerConfig: AnalyzerConfiguration) :
    PackageManager("Dummy", "Project", analyzerConfig) {
    override val globsForDefinitionFiles = emptyList<String>()
    val calls = mutableListOf<String>()

    override fun beforeResolution(analysisRoot: File, definitionFiles: List<File>) {
        calls += "beforeResolution"
    }

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        calls += "resolveDependencies"
        throw IOException()
    }

    override fun afterResolution(analysisRoot: File, definitionFiles: List<File>) {
        calls += "afterResolution"
    }
}
