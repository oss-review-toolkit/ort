/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.bundler

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlNamingStrategy

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal val YAML = Yaml(
    configuration = YamlConfiguration(strictMode = false, yamlNamingStrategy = YamlNamingStrategy.SnakeCase)
)

/**
 * The information for a Gem as typically defined in a ".gemspec" file, see
 * - https://guides.rubygems.org/specification-reference/
 * - https://github.com/rubygems/rubygems/blob/95128500fba28e3bb03d3bbfcf8536d00a893035/lib/rubygems/specification.rb#L19-L40
 */
@Serializable
internal data class GemSpec(
    /** The Gem’s name. */
    val name: String,

    /** The Gem’s specified version. */
    val version: Version,

    /** A long description of this Gem. */
    val description: String? = null,

    /** A short summary of this Gem’s description. */
    val summary: String? = null,

    /** The URI of this Gem’s homepage. */
    val homepage: String? = null,

    /** A list of authors for this Gem. */
    val authors: List<String> = emptyList(),

    /** The license(s) for the Gem. Each license must be a short name, no more than 64 characters. */
    val licenses: List<String> = emptyList(),

    /** The list of specified dependencies. */
    val dependencies: List<Dependency> = emptyList()
) {
    @Serializable
    data class Version(
        /** The version string, containing numbers and periods, such as "1.0.0.pre" for a prerelease. */
        val version: String
    )

    @Serializable
    data class Dependency(
        /** The name of the dependency. */
        val name: String,

        /** The type of the dependency as a Ruby symbol, one of ":development" or ":runtime". */
        val type: String
    )
}

/**
 * Version details for a specific Gem version, see
 * - https://guides.rubygems.org/rubygems-org-api-v2/
 * - https://github.com/rubygems/rubygems.org/blob/1f308c8d55403ccc04df407399bcafce87aa5016/app/models/rubygem.rb#L211-L239
 */
@Serializable
internal data class VersionDetails(
    /** The Gem’s name. */
    val name: String,

    /** The version string, containing numbers and periods, such as "1.0.0.pre" for a prerelease. */
    val version: String,

    /** A comma-separated list of authors for this Gem. */
    val authors: String? = null,

    /** A long description of this Gem. */
    val description: String? = null,

    /** A short summary of this Gem’s description. */
    val summary: String? = null,

    /** A synthetic field that contains either the descript or summary, if present. */
    val info: String? = null,

    /** The license(s) for the Gem. Each license must be a short name, no more than 64 characters. */
    val licenses: List<String> = emptyList(),

    /** The SHA256 hash of the Gem artifact. */
    val sha: String? = null,

    /** The download URI of the Gem artifact. */
    val gemUri: String? = null,

    /** The URI of this Gem’s homepage. */
    val homepageUri: String? = null,

    /** The URI of this Gem’s source code. */
    val sourceCodeUri: String? = null,

    /** A map of dependencies per scope. */
    val dependencies: Map<Scope, List<Dependency>> = emptyMap()
) {
    @Serializable
    enum class Scope {
        /** Dependencies required during development. */
        @SerialName("development")
        DEVELOPMENT,

        /** Dependencies required at runtime. */
        @SerialName("runtime")
        RUNTIME;

        /** A string representation as a Ruby symbol for convenient dependency type matching. */
        override fun toString() = ":${name.lowercase()}"
    }

    @Serializable
    data class Dependency(
        /** The name of the dependency Gem. */
        val name: String,

        /** The version requirements string of the dependency Gem. */
        val requirements: String? = null
    )
}
