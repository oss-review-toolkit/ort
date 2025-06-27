/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.nuget

import java.io.File

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.packagemanagers.nuget.utils.NuGetInspector
import org.ossreviewtoolkit.plugins.packagemanagers.nuget.utils.toOrtPackages
import org.ossreviewtoolkit.plugins.packagemanagers.nuget.utils.toOrtProject

data class NuGetConfig(
    /**
     * The path to the NuGet configuration file to use.
     */
    val nugetConfigFile: String?
)

/**
 * A package manager implementation for [.NET](https://docs.microsoft.com/en-us/dotnet/core/tools/) project files that
 * embed NuGet package configuration.
 */
@OrtPlugin(
    displayName = "NuGet",
    description = "The NuGet package manager for .NET.",
    factory = PackageManagerFactory::class
)
class NuGet(override val descriptor: PluginDescriptor = NuGetFactory.descriptor, config: NuGetConfig) :
    PackageManager("NuGet") {
    override val globsForDefinitionFiles = listOf("*.csproj", "*.fsproj", "*.vcxproj", "packages.config")

    private val nugetConfig = config.nugetConfigFile?.let { File(it) }

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        val result = NuGetInspector.inspect(definitionFile, nugetConfig)

        val project = result.toOrtProject(projectType, analysisRoot, definitionFile)
        val packages = result.dependencies.toOrtPackages()

        return listOf(ProjectAnalyzerResult(project, packages, collectTopLevelIssues(result)))
    }

    private fun collectTopLevelIssues(result: NuGetInspector.Result): List<Issue> {
        val errors = (result.headers.flatMap { it.errors } + result.packages.flatMap { it.errors }).map { message ->
            createAndLogIssue(message, Severity.ERROR)
        }

        val warnings = result.packages.flatMap { it.warnings }.map { message ->
            createAndLogIssue(message, Severity.WARNING)
        }

        return errors + warnings
    }
}
