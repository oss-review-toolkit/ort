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

import io.ks3.standard.sortedSetSerializer

import java.io.File
import java.net.URL

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

import org.ossreviewtoolkit.utils.spdx.toSpdx

const val FIRST_SUBCOMPONENT_NAME = "main"

/** The JSON format for automated and manual import for software components and their dependencies into AOSD. */
@Serializable
internal data class AOSD21(
    /** Semantic version of the schema. */
    val schemaVersion: String = "2.1.0",

    /** A label that can be used link an external ID for search and identification of custom information. */
    val externalId: String = "",

    /**
     *  A flag to mark if the software component information is the output from a scan tool like Black Duck Deep License
     *  Scan, ScanCode, Fossology etc., or if it was prepared manually.
     */
    val scanned: Boolean = true,

    /** A set with IDs of top level dependencies directly related to the product. */
    val directDependencies: Set<Long>,

    /** A set with all software components used in this product. */
    @Serializable(SortedComponentSetSerializer::class)
    val components: Set<Component>
) {
    fun validate(): AOSD21 =
        apply {
            require(directDependencies.isNotEmpty()) {
                "A product must have at least one direct dependency."
            }

            require(components.isNotEmpty()) {
                "A product must have at least one component."
            }
        }

    /** All needed information about a software component. */
    @Serializable
    data class Component(
        /** The unique id of the component in this file. */
        val id: Long,

        /** The name of the software component. */
        val componentName: String,

        /** The exact version of the software component. */
        val componentVersion: String,

        /** The URL for the source code repository / alternatively the homepage URL for the component. */
        val scmUrl: String,

        /**
         * Information about modification of source code of the component. Only relevant for licenses with conditions
         * for modifications.
         */
        val modified: Boolean?,

        /**
         * Information about the linking type of this component with its higher level code. Only relevant for licenses
         * with conditions for linking.
         */
        val linking: String?,

        /** The component IDs of transitive dependencies of the product. */
        val transitiveDependencies: Set<Long>,

        /**
         * A list with all subcomponents of the specific software component. The first subcomponent in every component
         * block must be named "main" and refer to the main license of the component.
         */
        val subcomponents: List<Subcomponent>
    ) {
        fun validate(): Component =
            apply {
                require(id >= 0L) {
                    "A component's ID must not be negative, but it is $id."
                }

                require(componentName.isNotEmpty()) {
                    "A component's name length must not be empty."
                }

                require(componentVersion.length in 1..50) {
                    "A component's version length must be in range 1..50, but '$componentVersion' has a length of " +
                        "${componentVersion.length}."
                }

                require(scmUrl.length >= 5) {
                    "The SCM or homepage URL must have a length of at least 5, but '$scmUrl' has a length of " +
                        "${scmUrl.length}."
                }

                require(subcomponents.isNotEmpty()) {
                    "A component must have at least one subcomponent."
                }

                val firstSubcomponentName = subcomponents.first().subcomponentName
                require(firstSubcomponentName == FIRST_SUBCOMPONENT_NAME) {
                    "The first subcomponent must be named 'main', but is it named '$firstSubcomponentName'."
                }
            }
    }

    /**
     * A subcomponent, which is a finding in a software component with license and / or copyright information (sometimes
     * also referred to as part). Usually there is a main license of the component and further subcomponent licenses in
     * individual directories or files of the component.
     */
    @Serializable
    data class Subcomponent(
        /** The name of the subcomponent. */
        val subcomponentName: String,

        /** The SPDX license expression of the subcomponent. Supports ScanCode LicenseRefs. */
        val spdxId: String,

        /** A list with all copyrights that are linked to this license subcomponent. */
        val copyrights: List<String>,

        /** A list with all authors that are related to this subcomponent's license. */
        val authors: List<String>,

        /**
         * The complete license text or permission note that was found for this specific subcomponent in the source
         * code. Make sure to include the individualized license text if the specific license provides such variable
         * parts e.g. BSD-3-Clause in clause 3 and disclaimer.
         */
        val licenseText: String,

        /** The URL to the license if the license information is not the result of a file level scan. */
        val licenseTextUrl: String,

        /**
         * The SPDX license expression of the selected / chosen license in case of alternative licensing for the
         * subcomponent. If empty, the license has to be selected in AOSD later on.
         */
        val selectedLicense: String,

        /** This field is not for any specific information but can be used for notes regarding the licenses, e.g. about
         * whether the license text is a permission note.
         */
        val additionalLicenseInfos: String = ""
    ) {
        fun validate(): Subcomponent =
            apply {
                require(subcomponentName.isNotEmpty()) {
                    "A subcomponent's name must not be empty."
                }

                require(spdxId.isNotEmpty()) {
                    "A subcomponent's SPDX ID must not be empty."
                }

                require(licenseText.length >= 20) {
                    "A subcomponent's license text must have a length of at least 20, but '$licenseText' has a " +
                        "length of ${licenseText.length}."
                }

                require(selectedLicense.isEmpty() || spdxId.toSpdx().isValidChoice(selectedLicense.toSpdx())) {
                    "The selected license '$selectedLicense' is not a valid choice for '$spdxId'."
                }
            }
    }
}

private val JSON = Json { encodeDefaults = true }

private object SortedComponentSetSerializer : KSerializer<Set<AOSD21.Component>> by sortedSetSerializer(
    compareBy { it.componentName }
)

internal fun File.writeReport(model: AOSD21): File = apply { outputStream().use { JSON.encodeToStream(model, it) } }

internal fun URL.readAosd21Report(): AOSD21 = openStream().use { JSON.decodeFromStream<AOSD21>(it) }
