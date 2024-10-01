/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

import org.ossreviewtoolkit.utils.common.AlphaNumericComparator
import org.ossreviewtoolkit.utils.common.encodeOr

/**
 * A unique identifier for a software component.
 */
data class Identifier(
    /**
     * The type of component this identifier describes. When used in the context of a [Project], the type equals the one
     * of the package manager that manages the project (e.g. "Gradle" for a Gradle project). When used in the context of
     * a [Package], the type is the name of the artifact ecosystem (e.g. "Maven" for a file from a Maven repository).
     */
    val type: String,

    /**
     * The namespace of the component, for example the group for "Maven" or the scope for "NPM".
     */
    val namespace: String,

    /**
     * The name of the component.
     */
    val name: String,

    /**
     * The version of the component.
     */
    val version: String
) : Comparable<Identifier> {
    companion object {
        /**
         * A constant for an [Identifier] where all properties are empty strings.
         */
        @JvmField
        val EMPTY = Identifier(
            type = "",
            namespace = "",
            name = "",
            version = ""
        )

        // This comparator is consistent with `equals()` as all properties are taken into account.
        private val COMPARATOR = compareBy<Identifier>({ it.type }, { it.namespace }, { it.name })
            .thenComparing({ it.version }, AlphaNumericComparator)
    }

    private constructor(properties: List<String>) : this(
        type = properties.getOrElse(0) { "" },
        namespace = properties.getOrElse(1) { "" },
        name = properties.getOrElse(2) { "" },
        version = properties.getOrElse(3) { "" }
    )

    /**
     * Create an [Identifier] from a string with the format "type:namespace:name:version". If the string has less than
     * three colon separators the missing values are assigned empty strings.
     */
    @JsonCreator
    constructor(identifier: String) : this(identifier.split(':', limit = 4))

    private val sanitizedProperties = listOf(type, namespace, name, version).map { component ->
        component.trim().filterNot { it < ' ' }
    }

    init {
        require(sanitizedProperties.none { ":" in it }) {
            "An identifier's properties must not contain ':' because that character is used as a separator in the " +
                "string representation: type='$type', namespace='$namespace', name='$name', version='$version'."
        }
    }

    override fun compareTo(other: Identifier) = COMPARATOR.compare(this, other)

    /**
     * Return whether this [Identifier] is likely to belong any of the organizations mentioned in [names] by looking at
     * the [namespace].
     */
    fun isFromOrg(vararg names: String) =
        names.any { name ->
            val lowerName = name.lowercase()
            val vendorNamespace = when (type) {
                "NPM" -> "@$lowerName"
                "Gradle", "Maven", "SBT" -> "((com|io|net|org)\\.)?$lowerName(\\..+)?"
                else -> ""
            }

            // TODO: Think about how to handle package managers that do not have the concept of namespaces, like Cargo.
            vendorNamespace.isNotEmpty() && namespace.matches(vendorNamespace.toRegex())
        }

    /**
     * Create Maven-like coordinates based on the properties of the [Identifier].
     */
    @JsonValue
    fun toCoordinates() = sanitizedProperties.joinToString(":")

    /**
     * Create a file system path based on the properties of the [Identifier]. All properties are encoded using
     * [encodeOr] with [emptyValue] as parameter.
     */
    fun toPath(separator: String = "/", emptyValue: String = "unknown"): String =
        sanitizedProperties.joinToString(separator) { it.encodeOr(emptyValue) }
}
