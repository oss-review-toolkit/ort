/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

package org.ossreviewtoolkit.analyzer.managers

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.module.kotlin.readValue

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.managers.utils.XmlPackageReferenceMapper
import org.ossreviewtoolkit.analyzer.managers.utils.resolveDotNetDependencies
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration

import java.io.File

class DotNetPackageReferenceMapper : XmlPackageReferenceMapper() {
    // See https://docs.microsoft.com/en-us/nuget/consume-packages/package-references-in-project-files.
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ItemGroup(
        @JsonProperty(value = "PackageReference")
        @JacksonXmlElementWrapper(useWrapping = false)
        val packageReference: List<PackageReference>?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PackageReference(
        @JacksonXmlProperty(isAttribute = true, localName = "Include")
        val include: String,
        @JacksonXmlProperty(isAttribute = true, localName = "Version")
        val version: String
    )

    override fun mapPackageReferences(definitionFile: File): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val itemGroups = mapper.readValue<List<ItemGroup>>(definitionFile)

        itemGroups.forEach { itemGroup ->
            itemGroup.packageReference?.forEach {
                if (it.include.isNotEmpty()) {
                    map[it.include] = it.version
                }
            }
        }

        return map
    }
}

/**
 * The [DotNet](https://docs.microsoft.com/en-us/dotnet/core/tools/) package manager for .NET.
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

    override fun resolveDependencies(definitionFile: File): List<ProjectAnalyzerResult> =
        listOfNotNull(resolveDotNetDependencies(definitionFile, DotNetPackageReferenceMapper()))
}
