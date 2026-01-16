/*
 * Copyright (C) 2025 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.gleam

import java.io.File

import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

import net.peanuuutz.tomlkt.Toml
import net.peanuuutz.tomlkt.TomlDecoder
import net.peanuuutz.tomlkt.TomlLiteral
import net.peanuuutz.tomlkt.TomlTable
import net.peanuuutz.tomlkt.asTomlLiteral
import net.peanuuutz.tomlkt.asTomlTable
import net.peanuuutz.tomlkt.decodeFromNativeReader

private val toml = Toml { ignoreUnknownKeys = true }

internal fun parseGleamToml(file: File): GleamToml = file.reader().use { toml.decodeFromNativeReader<GleamToml>(it) }

/**
 * Represents the gleam.toml project manifest file.
 * See https://gleam.run/writing-gleam/gleam-toml/ for the specification.
 */
@Serializable
internal data class GleamToml(
    /** The name of the project. */
    val name: String,

    /** The version of the project in semantic versioning format. */
    val version: String,

    /** A brief description of the project. */
    val description: String? = null,

    /** SPDX license identifiers. Note: Gleam uses British spelling "licences". */
    val licences: List<String> = emptyList(),

    /** The repository where the project is hosted. */
    val repository: Repository? = null,

    /** The Gleam version requirement for this project. */
    val gleam: String? = null,

    /** The default compilation target: "erlang" or "javascript". */
    val target: String? = null,

    /** The project dependencies as a map of package names to version constraints or dependency objects. */
    val dependencies: Map<String, Dependency> = emptyMap(),

    /** Development-only dependencies. */
    @SerialName("dev-dependencies")
    val devDependencies: Map<String, Dependency> = emptyMap(),

    /** Links to project resources like homepage, documentation, etc. */
    val links: List<Link> = emptyList()
) {
    /**
     * Represents a link in the gleam.toml links section.
     */
    @Serializable
    data class Link(
        val title: String,
        val href: String
    )

    /**
     * Represents the repository configuration in gleam.toml.
     * Can be either a well-known hosting service (github, gitlab, etc.) or a custom URL.
     */
    @Serializable
    data class Repository(
        /** The type of repository hosting. */
        val type: String? = null,

        /** The username or organization on the hosting service. */
        val user: String? = null,

        /** The repository name on the hosting service. */
        val repo: String? = null,

        /** The host for self-hosted services like forgejo or gitea. */
        val host: String? = null,

        /** A custom URL for self-hosted or other repositories. */
        val url: String? = null
    ) {
        companion object {
            private val KNOWN_HOSTS = mapOf(
                "github" to "github.com",
                "gitlab" to "gitlab.com",
                "bitbucket" to "bitbucket.org",
                "codeberg" to "codeberg.org",
                "tangled" to "tangled.sh"
            )
        }

        /**
         * Constructs the full repository URL based on the type and other fields.
         */
        fun toUrl(): String? {
            if (type == "custom") return url

            val normalizedType = type?.lowercase() ?: return null

            // SourceHut has a special URL format with ~ prefix.
            if (normalizedType == "sourcehut") {
                return user?.let { u -> repo?.let { r -> "https://git.sr.ht/~$u/$r" } }
            }

            // Self-hosted services require a host parameter.
            if (normalizedType == "forgejo" || normalizedType == "gitea") {
                return host?.let { h -> user?.let { u -> repo?.let { r -> "https://$h/$u/$r" } } }
            }

            val hostDomain = KNOWN_HOSTS[normalizedType] ?: return null
            return user?.let { u -> repo?.let { r -> "https://$hostDomain/$u/$r" } }
        }
    }

    /**
     * Represents a dependency in gleam.toml which can be:
     * - A hex package with a version constraint (string)
     * - A path dependency (object with "path" field)
     * - A git dependency (object with "git" field and optional "ref")
     */
    @Serializable(DependencySerializer::class)
    sealed interface Dependency {
        /** A dependency from hex.pm with a version constraint. */
        data class Hex(val version: String) : Dependency

        /** A local path dependency. */
        data class Path(val path: String) : Dependency

        /** A git repository dependency. */
        data class Git(val url: String, val ref: String? = null) : Dependency
    }
}

private object DependencySerializer : KSerializer<GleamToml.Dependency> {
    override val descriptor = PolymorphicSerializer(GleamToml.Dependency::class).descriptor

    /**
     * Parse a TOML element into a Dependency.
     */
    override fun deserialize(decoder: Decoder): GleamToml.Dependency {
        require(decoder is TomlDecoder)

        return when (val element = decoder.decodeTomlElement()) {
            is TomlLiteral -> GleamToml.Dependency.Hex(element.asTomlLiteral().content)
            is TomlTable -> {
                val table = element.asTomlTable()
                when {
                    "path" in table -> GleamToml.Dependency.Path(table.getValue("path").asTomlLiteral().content)
                    "git" in table -> GleamToml.Dependency.Git(
                        url = table.getValue("git").asTomlLiteral().content,
                        ref = table["ref"]?.asTomlLiteral()?.content
                    )

                    else -> throw IllegalArgumentException("Unknown dependency format: $element")
                }
            }

            else -> throw IllegalArgumentException("Unknown dependency format: $element")
        }
    }

    override fun serialize(encoder: Encoder, value: GleamToml.Dependency) =
        throw NotImplementedError("${descriptor.serialName} can only be deserialized.")
}
