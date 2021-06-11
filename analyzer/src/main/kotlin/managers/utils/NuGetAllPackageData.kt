/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

package org.ossreviewtoolkit.analyzer.managers.utils

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

internal class NuGetAllPackageData(
    val data: PackageData,
    val details: PackageDetails,
    val spec: PackageSpec
) {
    // See https://docs.microsoft.com/en-us/nuget/api/service-index.
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ServiceIndex(
        val version: String,
        val resources: List<ServiceResource>
    )

    // See https://docs.microsoft.com/en-us/nuget/api/service-index#resource.
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ServiceResource(
        @JsonProperty("@id")
        val id: String,
        @JsonProperty("@type")
        val type: String
    )

    // See https://docs.microsoft.com/en-us/nuget/api/registration-base-url-resource.
    @JsonIgnoreProperties(ignoreUnknown = true)
    class PackageData(
        val catalogEntry: String,
        val packageContent: String
    )

    // See https://docs.microsoft.com/en-us/nuget/api/catalog-resource#package-details-catalog-items.
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PackageDetails(
        // Mandatory fields.
        val id: String,
        val version: String,
        val packageHash: String,
        val packageHashAlgorithm: String,
        val packageSize: Int,

        // Optional fields.
        val description: String? = null,
        val projectUrl: String? = null,
        val dependencyGroups: List<DependencyGroup> = emptyList()
    )

    // See https://docs.microsoft.com/en-us/nuget/api/registration-base-url-resource#package-dependency-group.
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class DependencyGroup(
        val targetFramework: String? = null,
        val dependencies: List<Dependency> = emptyList()
    )

    // See https://docs.microsoft.com/en-us/nuget/api/registration-base-url-resource#package-dependency.
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Dependency(
        val id: String,

        // See https://docs.microsoft.com/en-us/nuget/concepts/package-versioning#version-ranges.
        val range: String
    )

    // See https://docs.microsoft.com/en-us/nuget/reference/nuspec.
    data class PackageSpec(
        val metadata: MetaData
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class MetaData(
        // See https://docs.microsoft.com/en-us/nuget/reference/nuspec#required-metadata-elements
        val id: String,
        val version: String,
        val description: String,
        val authors: String,

        // See https://docs.microsoft.com/en-us/nuget/reference/nuspec#optional-metadata-elements.
        val licenseUrl: String? = null,
        val license: License? = null,
        val repository: Repository? = null
    )

    // See https://docs.microsoft.com/en-us/nuget/reference/nuspec#license.
    data class License(
        @JacksonXmlProperty(isAttribute = true)
        val type: String,
        @JacksonXmlProperty(isAttribute = true)
        val version: String? = null,

        // The (non-attribute) tag value.
        val value: String
    )

    // See https://docs.microsoft.com/en-us/nuget/reference/nuspec#repository.
    data class Repository(
        @JacksonXmlProperty(isAttribute = true)
        val type: String? = null,
        @JacksonXmlProperty(isAttribute = true)
        val url: String? = null,
        @JacksonXmlProperty(isAttribute = true)
        val branch: String? = null,
        @JacksonXmlProperty(isAttribute = true)
        val commit: String? = null
    )
}
