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

package org.ossreviewtoolkit.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

import org.ossreviewtoolkit.utils.encodeOr
import org.ossreviewtoolkit.utils.percentEncode

/**
 * A unique identifier for a software package.
 */
data class Identifier(
    /**
     * The type of package. When used in the context of a [Project], the type is the name of the package manager that
     * manages the project (e.g. "Gradle" for a Gradle project). When used in the context of a [Package], the type is
     * the name of the package type or protocol (e.g. "Maven" for a file from a Maven repository).
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
    constructor(identifier: String) : this(identifier.split(':', limit = 4))

    private val components = listOf(type, namespace, name, version)

    init {
        require(components.none { ":" in it }) {
            "An identifier's properties must not contain ':' because that character is used as a separator in the " +
                    "string representation: type='$type', namespace='$namespace', name='$name', version='$version'."
        }
    }

    override fun compareTo(other: Identifier) = toCoordinates().compareTo(other.toCoordinates())

    /**
     * Return whether this [Identifier] is likely to belong any of the organizations mentioned in [names].
     */
    fun isFromOrg(vararg names: String) =
        names.any { name ->
            val lowerName = name.toLowerCase()
            val vendorNamespace = when (type) {
                "NPM" -> "@$lowerName"
                "Gradle", "Maven", "SBT" -> "(com|io|net|org)\\.$lowerName(\\..+)?"
                else -> ""
            }

            vendorNamespace.isNotEmpty() && namespace.matches(vendorNamespace.toRegex())
        }

    /**
     * Create Maven-like coordinates based on the properties of the [Identifier].
     */
    // TODO: We probably want to already sanitize the individual properties, also in other classes, but Kotlin does not
    //       seem to offer a generic / elegant way to do so.
    @JsonValue
    fun toCoordinates() = components.joinToString(":") { component -> component.trim().filterNot { it < ' ' } }

    /**
     * Create a file system path based on the properties of the [Identifier]. All properties are encoded using
     * [encodeOr] with [emptyValue] as parameter.
     */
    fun toPath(separator: String = "/", emptyValue: String = "unknown"): String =
        components.joinToString(separator) { it.encodeOr(emptyValue) }

    /**
     * Create the canonical [package URL](https://github.com/package-url/purl-spec) ("purl") based on the properties of
     * the [Identifier]. Some issues remain with this specification
     * (see e.g. https://github.com/package-url/purl-spec/issues/33).
     *
     * This implementation uses the package type as 'type' purl element as it is used
     * [in the documentation](https://github.com/package-url/purl-spec/blob/master/README.rst#purl).
     * E.g. 'maven' for Gradle projects.
     */
    fun toPurl() = "".takeIf { this == EMPTY }
        ?: buildString {
            append("pkg:")
            append(getPurlType())

            if (namespace.isNotEmpty()) {
                append('/')
                append(namespace.percentEncode())
            }

            append('/')
            append(name.percentEncode())

            append('@')
            append(version.percentEncode())
        }

    /**
     * Map a package manager type to the String representation of the respective [PurlType].
     * Falls back to the lower case package manager type if the [PurlType] cannot be determined.
     *
     * E.g. PIP to [PurlType.PYPI] or Gradle to [PurlType.MAVEN].
     */
    fun getPurlType() =
        when (val lowerType = type.toLowerCase()) {
            "bower" -> PurlType.BOWER
            "cargo" -> PurlType.CARGO
            "composer" -> PurlType.COMPOSER
            "conan" -> PurlType.CONAN
            "dep", "glide", "godep", "gomod" -> PurlType.GOLANG
            "dotnet", "nuget" -> PurlType.NUGET
            "gem" -> PurlType.GEM
            "gradle", "maven", "sbt" -> PurlType.MAVEN
            "npm", "yarn" -> PurlType.NPM
            "pip", "pipenv" -> PurlType.PYPI
            else -> lowerType
        }.toString()

    enum class PurlType(private val value: String) {
        ALPINE("alpine"),
        A_NAME("a-name"),
        BOWER("bower"),
        CARGO("cargo"),
        COCOAPODS("cocoapods"),
        COMPOSER("composer"),
        CONAN("conan"),
        CONDA("conda"),
        CRAN("cran"),
        DEBIAN("debian"),
        DRUPAL("drupal"),
        GEM("gem"),
        GOLANG("golang"),
        MAVEN("maven"),
        NPM("npm"),
        NUGET("nuget"),
        PECOFF("pecoff"),
        PYPI("pypi"),
        RPM("rpm");

        override fun toString() = value
    }
}
