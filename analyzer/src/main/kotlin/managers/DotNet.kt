/*
 * Copyright (C) 2019 Bosch Software Innovations
 * Based on:
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.analyzer.managers

import com.here.ort.analyzer.AbstractPackageManagerFactory
import com.here.ort.analyzer.DotNetSupport
import com.here.ort.analyzer.PackageManager
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.Identifier
import com.here.ort.model.Project
import com.here.ort.model.ProjectAnalyzerResult
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.model.xmlMapper
import java.io.File

/**
 * Dotnet package manager
 */
open class DotNet(name: String, analyzerConfig: AnalyzerConfiguration, repoConfig: RepositoryConfiguration) :
        PackageManager(name ,analyzerConfig, repoConfig)/*, CommandLineTool */ {

    class Factory : AbstractPackageManagerFactory<DotNet>("DotNet") {
        override val globsForDefinitionFiles = listOf("dotnet.xml")

        override fun create(analyzerConfig: AnalyzerConfiguration, repoConfig: RepositoryConfiguration) =
                DotNet(managerName, analyzerConfig, repoConfig)
    }

    override fun resolveDependencies(definitionFile: File): ProjectAnalyzerResult? {
        val workingDir = definitionFile.parentFile

        val dotnet = DotNetSupport(makeMapOfCsprojPackageReference(workingDir), workingDir)

        val vcsInfo = VersionControlSystem.getPathInfo(workingDir)

        val project = Project(
                id = Identifier(
                        type = "nuget",
                        namespace = "",
                        name = workingDir.name,
                        version = ""
                ),
                definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                declaredLicenses = sortedSetOf(),
                vcs = vcsInfo,
                vcsProcessed = vcsInfo.normalize(),
                homepageUrl = "",
                scopes = sortedSetOf(dotnet.scope)
        )

        return ProjectAnalyzerResult(
                project,
                packages = dotnet.packages.values.map { it.toCuratedPackage() }.toSortedSet(),
                errors = dotnet.errors
        )
    }

    private fun makeMapOfCsprojPackageReference(workingDir: File): Map<String, String> {
        val mapOfCsProjectFileReferences = mutableMapOf<String, String>()

        workingDir.walkTopDown().filter {
            it.name.endsWith(".csproj")
        }.forEach {
            it.forEachLine { line: String ->
                if (line.contains("PackageReference") || line.contains("Reference")) {
                    val lineNode = xmlMapper.readTree(line)
                    mapOfCsProjectFileReferences[lineNode["Include"].textValue() ?: ""] =
                            lineNode["Version"].textValue() ?: ""
                }
            }
        }
        return mapOfCsProjectFileReferences
    }
}
