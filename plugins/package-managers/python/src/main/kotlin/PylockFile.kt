/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.python

import dev.eav.tomlkt.TomlElement
import dev.eav.tomlkt.TomlLiteral
import dev.eav.tomlkt.TomlTable
import dev.eav.tomlkt.asTomlTable
import dev.eav.tomlkt.decodeFromNativeReader
import dev.eav.tomlkt.encodeToTomlElement

import java.io.File
import java.lang.invoke.MethodHandles

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

import org.apache.logging.log4j.kotlin.loggerOf

import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.plugins.packagemanagers.python.utils.toml

private val logger = loggerOf(MethodHandles.lookup().lookupClass())

/** The supported major version of the lockfile format. PEP 751 requires to fail on an unknown major version. */
private const val SUPPORTED_LOCK_VERSION_MAJOR = "1"

private val PACKAGE_NAME_SEPARATOR_REGEX = Regex("[-_.]+")

/** A dependency group or extra test in a marker, like `"dev" in dependency_groups`, with an optional `not`. */
private val GROUP_MARKER_REGEX = Regex("""(["'])([^"']+)\1\s+(not\s+)?in\s+(?:dependency_groups|extras)""")

/** A token of a marker expression: a quoted string, a parenthesis or a run of other non-blank characters. */
private val MARKER_TOKEN_REGEX = Regex(""""[^"]*"|'[^']*'|[()]|[^\s()"']+""")

private val MARKER_OPERATOR_TOKENS = setOf("and", "or", "(", ")")

private val HASH_ALGORITHM_PREFERENCE = listOf(
    HashAlgorithm.SHA512,
    HashAlgorithm.SHA384,
    HashAlgorithm.SHA256,
    HashAlgorithm.SHA1,
    HashAlgorithm.MD5
)

/** A lockfile as specified by PEP 751, see https://peps.python.org/pep-0751/#file-format. */
@Serializable
internal data class PylockFile(
    @SerialName("lock-version")
    val lockVersion: String,

    @SerialName("created-by")
    val createdBy: String,

    val environments: List<String> = emptyList(),

    @SerialName("requires-python")
    val requiresPython: String? = null,

    val extras: List<String> = emptyList(),

    @SerialName("dependency-groups")
    val dependencyGroups: List<String> = emptyList(),

    @SerialName("default-groups")
    val defaultGroups: List<String> = emptyList(),

    val packages: List<Package> = emptyList()
) {
    /** A package entry, see https://peps.python.org/pep-0751/#packages. */
    @Serializable
    data class Package(
        val name: String,
        val version: String? = null,
        val marker: String? = null,

        @SerialName("requires-python")
        val requiresPython: String? = null,

        val index: String? = null,

        /** References to other entries, which can consist of any subset of the keys of the referenced entry. */
        val dependencies: List<TomlTable> = emptyList(),

        val vcs: Vcs? = null,
        val directory: Directory? = null,
        val archive: Archive? = null,
        val sdist: Distribution? = null,
        val wheels: List<Distribution> = emptyList()
    ) {
        // The serialized form of an entry is matched against dependency references, so properties derived from the
        // lockfile keys must not become part of it.

        @Transient
        val normalizedName: String = name.normalizePythonPackageName()

        /** Whether this entry refers to files on a package index, and thus has metadata there. */
        @Transient
        val isIndexPackage: Boolean =
            vcs == null && directory == null && archive == null && (sdist != null || wheels.isNotEmpty())

        /**
         * Return how this package relates to dependency groups and extras according to its [marker]. An extra is
         * treated like a dependency group of the same name.
         */
        fun getGroupMembership(): GroupMembership =
            marker?.let { MarkerParser(it).parse() } ?: GroupMembership(emptySet(), isRequiredWithoutGroups = true)

        /** Return whether all keys of the given dependency [reference] have the same value in this entry. */
        fun isReferencedBy(reference: TomlTable): Boolean {
            if ("name" in reference && reference.getReferencedName() != normalizedName) return false

            val otherKeys = reference.filterKeys { it != "name" }
            if (otherKeys.isEmpty()) return true

            val table = toml.encodeToTomlElement(this).asTomlTable()
            return otherKeys.all { (key, value) -> value.isSubsetOf(table[key]) }
        }
    }

    /** See https://peps.python.org/pep-0751/#packages-vcs. */
    @Serializable
    data class Vcs(
        val type: String,
        val url: String? = null,
        val path: String? = null,

        @SerialName("requested-revision")
        val requestedRevision: String? = null,

        @SerialName("commit-id")
        val commitId: String,

        val subdirectory: String? = null
    )

    /** See https://peps.python.org/pep-0751/#packages-directory. */
    @Serializable
    data class Directory(
        val path: String,
        val editable: Boolean = false,
        val subdirectory: String? = null
    )

    /** See https://peps.python.org/pep-0751/#packages-archive. */
    @Serializable
    data class Archive(
        val url: String? = null,
        val path: String? = null,
        val hashes: Map<String, String> = emptyMap(),
        val subdirectory: String? = null
    )

    /** See https://peps.python.org/pep-0751/#packages-sdist and https://peps.python.org/pep-0751/#packages-wheels. */
    @Serializable
    data class Distribution(
        val name: String? = null,
        val url: String? = null,
        val path: String? = null,
        val hashes: Map<String, String> = emptyMap()
    ) {
        /** The file name, which may be omitted in the lockfile if it equals the last part of the URL or path. */
        @Transient
        val filename: String? = name ?: url?.getFilenameFromUrl() ?: path?.substringAfterLast('/')

        @Transient
        val isUniversalWheel: Boolean = filename?.endsWith("-none-any.whl") == true
    }
}

/** Return whether the [other] element has a matching value for each key of this element. */
private fun TomlElement.isSubsetOf(other: TomlElement?): Boolean =
    if (this is TomlTable && other is TomlTable) all { (key, value) -> value.isSubsetOf(other[key]) } else this == other

internal fun TomlTable.getReferencedName(): String? =
    (this["name"] as? TomlLiteral)?.content?.normalizePythonPackageName()

/** The dependency [groups] and extras that can require a package, and whether it [isRequiredWithoutGroups]. */
internal data class GroupMembership(val groups: Set<String>, val isRequiredWithoutGroups: Boolean) {
    // A package that needs several groups is assigned to each of them, so that it is not excluded together with a
    // group it is still needed without.
    infix fun and(other: GroupMembership) =
        GroupMembership(groups + other.groups, isRequiredWithoutGroups && other.isRequiredWithoutGroups)

    infix fun or(other: GroupMembership) =
        GroupMembership(groups + other.groups, isRequiredWithoutGroups || other.isRequiredWithoutGroups)
}

/**
 * A parser for the Boolean structure of a marker expression. Comparisons other than dependency group or extra tests
 * are assumed to possibly hold, as evaluating them would require a target environment.
 */
private class MarkerParser(marker: String) {
    private val tokens = MARKER_TOKEN_REGEX.findAll(marker).map { it.value }.toList()
    private var position = 0

    private val currentToken: String?
        get() = tokens.getOrNull(position)

    fun parse(): GroupMembership = parseDisjunction()

    private fun parseDisjunction(): GroupMembership {
        var result = parseConjunction()

        while (currentToken == "or") {
            position++
            result = result or parseConjunction()
        }

        return result
    }

    private fun parseConjunction(): GroupMembership {
        var result = parseComparison()

        while (currentToken == "and") {
            position++
            result = result and parseComparison()
        }

        return result
    }

    private fun parseComparison(): GroupMembership {
        if (currentToken == "(") {
            position++
            return parseDisjunction().also { if (currentToken == ")") position++ }
        }

        val comparison = buildList {
            while (currentToken?.let { it !in MARKER_OPERATOR_TOKENS } == true) add(tokens[position++])
        }.joinToString(" ")

        val groupTest = GROUP_MARKER_REGEX.matchEntire(comparison)
        val isNegated = groupTest?.groups?.get(3) != null

        return if (groupTest == null || isNegated) {
            GroupMembership(emptySet(), isRequiredWithoutGroups = true)
        } else {
            GroupMembership(setOf(groupTest.groupValues[2]), isRequiredWithoutGroups = false)
        }
    }
}

/** Parse the given [lockfile] and fail if its format version is not supported. */
internal fun parsePylockFile(lockfile: File): PylockFile {
    val pylock = lockfile.reader().use { toml.decodeFromNativeReader<PylockFile>(it) }

    val (major, minor) = pylock.lockVersion.split('.', limit = 2).let { it.first() to (it.getOrNull(1) ?: "0") }

    require(major == SUPPORTED_LOCK_VERSION_MAJOR) {
        "The lockfile '$lockfile' has the unsupported lock-version '${pylock.lockVersion}', only version " +
            "$SUPPORTED_LOCK_VERSION_MAJOR.x is supported."
    }

    if (minor != "0") {
        logger.warn {
            "The lockfile '$lockfile' has lock-version '${pylock.lockVersion}', which is newer than the supported " +
                "version $SUPPORTED_LOCK_VERSION_MAJOR.0. Keys added in that version are ignored."
        }
    }

    return pylock
}

internal fun String.getFilenameFromUrl(): String = substringBefore('#').substringBefore('?').substringAfterLast('/')

/** See https://packaging.python.org/en/latest/specifications/name-normalization/. */
internal fun String.normalizePythonPackageName(): String = PACKAGE_NAME_SEPARATOR_REGEX.replace(this, "-").lowercase()

/** Convert these hashes, keyed by the algorithm name, to a [Hash] of the strongest known algorithm. */
internal fun Map<String, String>.toHash(): Hash {
    val hashesByAlgorithm = mapKeys { (algorithm, _) -> HashAlgorithm.fromString(algorithm) }

    return HASH_ALGORITHM_PREFERENCE.firstNotNullOfOrNull { algorithm ->
        hashesByAlgorithm[algorithm]?.let { value ->
            runCatching { Hash(value, algorithm) }.onFailure { logger.warn { it.message.orEmpty() } }.getOrNull()
        }
    } ?: Hash.NONE
}

internal fun PylockFile.Distribution.toRemoteArtifact(): RemoteArtifact? =
    url?.let { RemoteArtifact(it, hashes.toHash()) }

internal fun PylockFile.Archive.toRemoteArtifact(): RemoteArtifact? = url?.let { RemoteArtifact(it, hashes.toHash()) }
