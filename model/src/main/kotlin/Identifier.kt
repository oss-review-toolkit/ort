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

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.KeyDeserializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer

import com.here.ort.utils.encodeOrUnknown

/**
 * A unique identifier for a software package.
 */
data class Identifier(
        /**
         * The name of the provider that hosts this package, for example Maven or NPM.
         */
        val provider: String,

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
) : Comparable<Identifier> {
    companion object {
        /**
         * A constant for an [Identifier] where all properties are empty strings.
         */
        @JvmField
        val EMPTY = Identifier(
                provider = "",
                namespace = "",
                name = "",
                version = ""
        )

        /**
         * Create an [Identifier] from a string with the format "provider:namespace:name:version". If the string
         * has less than three colon separators the missing values are assigned empty strings.
         */
        fun fromString(identifier: String): Identifier {
            val components = identifier.split(':')
            return Identifier(
                    provider = components.getOrNull(0) ?: "",
                    namespace = components.getOrNull(1) ?: "",
                    name = components.getOrNull(2) ?: "",
                    version = components.getOrNull(3) ?: ""
            )
        }
    }

    private val components = listOf(provider, namespace, name, version)

    init {
        require(components.none { ":" in it }) {
            "Properties of Identifier must not contain ':' because it is used as a separator in the String " +
                    "representation of the Identifier: provider='$provider', namespace='$namespace', name='$name', " +
                    "version='$version'"
        }
    }

    override fun compareTo(other: Identifier) = toString().compareTo(other.toString())

    /**
     * Returns true if this matches the other identifier. To match, both identifiers need to have the same
     * [provider] and [namespace], and the [name] and [version] must be either equal or empty for at least one of
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
    fun matches(other: Identifier): Boolean {
        if (!provider.equals(other.provider, true)) {
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
    override fun toString() = components.joinToString(":")
}

class IdentifierToStringSerializer : StdSerializer<Identifier>(Identifier::class.java) {
    override fun serialize(value: Identifier, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeString(value.toString())
    }
}

class IdentifierFromStringDeserializer : StdDeserializer<Identifier>(Identifier::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Identifier {
        return Identifier.fromString(p.valueAsString)
    }
}

class IdentifierFromStringKeyDeserializer : KeyDeserializer() {
    override fun deserializeKey(key: String, ctxt: DeserializationContext): Identifier {
        return Identifier.fromString(key)
    }
}
