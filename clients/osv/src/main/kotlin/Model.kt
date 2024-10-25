/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import io.ks3.java.typealiases.InstantAsString

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
 * Implementation of the "Open Source Vulnerability format" according to schema version 1.6.0 (Aug 11, 2023), see
 * https://github.com/ossf/osv-schema/blob/v1.6.0/validation/schema.json, also referenced from
 * https://ossf.github.io/osv-schema/.
 *
 * For the documentation of all entities and properties please refer to above links.
 */
@Serializable
data class Vulnerability(
    val schemaVersion: String = "1.0.0",
    val id: String,
    val modified: InstantAsString,
    val published: InstantAsString? = null,
    val withdrawn: InstantAsString? = null,
    val aliases: Set<String> = emptySet(),
    val related: Set<String> = emptySet(),
    val summary: String? = null,
    val details: String? = null,

    /**
     * The severity is a collection in order to allow for providing multiple representations using different scoring
     * systems. See https://github.com/google/osv.dev/issues/545#issuecomment-1190880767 and
     * https://ossf.github.io/osv-schema/#severity-field.
     */
    val severity: Set<Severity> = emptySet(),

    val affected: Set<Affected> = emptySet(),
    val references: Set<Reference> = emptySet(),
    val databaseSpecific: JsonObject? = null,
    val credits: Set<Credit> = emptySet()
)

/**
 * The affected package and versions, meaning those that contain the vulnerability.
 * See https://ossf.github.io/osv-schema/#affected-fields.
 */
@Serializable
data class Affected(
    @SerialName("package")
    val pkg: Package? = null,
    val ranges: List<Range> = emptyList(),
    val severity: Set<Severity> = emptySet(),
    val versions: List<String> = emptyList(),
    val ecosystemSpecific: JsonObject? = null,
    val databaseSpecific: JsonObject? = null
)

/**
 * A way to give credit for the discovery, confirmation, patch, or other events in the life cycle of a vulnerability.
 * See https://ossf.github.io/osv-schema/#credits-fields.
 */
@Serializable
data class Credit(
    val name: String,
    val contact: List<String> = emptyList()
) {
    enum class Type {
        ANALYST,
        COORDINATOR,
        FINDER,
        OTHER,
        REMEDIATION_DEVELOPER,
        REMEDIATION_REVIEWER,
        REMEDIATION_VERIFIER,
        REPORTER,
        SPONSOR,
        TOOL
    }
}

/**
 * Defined package ecosystem values, see https://ossf.github.io/osv-schema/#affectedpackage-field.
 */
object Ecosystem {
    const val ALMA_LINUX = "AlmaLinux"
    const val ALPINE = "Alpine"
    const val ANDROID = "Android"
    const val BIOCONDUCTOR = "Bioconductor"
    const val BITNAMI = "Bitnami"
    const val CHAINGUARD = "Chainguard"
    const val CONAN_CENTER = "ConanCenter"
    const val CRAN = "CRAN"
    const val CRATES_IO = "crates.io"
    const val DEBIAN = "Debian"
    const val GHC = "GHC"
    const val GIHUB_ACTIONS = "GitHub Actions"
    const val GO = "Go"
    const val HACKAGE = "Hackage"
    const val HEX = "Hex"
    const val LINUX = "Linux"
    const val MAGEIA = "Mageia"
    const val MAVEN = "Maven"
    const val NPM = "npm"
    const val NUGET = "NuGet"
    const val OSS_FUZZ = "OSS-Fuzz"
    const val OPEN_SUSE = "openSUSE"
    const val PACKAGIST = "Packagist"
    const val PHOTON_OS = "Photon OS"
    const val PUB = "Pub"
    const val PYPI = "PyPI"
    const val RED_HAT = "Red Hat"
    const val ROCKY_LINUX = "Rocky Linux"
    const val RUBY_GEMS = "RubyGems"
    const val SUSE = "SUSE"
    const val SWIFT_URL = "SwiftURL"
    const val UBUNTU = "Ubuntu"
}

/**
 * A representation of a status change for an affected package.
 * See https://ossf.github.io/osv-schema/#affectedrangesevents-fields.
 */
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

/**
 * A class to identify the the affected code library or command provided by the package.
 * See https://ossf.github.io/osv-schema/#affectedpackage-field.
 */
@Serializable
data class Package(
    /** See also [Ecosystem]. */
    val ecosystem: String? = null,
    val name: String? = null,
    val purl: String? = null
)

/**
 * A class to store information about the affected version range of the given [type] and optional [repo] URL. Each of
 * [events] describes the event that occurred in a single version.
 * See https://ossf.github.io/osv-schema/#affectedranges-field.
 */
@Serializable
data class Range(
    val type: Type,
    val repo: String? = null,
    val events: List<Event>,
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

/**
 * A class to specify the [type] of reference, and the fully-qualified URL (including the scheme, typically “https://”)
 * linking to additional information. See https://ossf.github.io/osv-schema/#references-field.
 */
@Serializable
data class Reference(
    val type: Type,
    val url: String
) {
    enum class Type {
        ADVISORY,
        ARTICLE,
        DETECTION,
        DISCUSSION,
        EVIDENCE,
        FIX,
        INTRODUCED,
        PACKAGE,
        REPORT,
        WEB
    }
}

/**
 * A class to escribe the quantitative [type] method used to calculate the associated [score].
 * See https://ossf.github.io/osv-schema/#severitytype-field.
 */
@Serializable
data class Severity(
    val type: Type,
    val score: String
) {
    enum class Type {
        CVSS_V2,
        CVSS_V3,
        CVSS_V4
    }
}

@Serializable
data class ErrorResponse(
    val code: Int,
    val message: String
)
