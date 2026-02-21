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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

/**
 * Data class representing an Ivy module descriptor (ivy.xml).
 */
@Serializable
@XmlSerialName("ivy-module", "", "")
internal data class IvyModule(
    @XmlElement(false)
    @SerialName("version")
    val version: String? = null,

    val info: Info? = null,

    val configurations: Configurations? = null,

    val publications: Publications? = null,

    val dependencies: Dependencies? = null
) {
    @Serializable
    @XmlSerialName("info", "", "")
    data class Info(
        @XmlElement(false)
        @SerialName("organisation")
        val organisation: String? = null,

        @XmlElement(false)
        @SerialName("module")
        val module: String? = null,

        @XmlElement(false)
        @SerialName("revision")
        val revision: String? = null,

        @XmlElement(false)
        @SerialName("status")
        val status: String? = null,

        @XmlElement(false)
        @SerialName("publication")
        val publication: String? = null,

        // These are XML elements, not attributes
        val license: License? = null,

        @XmlElement(true)
        val description: String? = null,

        @XmlElement(true)
        val homepage: String? = null
    )

    @Serializable
    @XmlSerialName("license", "", "")
    data class License(
        @XmlElement(false)
        @SerialName("name")
        val name: String? = null,

        @XmlElement(false)
        @SerialName("url")
        val url: String? = null
    )

    @Serializable
    @XmlSerialName("configurations", "", "")
    data class Configurations(
        @SerialName("conf")
        val conf: List<Conf>? = null
    ) {
        @Serializable
        @XmlSerialName("conf", "", "")
        data class Conf(
            @XmlElement(false)
            @SerialName("name")
            val name: String? = null,

            @XmlElement(false)
            @SerialName("visibility")
            val visibility: String? = null,

            @XmlElement(false)
            @SerialName("description")
            val description: String? = null
        )
    }

    @Serializable
    @XmlSerialName("publications", "", "")
    data class Publications(
        @SerialName("artifact")
        val artifact: List<Artifact>? = null
    ) {
        @Serializable
        @XmlSerialName("artifact", "", "")
        data class Artifact(
            @XmlElement(false)
            @SerialName("name")
            val name: String? = null,

            @XmlElement(false)
            @SerialName("type")
            val type: String? = null,

            @XmlElement(false)
            @SerialName("ext")
            val ext: String? = null,

            @XmlElement(false)
            @SerialName("conf")
            val conf: String? = null
        )
    }

    @Serializable
    @XmlSerialName("dependencies", "", "")
    data class Dependencies(
        @SerialName("dependency")
        val dependency: List<Dependency>? = null
    ) {
        @Serializable
        @XmlSerialName("dependency", "", "")
        data class Dependency(
            @XmlElement(false)
            @SerialName("org")
            val org: String? = null,

            @XmlElement(false)
            @SerialName("name")
            val name: String? = null,

            @XmlElement(false)
            @SerialName("rev")
            val rev: String? = null,

            @XmlElement(false)
            @SerialName("conf")
            val conf: String? = null,

            @XmlElement(false)
            @SerialName("transitive")
            val transitive: Boolean? = null
        )
    }
}
