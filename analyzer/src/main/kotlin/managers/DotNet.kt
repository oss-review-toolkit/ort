/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
 * Copyright (C) 2022 Bosch.IO GmbH
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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.module.kotlin.readValue

import java.io.File

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.managers.utils.NuGetDependency
import org.ossreviewtoolkit.analyzer.managers.utils.NuGetSupport
import org.ossreviewtoolkit.analyzer.managers.utils.OPTION_DIRECT_DEPENDENCIES_ONLY
import org.ossreviewtoolkit.analyzer.managers.utils.XmlPackageFileReader
import org.ossreviewtoolkit.analyzer.managers.utils.resolveNuGetDependencies
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration

/**
 * A package manager implementation for [.NET](https://docs.microsoft.com/en-us/dotnet/core/tools/) project files that
 * embed NuGet package configuration.
 *
 * This package manager supports the following [options][PackageManagerConfiguration.options]:
 * - *directDependenciesOnly*: If true, only direct dependencies are reported. Defaults to false.
 */
class DotNet(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig) {
    class Factory : AbstractPackageManagerFactory<DotNet>("DotNet") {
        override val globsForDefinitionFiles = listOf("*.csproj", "*.fsproj", "*.vcxproj")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = DotNet(managerName, analysisRoot, analyzerConfig, repoConfig)
    }

    private val directDependenciesOnly =
        analyzerConfig.getPackageManagerConfiguration(managerName)?.options?.get(OPTION_DIRECT_DEPENDENCIES_ONLY)
            .toBoolean()

    private val reader = DotNetPackageFileReader()

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> =
        listOf(
            resolveNuGetDependencies(
                definitionFile,
                reader,
                NuGetSupport.create(definitionFile),
                directDependenciesOnly
            )
        )
}

/**
 * A reader for XML-based .NET project files that embed NuGet package configuration, see
 * https://docs.microsoft.com/en-us/nuget/consume-packages/package-references-in-project-files.
 */
class DotNetPackageFileReader : XmlPackageFileReader {
    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class PropertyGroup(
        @JsonProperty(value = "TargetFramework")
        val targetFramework: String?,
        @JsonProperty(value = "TargetFrameworks")
        val targetFrameworks: String?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class ItemGroup(
        @JacksonXmlProperty(isAttribute = true, localName = "Condition")
        val condition: String?,
        @JsonProperty(value = "PackageReference")
        @JacksonXmlElementWrapper(useWrapping = false)
        val packageReferences: List<PackageReference>?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class PackageReference(
        @JacksonXmlProperty(isAttribute = true, localName = "Condition")
        val condition: String?,
        @JacksonXmlProperty(isAttribute = true, localName = "Include")
        val include: String,
        @JacksonXmlProperty(isAttribute = true, localName = "Version")
        val version: String,
        @JsonProperty(value = "PrivateAssets")
        val privateAssets: String?
    )

    override fun getDependencies(definitionFile: File): Set<NuGetDependency> {
        // TODO: Find a way to parse both collections at once to not have to parse the file twice. The problem with
        //       adding propertyGroups and itemGroups to a parent object is that the elements can be mixed and in this
        //       case Jackson only returns the last match for each list.
        val propertyGroups = NuGetSupport.XML_MAPPER.readValue<List<PropertyGroup>>(definitionFile)
        val itemGroups = NuGetSupport.XML_MAPPER.readValue<List<ItemGroup>>(definitionFile)

        val targetFrameworks = propertyGroups.find { it.targetFramework != null }?.targetFramework?.let { listOf(it) }
            ?: propertyGroups.find { it.targetFrameworks != null }?.targetFrameworks?.split(";")
            ?: listOf("")

        fun conditionMatchesTargetFramework(condition: String?, targetFramework: String) =
            condition == null || targetFramework.isEmpty() || condition.contains(targetFramework)

        return targetFrameworks.distinct().flatMapTo(mutableSetOf()) { targetFramework ->
            itemGroups.filter { conditionMatchesTargetFramework(it.condition, targetFramework) }.flatMap { itemGroup ->
                itemGroup.packageReferences?.filter { conditionMatchesTargetFramework(it.condition, targetFramework) }
                    ?.map { packageReference ->
                        NuGetDependency(
                            name = packageReference.include,
                            version = packageReference.version,
                            targetFramework = targetFramework,
                            developmentDependency = packageReference.privateAssets?.lowercase() == "all"
                        )
                    }.orEmpty()
            }
        }
    }
}
