/*
 * Copyright (C) 2019 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

@file:Suppress("MatchingDeclarationName", "TooManyFunctions")

package org.ossreviewtoolkit.helper.utils

import java.io.File

import org.ossreviewtoolkit.analyzer.withResolvedScopes
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.LicenseFindingCuration
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.writeValue
import org.ossreviewtoolkit.utils.common.encodeOrUnknown
import org.ossreviewtoolkit.utils.common.fileSystemEncode
import org.ossreviewtoolkit.utils.common.isSymbolicLink
import org.ossreviewtoolkit.utils.common.replaceCredentialsInUri
import org.ossreviewtoolkit.utils.common.withoutPrefix
import org.ossreviewtoolkit.utils.spdx.SpdxExpression

const val ORTH_NAME = "orth"

/**
 * Represents a mapping from repository URLs to list of [PathExclude]s for the respective repository.
 */
internal typealias RepositoryPathExcludes = Map<String, List<PathExclude>>

internal typealias RepositoryLicenseFindingCurations = Map<String, List<LicenseFindingCuration>>

/**
 * Return all files underneath the given [directory].
 */
internal fun findFilesRecursive(directory: File): List<String> {
    require(directory.isDirectory)
    return directory.walk()
        .onEnter { !it.isSymbolicLink }
        .filter { !it.isSymbolicLink && it.isFile }
        .mapTo(mutableListOf()) { it.relativeTo(directory).path }
}

/**
 * Search the given [directory] for repositories and return a mapping from repository URLs to the relative paths where
 * each respective repository was found.
 */
internal fun findRepositoryPaths(directory: File): Map<String, Set<String>> {
    require(directory.isDirectory)

    val result = mutableMapOf<String, MutableSet<String>>()

    findRepositories(directory).forEach { (path, vcs) ->
        result.getOrPut(vcs.url.replaceCredentialsInUri()) { mutableSetOf() } += path
    }

    return result
}

/**
 * Search the given [directory] for repositories and return a mapping from paths where each respective repository was
 * found to the corresponding [VcsInfo].
 */
internal fun findRepositories(directory: File): Map<String, VcsInfo> {
    require(directory.isDirectory)

    val workingTree = VersionControlSystem.forDirectory(directory)
    return workingTree?.getNested()?.filter { (path, _) ->
        // Only include nested VCS if they are part of the analyzed directory.
        workingTree.getRootPath().resolve(path).startsWith(directory)
    }.orEmpty()
}

/**
 * Build the file for the split curations.
 */
internal fun getSplitCurationFile(parent: File, packageId: Identifier, fileExtension: String) =
    parent.resolve(packageId.type.encodeOrUnknown())
        .resolve(packageId.namespace.ifBlank { "_" }.fileSystemEncode())
        .resolve("${packageId.name.encodeOrUnknown()}.$fileExtension")

/**
 * Return an approximation for the Set-Cover Problem, see https://en.wikipedia.org/wiki/Set_cover_problem.
 * If there is a tie in the selection of the next best element, [tieComparator] can be used to inject a selection
 * preference. By default, there is no preference so the first best element is selected.
 */
internal fun <K, V> greedySetCover(
    sets: Map<K, Set<V>>,
    tieComparator: Comparator<K> = Comparator { _, _ -> 0 }
): Set<K> {
    val result = mutableSetOf<K>()

    val uncovered = sets.values.flatten().toMutableSet()
    val queue = sets.entries.toMutableSet()

    val comparator = compareBy<Map.Entry<K, Set<V>>> {
        it.value.intersect(uncovered).size
    }.thenComparing { a, b ->
        tieComparator.compare(a.key, b.key)
    }

    while (queue.isNotEmpty()) {
        val maxCover = queue.maxWith(comparator)

        if (uncovered.intersect(maxCover.value).isNotEmpty()) {
            uncovered.removeAll(maxCover.value)
            queue.remove(maxCover)
            result += maxCover.key
        } else {
            break
        }
    }

    return result
}

/**
 * A processed copyright statement.
 */
internal data class ProcessedCopyrightStatement(
    /**
     * The package containing the copyright statement.
     */
    val packageId: Identifier,

    /**
     * The license associated with the copyright statement.
     */
    val license: SpdxExpression,

    /**
     * The processed copyright statement.
     */
    val statement: String,

    /**
     * The original statement(s) which yield this processed [statement].
     */
    val rawStatements: Set<String>
) {
    init {
        require(rawStatements.isNotEmpty()) { "The set of raw statements must not be empty." }
    }
}

/**
 * Return all license finding curations from [curations] represented as [RepositoryLicenseFindingCurations].
 */
internal fun getLicenseFindingCurationsByRepository(
    curations: Collection<LicenseFindingCuration>,
    nestedRepositories: Map<String, VcsInfo>
): RepositoryLicenseFindingCurations {
    val result = mutableMapOf<String, MutableList<LicenseFindingCuration>>()

    nestedRepositories.forEach { (path, vcs) ->
        val pathExcludesForRepository = result.getOrPut(vcs.url) { mutableListOf() }
        curations.forEach { curation ->
            curation.path.withoutPrefix("$path/")?.let {
                pathExcludesForRepository += curation.copy(path = it)
            }
        }
    }

    return result.mapValues { excludes -> excludes.value.sortedBy { it.path } }.toSortedMap()
}

/**
 * Return all path excludes from [pathExcludes] represented as [RepositoryPathExcludes].
 */
internal fun getPathExcludesByRepository(
    pathExcludes: Collection<PathExclude>,
    nestedRepositories: Map<String, VcsInfo>
): RepositoryPathExcludes {
    val result = mutableMapOf<String, MutableList<PathExclude>>()

    nestedRepositories.forEach { (path, vcs) ->
        val pathExcludesForRepository = result.getOrPut(vcs.url) { mutableListOf() }
        pathExcludes.forEach { pathExclude ->
            pathExclude.pattern.withoutPrefix("$path/")?.let {
                pathExcludesForRepository += pathExclude.copy(pattern = it)
            }
        }
    }

    return result.mapValues { excludes -> excludes.value.sortedBy { it.pattern } }.toSortedMap()
}

/**
 * Read a list of [PackageCuration]s from the given [file].
 */
internal fun readPackageCurations(file: File): List<PackageCuration> =
    if (file.isFile) {
        file.readValue()
    } else {
        emptyList()
    }

internal fun importPathExcludes(
    repositoryPaths: Map<String, Set<String>>,
    pathExcludesFile: File,
    vcsUrlMapping: VcsUrlMapping
): List<PathExclude> {
    val result = mutableListOf<PathExclude>()
    val pathExcludes = pathExcludesFile.readValue<RepositoryPathExcludes>()

    println("Found ${repositoryPaths.size} repositories in ${repositoryPaths.values.sumOf { it.size }} locations.")
    println("Found ${pathExcludes.values.sumOf { it.size }} excludes for ${pathExcludes.size} repositories.")

    repositoryPaths.mapKeys { vcsUrlMapping.map(it.key) }.forEach { (vcsUrl, relativePaths) ->
        pathExcludes[vcsUrl]?.let { pathExcludesForRepository ->
            pathExcludesForRepository.forEach { pathExclude ->
                relativePaths.forEach { path ->
                    result += pathExclude.copy(pattern = path + '/' + pathExclude.pattern)
                }
            }
        }
    }

    return result
}

internal fun importLicenseFindingCurations(
    repositoryPaths: Map<String, Set<String>>,
    licenseFindingCurationsFile: File,
    vcsUrlMapping: VcsUrlMapping
): List<LicenseFindingCuration> {
    val curations = licenseFindingCurationsFile.readValue<RepositoryLicenseFindingCurations>()
    val result = mutableListOf<LicenseFindingCuration>()

    println("Found ${repositoryPaths.size} repositories in ${repositoryPaths.values.sumOf { it.size }} locations.")
    println("Found ${curations.values.sumOf { it.size }} curations for ${curations.size} repositories.")

    repositoryPaths.mapKeys { vcsUrlMapping.map(it.key) }.forEach { (vcsUrl, relativePaths) ->
        curations[vcsUrl]?.let { curationsForRepository ->
            curationsForRepository.forEach { curation ->
                relativePaths.forEach { path ->
                    result += curation.copy(path = path + '/' + curation.path)
                }
            }
        }
    }

    return result
}

/**
 * Read [ortFile] into an [OrtResult] and return it. Make sure that information about project scopes is available
 * (by calling [OrtResult.withResolvedScopes]), so that it can be processed.
 */
internal fun readOrtResult(ortFile: File): OrtResult = ortFile.readValue<OrtResult>().withResolvedScopes()

/**
 * Write the [ortResult] to [file].
 */
internal fun writeOrtResult(ortResult: OrtResult, file: File): Unit = file.writeValue(ortResult)
