/*
 * Copyright (C) 2022 EPAM Systems, Inc.
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

package org.ossreviewtoolkit.clients.osv

import java.time.Instant

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// TODO: Remove all JsonElement subtypes as property types from the model in favor of raw strings holding JSON.
//
// Accessing JsonElement subtypes requires the client code to add 'kotlinx.serialization' as dependency, which is not
// desired - raw strings would fix that.
// At the time of writing, that's not (easily) possible to implement due to limitations in the serialization library,
// see:
// 1. https://github.com/Kotlin/kotlinx.serialization/issues/1298
// 2. https://github.com/Kotlin/kotlinx.serialization/issues/1405
// 3. https://github.com/Kotlin/kotlinx.serialization/issues/1058

/**
 * Implementation of the "Open Source Vulnerability format" according to schema version 1.3.0 (March 24, 2022), see
 * https://ossf.github.io/osv-schema/ which links to
 * https://github.com/ossf/osv-schema/blob/11524982426be469795b9c684ba340c5c90895d0/validation/schema.json which was
 * used as a reference.
 *
 * For the documentation of all entities and properties please refer to above links.
 */
@Serializable
data class Vulnerability(
    @SerialName("schema_version")
    val schemaVersion: String = "1.0.0",
    val id: String,
    @Serializable(InstantSerializer::class)
    val modified: Instant,
    @Serializable(InstantSerializer::class)
    val published: Instant? = null,
    @Serializable(InstantSerializer::class)
    val withdrawn: Instant? = null,
    val aliases: List<String> = emptyList(),
    val related: List<String> = emptyList(),
    val summary: String? = null,
    val details: String? = null,

    /**
     * The severity of this [Vulnerability]. This is a list in order to allow for providing multiple representations,
     * using different scoring systems. See https://github.com/google/osv.dev/issues/545#issuecomment-1190880767 and
     * https://ossf.github.io/osv-schema/#severity-field.
     */
    val severity: List<Severity> = emptyList(),

    val affected: List<Affected> = emptyList(),
    val references: List<Reference> = emptyList(),
    @SerialName("database_specific")
    val databaseSpecific: JsonObject? = null,
    val credits: List<Credit> = emptyList()
)

@Serializable
data class Affected(
    @SerialName("package")
    val pkg: Package,
    val ranges: List<Range> = emptyList(),
    val versions: List<String> = emptyList(),
    @SerialName("ecosystem_specific")
    val ecosystemSpecific: JsonObject? = null,
    @SerialName("database_specific")
    val databaseSpecific: JsonObject? = null
)

@Serializable
data class Credit(
    val name: String,
    val contact: List<String> = emptyList()
)

/**
 * Defined package ecosystem values, see https://ossf.github.io/osv-schema/.
 */
object Ecosystem {
    const val ANDROID = "Android"
    const val CRATES_IO = "crates.io"
    const val DEBIAN = "Debian"
    const val GO = "Go"
    const val HEX = "Hex"
    const val LINUX = "Linux"
    const val MAVEN = "Maven"
    const val NPM = "npm"
    const val NUGET = "NuGet"
    const val OSS_FUZZ = "OSS-Fuzz"
    const val PACKAGIST = "Packagist"
    const val PYPI = "PyPI"
    const val RUBY_GEMS = "RubyGems"
}

@Serializable(EventSerializer::class)
data class Event(
    val type: Type,
    val value: String
) {
    enum class Type {
        INTRODUCED,
        FIXED,
        LAST_AFFECTED,
        LIMIT
    }
}

@Serializable
data class Package(
    /** See also [Ecosystem]. */
    val ecosystem: String,
    val name: String,
    val purl: String? = null
)

@Serializable
data class Range(
    val type: Type,
    val repo: String? = null,
    val events: List<Event>,
    @SerialName("database_specific")
    val databaseSpecific: JsonObject? = null
) {
    enum class Type {
        ECOSYSTEM,
        GIT,
        SEMVER
    }

    init {
        require(type != Type.GIT || !repo.isNullOrBlank()) {
            "Range of type 'git' requires a non-blank 'repo' property."
        }

        requireNotNull(events.find { it.type == Event.Type.INTRODUCED }) {
            "A range requires at least one 'introduced' event."
        }
    }
}

@Serializable
data class Reference(
    val type: Type,
    val url: String
) {
    enum class Type {
        ADVISORY,
        ARTICLE,
        FIX,
        PACKAGE,
        REPORT,
        WEB
    }
}

@Serializable
data class Severity(
    val type: Type,
    val score: String
) {
    enum class Type {
        CVSS_V3
    }
}
