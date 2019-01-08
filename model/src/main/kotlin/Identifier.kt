/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer

import com.here.ort.utils.encodeOrUnknown

/**
 * A unique identifier for a software package.
 */
data class Identifier(
        /**
         * The type of package, i.e. its packaging type, for example "Maven" or "NPM".
         */
        val type: String,

        /**
         * The namespace of the package, for example the group for "Maven" or the scope for "NPM".
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
    }

    private constructor(components: List<String>) : this(
            type = components.getOrElse(0) { "" },
            namespace = components.getOrElse(1) { "" },
            name = components.getOrElse(2) { "" },
            version = components.getOrElse(3) { "" }
    )

    /**
     * Create an [Identifier] from a string with the format "type:namespace:name:version". If the string has less than
     * three colon separators the missing values are assigned empty strings.
     */
    @JsonCreator
    constructor(identifier: String) : this(identifier.split(':'))

    private val components = listOf(type, namespace, name, version)

    init {
        require(components.none { ":" in it }) {
            "Properties of Identifier must not contain ':' because it is used as a separator in the String " +
                    "representation of the Identifier: type='$type', namespace='$namespace', name='$name', " +
                    "version='$version'"
        }
    }

    override fun compareTo(other: Identifier) = toString().compareTo(other.toString())

    /**
     * Return whether this [Identifier] is likely to belong to the vendor of the given [name].
     */
    fun isFromVendor(name: String): Boolean {
        val lowerName = name.toLowerCase()
        val vendorNamespace = when (type) {
            "NPM" -> "@$lowerName"
            "Gradle", "Maven", "SBT" -> "com.$lowerName"
            else -> ""
        }

        return vendorNamespace.isNotEmpty() && namespace.startsWith(vendorNamespace)
    }

    /**
     * Return true if this matches the other identifier. To match, both identifiers need to have the same [type] and
     * [namespace], and the [name] and [version] must be either equal or empty for at least one of them.
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
    fun matches(other: Identifier): Boolean {
        if (!type.equals(other.type, true)) {
            return false
        }

        if (namespace != other.namespace) {
            return false
        }

        val nameMatches = name == other.name || name.isBlank() || other.name.isBlank()
        val versionMatches = version == other.version || version.isBlank() || other.version.isBlank()

        return nameMatches && versionMatches
    }

    /**
     * Create a path based on the properties of the [Identifier]. All properties are encoded using [encodeOrUnknown].
     */
    fun toPath() = components.joinToString("/") { it.encodeOrUnknown() }

    // TODO: Consider using a PURL here, see https://github.com/package-url/purl-spec#purl.
    // TODO: We probably want to already sanitize the individual properties, also in other classes, but Kotlin does not
    // seem to offer a generic / elegant way to do so.
    override fun toString() = components.joinToString(":") { it.trim().filterNot { it < ' ' } }
}

class IdentifierToStringSerializer : StdSerializer<Identifier>(Identifier::class.java) {
    override fun serialize(value: Identifier, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeString(value.toString())
    }
}
