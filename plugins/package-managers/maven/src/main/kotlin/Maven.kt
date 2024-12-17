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

package org.ossreviewtoolkit.plugins.packagemanagers.maven

import java.io.File
import java.util.Properties

import org.apache.maven.shared.invoker.DefaultInvocationRequest
import org.apache.maven.shared.invoker.DefaultInvoker
import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.ort.createOrtTempDir
import org.ossreviewtoolkit.utils.ort.downloadFile
import org.ossreviewtoolkit.utils.ort.okHttpClient

/**
 * The [Maven](https://maven.apache.org/) package manager for Java.
 */
class Maven(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, "Maven", analysisRoot, analyzerConfig, repoConfig) {
    class Factory : AbstractPackageManagerFactory<Maven>("Maven") {
        override val globsForDefinitionFiles = listOf("pom.xml")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Maven(type, analysisRoot, analyzerConfig, repoConfig)
    }

    init {
        // Get the Maven plugin JAR.
        val downloadDir = createOrtTempDir()
        okHttpClient.downloadFile("https://repo1.maven.org/maven2/org/cyclonedx/cyclonedx-maven-plugin/2.8.1/cyclonedx-maven-plugin-2.8.1.jar", downloadDir)

        val request = DefaultInvocationRequest().apply {
            isBatchMode = true
            goals = listOf("org.apache.maven.plugins:maven-install-plugin:3.1.3:install-file")
            properties = Properties().apply { this.setProperty("file", "$downloadDir/cyclonedx-maven-plugin-2.8.1.jar") }
        }

        val invoker = DefaultInvoker()
        val result = invoker.execute(request)
        require(result.exitCode == 0)

        downloadDir.safeDeleteRecursively()
    }

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        val request = DefaultInvocationRequest().apply {
            pomFile = definitionFile
            isBatchMode = true
            goals = listOf("org.cyclonedx:cyclonedx-maven-plugin:2.8.1:makeAggregateBom")
        }

        val invoker = DefaultInvoker()
        val result = invoker.execute(request)
        require(result.exitCode == 0)

        val bom = definitionFile.resolveSibling("target/bom.json")
        require(bom.isFile)

        return listOf(ProjectAnalyzerResult(Project.EMPTY, packages = emptySet()))
    }
}
