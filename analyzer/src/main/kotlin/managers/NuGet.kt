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
 * The [NuGet](https://www.nuget.org/) package manager for .NET.
 *
 * This package manager supports the following [options][PackageManagerConfiguration.options]:
 * - *directDependenciesOnly*: If true, only direct dependencies are reported. Defaults to false.
 */
class NuGet(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig) {
    class Factory : AbstractPackageManagerFactory<NuGet>("NuGet") {
        override val globsForDefinitionFiles = listOf("packages.config")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = NuGet(managerName, analysisRoot, analyzerConfig, repoConfig)
    }

    private val directDependenciesOnly =
        analyzerConfig.getPackageManagerConfiguration(managerName)?.options?.get(OPTION_DIRECT_DEPENDENCIES_ONLY)
            .toBoolean()

    private val reader = NuGetPackageFileReader()

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
 * A reader for XML-based NuGet package configuration files, see
 * https://docs.microsoft.com/en-us/nuget/reference/packages-config.
 */
class NuGetPackageFileReader : XmlPackageFileReader {
    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class PackagesConfig(
        @JsonProperty(value = "package")
        @JacksonXmlElementWrapper(useWrapping = false)
        val packages: List<Package>
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class Package(
        @JacksonXmlProperty(isAttribute = true)
        val id: String,
        @JacksonXmlProperty(isAttribute = true)
        val version: String,
        @JacksonXmlProperty(isAttribute = true)
        val targetFramework: String?,
        @JacksonXmlProperty(isAttribute = true)
        val developmentDependency: Boolean?
    )

    override fun getDependencies(definitionFile: File): Set<NuGetDependency> {
        val packagesConfig = NuGetSupport.XML_MAPPER.readValue<PackagesConfig>(definitionFile)

        return packagesConfig.packages.mapTo(mutableSetOf()) { pkg ->
            NuGetDependency(
                name = pkg.id,
                version = pkg.version,
                targetFramework = pkg.targetFramework.orEmpty(),
                developmentDependency = pkg.developmentDependency ?: false
            )
        }
    }
}
