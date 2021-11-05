/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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
import org.ossreviewtoolkit.analyzer.managers.utils.NuGetSupport
import org.ossreviewtoolkit.analyzer.managers.utils.XmlPackageFileReader
import org.ossreviewtoolkit.analyzer.managers.utils.resolveNuGetDependencies
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration

/**
 * A package manager implementation for [.NET](https://docs.microsoft.com/en-us/dotnet/core/tools/) project files that
 * embed NuGet package configuration.
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

    private val reader = DotNetPackageFileReader()

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> =
        listOf(resolveNuGetDependencies(definitionFile, reader, NuGetSupport.create(definitionFile)))
}

/**
 * A reader for XML-based .NET project files that embed NuGet package configuration, see
 * https://docs.microsoft.com/en-us/nuget/consume-packages/package-references-in-project-files.
 */
class DotNetPackageFileReader : XmlPackageFileReader {
    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class ItemGroup(
        @JsonProperty(value = "PackageReference")
        @JacksonXmlElementWrapper(useWrapping = false)
        val packageReference: List<PackageReference>?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class PackageReference(
        @JacksonXmlProperty(isAttribute = true, localName = "Include")
        val include: String,
        @JacksonXmlProperty(isAttribute = true, localName = "Version")
        val version: String
    )

    override fun getPackageReferences(definitionFile: File): Set<Identifier> {
        val ids = mutableSetOf<Identifier>()
        val itemGroups = NuGetSupport.XML_MAPPER.readValue<List<ItemGroup>>(definitionFile)

        itemGroups.forEach { itemGroup ->
            itemGroup.packageReference?.forEach {
                ids += Identifier(type = "NuGet", namespace = "", name = it.include, version = it.version)
            }
        }

        return ids
    }
}
