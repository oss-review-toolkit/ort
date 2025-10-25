/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.ivy

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

/**
 * Data class representing an Ivy module descriptor (ivy.xml).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class IvyModule(
    @JacksonXmlProperty(isAttribute = true)
    val version: String? = null,

    val info: Info? = null,

    val configurations: Configurations? = null,

    val publications: Publications? = null,

    val dependencies: Dependencies? = null
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Info(
        @JacksonXmlProperty(isAttribute = true)
        val organisation: String? = null,

        @JacksonXmlProperty(isAttribute = true)
        val module: String? = null,

        @JacksonXmlProperty(isAttribute = true)
        val revision: String? = null,

        @JacksonXmlProperty(isAttribute = true)
        val status: String? = null,

        @JacksonXmlProperty(isAttribute = true)
        val publication: String? = null,

        val license: License? = null,

        val description: String? = null,

        val homepage: String? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class License(
        @JacksonXmlProperty(isAttribute = true)
        val name: String? = null,

        @JacksonXmlProperty(isAttribute = true)
        val url: String? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Configurations(
        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "conf")
        val conf: List<Conf>? = null
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Conf(
            @JacksonXmlProperty(isAttribute = true)
            val name: String? = null,

            @JacksonXmlProperty(isAttribute = true)
            val visibility: String? = null,

            @JacksonXmlProperty(isAttribute = true)
            val description: String? = null
        )
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Publications(
        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "artifact")
        val artifact: List<Artifact>? = null
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Artifact(
            @JacksonXmlProperty(isAttribute = true)
            val name: String? = null,

            @JacksonXmlProperty(isAttribute = true)
            val type: String? = null,

            @JacksonXmlProperty(isAttribute = true)
            val ext: String? = null,

            @JacksonXmlProperty(isAttribute = true)
            val conf: String? = null
        )
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Dependencies(
        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "dependency")
        val dependency: List<Dependency>? = null
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Dependency(
            @JacksonXmlProperty(isAttribute = true)
            val org: String? = null,

            @JacksonXmlProperty(isAttribute = true)
            val name: String? = null,

            @JacksonXmlProperty(isAttribute = true)
            val rev: String? = null,

            @JacksonXmlProperty(isAttribute = true)
            val conf: String? = null,

            @JacksonXmlProperty(isAttribute = true)
            val transitive: Boolean? = null
        )
    }
}
