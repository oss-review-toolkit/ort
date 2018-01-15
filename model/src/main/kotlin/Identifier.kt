/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

package com.here.ort.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * A unique identifier for a software package.
 */
data class Identifier(
        /**
         * The name of the package manager that was used to discover this package, for example Maven or NPM.
         */
        @JsonProperty("package_manager")
        val packageManager: String,

        /**
         * The namespace of the package, for example the group id in Maven or the scope in NPM.
         */
        val namespace: String,

        /**
         * The name of the package.
         */
        val name: String,

        /**
         * The version of the package.
         */
        val version: String
) {
    companion object {
        /**
         * A constant for a [Identifier] where all properties are empty strings.
         */
        @JvmField
        val EMPTY = Identifier(
                packageManager = "",
                namespace = "",
                name = "",
                version = ""
        )

        fun fromString(identifier: String): Identifier {
            val list = identifier.split(':')
            return Identifier(
                    packageManager = list.getOrNull(0) ?: "",
                    namespace = list.getOrNull(1) ?: "",
                    name = list.getOrNull(2) ?: "",
                    version = list.getOrNull(3) ?: ""
            )
        }
    }

    /**
     * Returns true if this matches the provided identifier. To match both identifiers need to have the same
     * [packageManager] and [namespace], and the [name] and [version] must be either equal or empty for at least one of
     * them.
     *
     * Examples for matching identifiers:
     * * "maven:org.hamcrest:hamcrest-core:1.3" <-> "maven:org.hamcrest:hamcrest-core:"
     * * "maven:org.hamcrest:hamcrest-core:1.3" <-> "maven:org.hamcrest::1.3"
     * * "maven:org.hamcrest:hamcrest-core:1.3" <-> "maven:org.hamcrest::"
     *
     * Examples for not matching identifiers:
     * * "maven:org.hamcrest:hamcrest-core:1.3" <-> "maven:org.hamcrest:hamcrest-core:1.2"
     * * "maven:org.hamcrest:hamcrest-core:" <-> "maven:org.hamcrest:hamcrest-library:"
     */
    fun matches(identifier: Identifier): Boolean {
        if (packageManager != identifier.packageManager) {
            return false
        }

        if (namespace != identifier.namespace) {
            return false
        }

        val nameMatches = name == identifier.name || name.isBlank() || identifier.name.isBlank()
        val versionMatches = version == identifier.version || version.isBlank() || identifier.version.isBlank()

        return nameMatches && versionMatches
    }

    override fun toString(): String = "$packageManager:$namespace:$name:$version"
}
