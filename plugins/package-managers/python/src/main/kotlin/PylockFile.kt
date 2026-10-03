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

import java.io.File
import java.lang.invoke.MethodHandles

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import net.peanuuutz.tomlkt.TomlElement
import net.peanuuutz.tomlkt.TomlLiteral
import net.peanuuutz.tomlkt.TomlTable
import net.peanuuutz.tomlkt.asTomlTable
import net.peanuuutz.tomlkt.decodeFromNativeReader
import net.peanuuutz.tomlkt.encodeToTomlElement

import org.apache.logging.log4j.kotlin.loggerOf

import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.plugins.packagemanagers.python.utils.toml

private val logger = loggerOf(MethodHandles.lookup().lookupClass())

/**
 * The major version of the lockfile format this parser supports. PEP 751 requires tools to fail on an unknown major
 * version but only to warn on an unknown minor version.
 */
private const val SUPPORTED_LOCK_VERSION_MAJOR = "1"

/**
 * The separators in Python package names, see
 * https://packaging.python.org/en/latest/specifications/name-normalization/.
 */
private val PACKAGE_NAME_SEPARATOR_REGEX = Regex("[-_.]+")

/**
 * A dependency group test in a marker, like `"dev" in dependency_groups`. The optional `not` is captured to detect
 * negated tests.
 */
private val DEPENDENCY_GROUP_MARKER_REGEX = Regex("""(["'])([^"']+)\1\s+(not\s+)?in\s+dependency_groups""")

/** A token of a marker expression: a quoted string, a parenthesis or a run of other non-blank characters. */
private val MARKER_TOKEN_REGEX = Regex(""""[^"]*"|'[^']*'|[()]|[^\s()"']+""")

private val MARKER_OPERATOR_TOKENS = setOf("and", "or", "(", ")")

/** The preferred hash algorithms if a lockfile provides several hashes for a file, strongest first. */
private val HASH_ALGORITHM_PREFERENCE = listOf(
    HashAlgorithm.SHA512,
    HashAlgorithm.SHA384,
    HashAlgorithm.SHA256,
    HashAlgorithm.SHA1,
    HashAlgorithm.MD5
)

/**
 * A lockfile as specified by PEP 751, see https://peps.python.org/pep-0751/#file-format. Only the keys used by ORT are
 * modeled, all other keys are ignored.
 */
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
    /**
     * A package entry, see https://peps.python.org/pep-0751/#packages. Exactly one of [vcs], [directory], [archive] or
     * [sdist] / [wheels] is set.
     */
    @Serializable
    data class Package(
        val name: String,
        val version: String? = null,
        val marker: String? = null,

        @SerialName("requires-python")
        val requiresPython: String? = null,

        val index: String? = null,

        /**
         * References to the entries this package depends on. A reference can consist of any subset of the keys of the
         * referenced entry, so it is kept as a raw table.
         */
        val dependencies: List<TomlTable> = emptyList(),

        val vcs: Vcs? = null,
        val directory: Directory? = null,
        val archive: Archive? = null,
        val sdist: Distribution? = null,
        val wheels: List<Distribution> = emptyList()
    ) {
        /** The name normalized according to the Python packaging specification. */
        val normalizedName: String
            get() = name.normalizePythonPackageName()

        /**
         * Whether this entry refers to files on a package index. Only such packages have metadata that can be
         * looked up on the index.
         */
        val isIndexPackage: Boolean
            get() = vcs == null && directory == null && archive == null && (sdist != null || wheels.isNotEmpty())

        /**
         * Return how this package relates to dependency groups according to its [marker]. PDM encodes the group
         * membership as `"<group>" in dependency_groups`, while pip and uv do not write such markers.
         */
        fun getGroupMembership(): GroupMembership =
            marker?.let { MarkerParser(it).parse() } ?: GroupMembership(emptySet(), isRequiredWithoutGroups = true)

        /**
         * Return whether the given dependency [reference] refers to this entry, i.e. whether all keys of the reference
         * have the same value in this entry. The name is compared in normalized form.
         */
        fun isReferencedBy(reference: TomlTable): Boolean {
            if ("name" in reference && reference.getReferencedName() != normalizedName) return false

            val otherKeys = reference.filterKeys { it != "name" }
            if (otherKeys.isEmpty()) return true

            val table = toml.encodeToTomlElement(this).asTomlTable()
            return otherKeys.all { (key, value) -> value.isSubsetOf(table[key]) }
        }
    }

    /** A source tree in a version control system, see https://peps.python.org/pep-0751/#packages-vcs. */
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

    /** A source tree in a local directory, see https://peps.python.org/pep-0751/#packages-directory. */
    @Serializable
    data class Directory(
        val path: String,
        val editable: Boolean = false,
        val subdirectory: String? = null
    )

    /** An archive that is not hosted on a package index, see https://peps.python.org/pep-0751/#packages-archive. */
    @Serializable
    data class Archive(
        val url: String? = null,
        val path: String? = null,
        val hashes: Map<String, String> = emptyMap(),
        val subdirectory: String? = null
    )

    /**
     * A source or wheel distribution hosted on a package index, see https://peps.python.org/pep-0751/#packages-sdist
     * and https://peps.python.org/pep-0751/#packages-wheels.
     */
    @Serializable
    data class Distribution(
        val name: String? = null,
        val url: String? = null,
        val path: String? = null,
        val hashes: Map<String, String> = emptyMap()
    ) {
        /** The file name, which may be omitted in the lockfile if it equals the last part of the URL or path. */
        val filename: String?
            get() = name ?: url?.getFilenameFromUrl() ?: path?.substringAfterLast('/')

        /** Whether this is a wheel that is not specific to a platform or Python implementation. */
        val isUniversalWheel: Boolean
            get() = filename?.endsWith("-none-any.whl") == true
    }
}

/**
 * Return whether this element matches the [other] element. A table matches if the other table has a matching value
 * for each of its keys.
 */
private fun TomlElement.isSubsetOf(other: TomlElement?): Boolean =
    if (this is TomlTable && other is TomlTable) all { (key, value) -> value.isSubsetOf(other[key]) } else this == other

/** Return the normalized name of the package this dependency reference names, or null if it has no name. */
internal fun TomlTable.getReferencedName(): String? =
    (this["name"] as? TomlLiteral)?.content?.normalizePythonPackageName()

/**
 * The relation of a package to the dependency groups of a lockfile: The [groups] that can require the package, and
 * whether the package [isRequiredWithoutGroups], i.e. also if no group is selected.
 */
internal data class GroupMembership(val groups: Set<String>, val isRequiredWithoutGroups: Boolean) {
    // A package that needs several groups is assigned to each of them, so that it is not excluded together with a
    // group it is still needed without.
    infix fun and(other: GroupMembership) =
        GroupMembership(groups + other.groups, isRequiredWithoutGroups && other.isRequiredWithoutGroups)

    infix fun or(other: GroupMembership) =
        GroupMembership(groups + other.groups, isRequiredWithoutGroups || other.isRequiredWithoutGroups)
}

/**
 * A parser for the Boolean structure of a marker expression. Only `and`, `or` and parentheses are interpreted, as
 * evaluating the comparisons would require a target environment. Any comparison other than a dependency group test
 * is assumed to possibly hold. Malformed markers are tolerated instead of failing the analysis.
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

        val groupTest = DEPENDENCY_GROUP_MARKER_REGEX.matchEntire(comparison)
        val isNegated = groupTest?.groups?.get(3) != null

        return if (groupTest == null || isNegated) {
            GroupMembership(emptySet(), isRequiredWithoutGroups = true)
        } else {
            GroupMembership(setOf(groupTest.groupValues[2]), isRequiredWithoutGroups = false)
        }
    }
}

/**
 * Parse the given [lockfile] and fail if its format version is not supported.
 */
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

/** Return the last path segment of this URL without any query string or fragment. */
internal fun String.getFilenameFromUrl(): String = substringBefore('#').substringBefore('?').substringAfterLast('/')

/**
 * Normalize this Python package name as described in
 * https://packaging.python.org/en/latest/specifications/name-normalization/.
 */
internal fun String.normalizePythonPackageName(): String = PACKAGE_NAME_SEPARATOR_REGEX.replace(this, "-").lowercase()

/**
 * Convert these hashes, keyed by the algorithm name, to a [Hash] of the strongest known algorithm, or [Hash.NONE] if
 * no algorithm is known. A malformed value is skipped with a warning.
 */
internal fun Map<String, String>.toHash(): Hash {
    val hashesByAlgorithm = mapKeys { (algorithm, _) -> HashAlgorithm.fromString(algorithm) }

    return HASH_ALGORITHM_PREFERENCE.firstNotNullOfOrNull { algorithm ->
        hashesByAlgorithm[algorithm]?.let { value ->
            runCatching { Hash(value, algorithm) }.onFailure { logger.warn { it.message.orEmpty() } }.getOrNull()
        }
    } ?: Hash.NONE
}

/** Convert this distribution to a [RemoteArtifact], or return null if it only has a local path. */
internal fun PylockFile.Distribution.toRemoteArtifact(): RemoteArtifact? =
    url?.let { RemoteArtifact(it, hashes.toHash()) }

/** Convert this archive to a [RemoteArtifact], or return null if it only has a local path. */
internal fun PylockFile.Archive.toRemoteArtifact(): RemoteArtifact? = url?.let { RemoteArtifact(it, hashes.toHash()) }
