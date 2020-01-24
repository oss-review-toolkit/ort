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
import com.fasterxml.jackson.annotation.JsonValue

import com.here.ort.utils.encodeOrUnknown
import com.here.ort.utils.percentEncode

/**
 * A unique identifier for a software component.
 */
sealed class Identifier(
    /**
     * The type of component this identifier describes. When used in the context of a [Project], the type describes the
     * package manager that manages the project (e.g. [GRADLE][ProjectIdentifier.Type.GRADLE] for a Gradle project).
     * When used in the context of a [Package], the type describes the package format or protocol (e.g.
     * [MAVEN][PackageIdentifier.Type.MAVEN] for a file from a Maven repository).
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
        val EMPTY = EmptyIdentifier()
    }

    private val properties = listOf(type, namespace, name, version)

    init {
        require(properties.none { ":" in it }) {
            "An identifier's properties must not contain ':' because that character is used as a separator in the " +
                    "string representation: type='$type', namespace='$namespace', name='$name', version='$version'."
        }
    }

    override fun compareTo(other: Identifier) = toCoordinates().compareTo(other.toCoordinates())

    /**
     * Create Maven-like coordinates based on the properties of the [Identifier].
     */
    // TODO: We probably want to already sanitize the individual properties, also in other classes, but Kotlin does not
    //       seem to offer a generic / elegant way to do so.
    @JsonValue
    fun toCoordinates() = properties.joinToString(":") { it.trim().filterNot { it < ' ' } }

    /**
     * Create a file system path based on the properties of the [Identifier]. All properties are encoded using
     * [encodeOrUnknown].
     */
    fun toPath() = properties.joinToString("/") { it.encodeOrUnknown() }
}

class EmptyIdentifier : Identifier("", "", "", "")

class ProjectIdentifier(val projectType: Type, namespace: String, name: String, version: String) :
    Identifier(projectType.toString(), namespace, name, version) {
    enum class Type(val value: String) {
        GRADLE("Gradle"),
        MAVEN("Maven"),
        NPM("NPM"),
        SBT("sbt");

        companion object {
            @JsonCreator
            @JvmStatic
            fun fromString(value: String) = enumValues<Type>().single { value.equals(it.value, ignoreCase = true) }
        }

        @JsonValue
        override fun toString() = value
    }

    /**
     * Return whether this [Identifier] is likely to belong any of the organizations mentioned in [names].
     */
    fun isFromOrg(vararg names: String) =
        names.any { name ->
            val lowerName = name.toLowerCase()
            val vendorNamespace = when (projectType) {
                Type.NPM -> "@$lowerName"
                Type.GRADLE, Type.MAVEN, Type.SBT -> "(com|net|org)\\.$lowerName(\\..+)?"
            }

            vendorNamespace.isNotEmpty() && namespace.matches(vendorNamespace.toRegex())
        }
}

class PackageIdentifier(val packageType: Type, namespace: String, name: String, version: String) :
    Identifier(packageType.toString(), namespace, name, version) {
    enum class Type(val value: String) {
        MAVEN("Maven"),
        NPM("NPM");

        companion object {
            @JsonCreator
            @JvmStatic
            fun fromString(value: String) = enumValues<Type>().single { value.equals(it.value, ignoreCase = true) }
        }

        @JsonValue
        override fun toString() = value
    }

    /**
     * Create the canonical [package URL](https://github.com/package-url/purl-spec) ("purl") based on the properties of
     * the [Identifier].
     */
    // TODO: This is a preliminary implementation as some open questions remain, see e.g.
    //       https://github.com/package-url/purl-spec/issues/33.
    fun toPurl() =
        buildString {
            append("pkg:")
            append(type.toLowerCase())

            if (namespace.isNotEmpty()) {
                append('/')
                append(namespace.percentEncode())
            }

            append('/')
            append(name.percentEncode())

            append('@')
            append(version.percentEncode())
        }
}
