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

@file:Suppress("MatchingDeclarationName")

package org.ossreviewtoolkit.plugins.reporters.aosd

import java.io.File
import java.net.URL

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

/**
 * The JSON format for importing product dependencies into AOSD 2.0, see https://www.aosd.cloud.audi/jsonschemadoc/.
 */
@Serializable
internal data class AOSD20(
    /** The reference to the official JSON schema. */
    @EncodeDefault
    @SerialName("\$schema")
    val schema: String = "https://www.aosd.cloud.audi/jsonschemadoc/static/aosd.schema.json",
    /** Ids of dependencies directly related to the project. */
    val directDependencies: List<String>,
    /** Description of the products to be evaluated by AOSD. */
    val dependencies: List<ExternalDependency>
) {
    @Serializable
    data class ExternalDependency(
        /** Unique identifier of the dependency. */
        val id: String,
        /** Product name of the dependency. */
        val name: String,
        /** Source code repository of the dependency. */
        val scmUrl: String? = null,
        /** Short description. */
        val description: String? = null,
        /** The exact version number. */
        val version: String,
        /** The possible version range of the dependency. */
        val versionRange: String? = null,
        /** Indication of the licenses under which the software may be used. All licenses are cumulative (and). */
        val licenses: List<License>,

        /**
         * The part description is optional for dividing the dependency into smaller pieces, e.g. for additional
         * license or usage information if necessary.
         */
        val parts: List<Part> = emptyList(),

        /** A deploy package represents a binary package, e.g. for a target platform. */
        val deployPackage: DeployPackage,

        /**
         * Provide the ability to model dependencies between two parts of the software. The precondition is that both
         * parts have been described in the JSON.
         */
        val internalDependencies: List<InternalDependency>? = null,

        /** References to the IDs of the dependencies which are required by this one. */
        val externalDependencies: List<String>? = null
    )

    /**
     * Indication of the licenses under which the software may be used. All licenses are cumulative (and).
     */
    @Serializable
    data class License(
        /** Full name of the license. */
        val name: String? = null,
        /** SPDX license identifier from https://spdx.org/licenses/. */
        val spdxId: String? = null,
        /** The license text. */
        val text: String,
        /** The URL where the license is published and can be read. */
        val url: String? = null,
        /** The copyrights associated with the license. */
        val copyrights: Copyrights? = null,
        /** The origin of the license information. */
        val origin: Origin? = null
    )

    @Serializable
    data class Copyrights(
        val holders: List<String>? = null,
        val notice: String? = null
    )

    /**
     * The origin of the license information.
     */
    @Serializable
    enum class Origin {
        @SerialName("packagemanagement")
        PACKAGE_MANAGEMENT,
        @SerialName("scm")
        SCM,
        @SerialName("licensefile")
        LICENSE_FILE
    }

    /**
     * A part represents a library that is available in the version and can be used separately by third parties. This is
     * not a dependency of the product.
     */
    @Serializable
    data class Part(
        val name: String,
        val description: String? = null,
        /** WARNING: Features are not yet implemented! */
        val features: List<String>? = null,
        val providers: List<Provider>,
        /** True, if the part is the work of a third party. */
        val external: Boolean? = null
    )

    /**
     * If the version has been divided into individual parts, then these can be made available for third-party use by
     * providing the usage features and license information if necessary (e.g. dual licensing).
     */
    @Serializable
    data class Provider(
        @EncodeDefault
        val additionalLicenses: List<License> = emptyList(),
        @EncodeDefault
        val modified: Boolean = false,
        @EncodeDefault
        val usage: Usage = Usage.DYNAMIC_LINKING
    )

    @Serializable
    enum class Usage {
        @SerialName("dynamic_linking")
        DYNAMIC_LINKING,
        @SerialName("static_linking")
        STATIC_LINKING,
        @SerialName("sys_call_dyn_link")
        SYS_CALL_DYN_LINK,
        @SerialName("sys_call_process")
        SYS_CALL_PROCESS
    }

    /**
     * A deploy package represents a binary package e.g. for a target platform.
     */
    @Serializable
    data class DeployPackage(
        /** Name or identifier of the variant. */
        val name: String,
        /** Information about the download location of the variant (binary). */
        val downloadUrl: String? = null,
        /** The variant (binary) of the product can be verified using the given (file-)checksum. */
        val checksums: Checksums? = null,
        /** Information on the custom disclaimer of the software. */
        val disclaimer: String? = null
    )

    /**
     * The variant (binary) of the product can be verified using the given (file-)checksum.
     */
    @Serializable
    data class Checksums(
        val md5: String? = null,
        val sha1: String? = null,
        val sha256: String? = null,
        val integrity: String? = null
    )

    @Serializable
    data class InternalDependency(
        /** Name of the software part that requires the dependency. */
        val from: String,
        /** Name of the software part, which is required as dependency. */
        val to: String
    )
}

private val JSON = Json.Default

internal fun File.writeReport(model: AOSD20): File = apply { outputStream().use { JSON.encodeToStream(model, it) } }

internal fun URL.readAosd20Report(): AOSD20 = openStream().use { JSON.decodeFromStream<AOSD20>(it) }
