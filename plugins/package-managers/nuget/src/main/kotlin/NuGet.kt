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

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.plugins.packagemanagers.nuget.utils.NuGetInspector
import org.ossreviewtoolkit.plugins.packagemanagers.nuget.utils.toOrtPackages
import org.ossreviewtoolkit.plugins.packagemanagers.nuget.utils.toOrtProject

/**
 * A package manager implementation for [.NET](https://docs.microsoft.com/en-us/dotnet/core/tools/) project files that
 * embed NuGet package configuration.
 */
class NuGet(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, "NuGet", analysisRoot, analyzerConfig, repoConfig) {
    companion object {
        const val OPTION_NUGET_CONFIG = "nugetConfigFile"
    }

    class Factory : AbstractPackageManagerFactory<NuGet>("NuGet") {
        override val globsForDefinitionFiles = listOf("*.csproj", "*.fsproj", "*.vcxproj", "packages.config")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = NuGet(type, analysisRoot, analyzerConfig, repoConfig)
    }

    private val nugetConfig = options[OPTION_NUGET_CONFIG]?.let { File(it) }

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        val result = NuGetInspector.inspect(definitionFile, nugetConfig)

        val project = result.toOrtProject(managerName, analysisRoot, definitionFile)
        val packages = result.dependencies.toOrtPackages()

        return listOf(ProjectAnalyzerResult(project, packages, collectTopLevelIssues(result)))
    }

    private fun collectTopLevelIssues(result: NuGetInspector.Result): List<Issue> {
        val errors = (result.headers.flatMap { it.errors } + result.packages.flatMap { it.errors }).map { message ->
            Issue(
                source = managerName,
                message = message,
                severity = Severity.ERROR
            )
        }

        val warnings = result.packages.flatMap { it.warnings }.map { message ->
            Issue(
                source = managerName,
                message = message,
                severity = Severity.WARNING
            )
        }

        return errors + warnings
    }
}
