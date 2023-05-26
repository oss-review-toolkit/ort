/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.reporters.gitlab

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer

private const val GITLAB_LICENSE_SCANNING_SCHEMA_VERSION_MAJOR_MINOR = "2.1"

/**
 * The GitLab license model according to
 * https://gitlab.com/gitlab-org/security-products/license-management/-/blob/f4ec1f1bf826654ab963d32a2d4a2588ecb91c04/spec/fixtures/schema/v2.1.json,
 * and to the examples under
 * https://gitlab.com/gitlab-org/security-products/license-management/-/tree/f4ec1f1bf826654ab963d32a2d4a2588ecb91c04/spec/fixtures/expected.
 */
@Serializable
internal data class GitLabLicenseModel(
    /**
     * The schema version of this model, must equal [GITLAB_LICENSE_SCANNING_SCHEMA_VERSION_MAJOR_MINOR].
     */
    val version: String = GITLAB_LICENSE_SCANNING_SCHEMA_VERSION_MAJOR_MINOR,

    /**
     * The complete set of licenses referred to by [dependencies].
     */
    @Serializable(SortedLicenseCollectionSerializer::class)
    @Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
    val licenses: Set<License>,

    /**
     * The list of all dependencies.
     */
    @Serializable(SortedDependenciesCollectionSerializer::class)
    @Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
    val dependencies: List<Dependency>
) {
    @Serializable
    data class License(
        /**
         * The SPDX identifier of the license.
         */
        val id: String,

        /**
         * The full name of the license.
         */
        val name: String,

        /**
         * The URL for this license.
         */
        val url: String
    )

    @Serializable
    data class Dependency(
        /**
         * The name of the dependency.
         */
        val name: String,

        /**
         * The version of the dependency.
         */
        val version: String,

        /**
         * The package manager corresponding to the project which depends on this dependency.
         */
        val packageManager: String,

        /**
         * A comma separate list of the definition file paths of all projects depending on this dependency.
         */
        val path: String,

        /**
         * The declared licenses of this dependency.
         */
        @Serializable(SortedStringCollectionSerializer::class)
        @Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
        val licenses: Set<String>
    )
}

private class SortedDependenciesCollectionSerializer :
    KSerializer<Collection<GitLabLicenseModel.Dependency>> by sortedCollectionSerializer(
        compareBy({ it.packageManager }, { it.name }, { it.version })
    )

private class SortedLicenseCollectionSerializer :
    KSerializer<Collection<GitLabLicenseModel.License>> by sortedCollectionSerializer(compareBy { it.id })

private class SortedStringCollectionSerializer :
    KSerializer<Collection<String>> by sortedCollectionSerializer(compareBy { it })

private inline fun <reified T> sortedCollectionSerializer(comparator: Comparator<in T>): KSerializer<Collection<T>> {
    val delegate = serializer<Collection<T>>()
    return object : KSerializer<Collection<T>> by delegate {
        override fun serialize(encoder: Encoder, value: Collection<T>) =
             delegate.serialize(encoder, value.sortedWith(comparator))
    }
}
