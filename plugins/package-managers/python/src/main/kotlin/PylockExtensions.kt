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

import java.lang.invoke.MethodHandles

import net.peanuuutz.tomlkt.TomlTable

import org.apache.logging.log4j.kotlin.loggerOf

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.ProjectResults
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.plugins.packagemanagers.python.utils.PACKAGE_TYPE
import org.ossreviewtoolkit.plugins.packagemanagers.python.utils.PythonCoreMetadata
import org.ossreviewtoolkit.plugins.packagemanagers.python.utils.processDeclaredLicenses

private val logger = loggerOf(MethodHandles.lookup().lookupClass())

/** The name of the scope for packages that do not belong to a dependency group, which is also used by PIP. */
internal const val DEFAULT_SCOPE_NAME = "install"

/**
 * Convert this lockfile entry to an ORT package, using the [metadata] from the index, if any, for the licenses,
 * authors, description, homepage and VCS location.
 */
internal fun PylockFile.Package.toOrtPackage(metadata: PythonCoreMetadata?): Package {
    val id = toIdentifier()
    val declaredLicenses = metadata?.getDeclaredLicenses().orEmpty()
    val (binaryArtifact, sourceArtifact) = getArtifacts()

    val vcsInfo = vcs?.let {
        VcsInfo(
            type = VcsType.forName(it.type),
            url = it.url ?: it.path.orEmpty(),
            revision = it.commitId,
            path = it.subdirectory.orEmpty()
        )
    } ?: VcsInfo.EMPTY

    // Packages that do not come from an index are not identified by name and version alone, so record where they come
    // from in the purl.
    val purlQualifiers = buildMap {
        vcs?.let { put("vcs_url", "${it.type}+${vcsInfo.url}@${it.commitId}") }
        archive?.url?.let { put("download_url", it) }
    }

    return Package(
        id = id,
        purl = id.toPurl(purlQualifiers),
        authors = metadata?.getAuthors().orEmpty(),
        declaredLicenses = declaredLicenses,
        declaredLicensesProcessed = processDeclaredLicenses(id, declaredLicenses),
        description = metadata?.summary.orEmpty(),
        homepageUrl = metadata?.getHomepageUrl().orEmpty(),
        binaryArtifact = binaryArtifact,
        sourceArtifact = sourceArtifact,
        vcs = vcsInfo,
        vcsProcessed = PackageManager.processPackageVcs(
            vcsInfo,
            *metadata?.getVcsFallbackUrls().orEmpty().toTypedArray()
        )
    )
}

/**
 * Make the packages with the same identifier identical across these project results, as ORT requires. Several
 * lockfiles may lock the same package with different distribution files, e.g. from different indexes. The package
 * with a source artifact is preferred, as the scanner works with it. Packages with different purls come from
 * different sources altogether, so losing one of them is logged.
 */
internal fun ProjectResults.unifyPackages(): ProjectResults {
    val packagesById = values.flatten().flatMap { it.packages }.groupBy { it.id }.mapValues { (id, packages) ->
        val purls = packages.mapTo(mutableSetOf()) { it.purl }
        if (purls.size > 1) {
            logger.warn {
                "The package '${id.toCoordinates()}' is locked from different sources $purls, of which only one " +
                    "can be kept."
            }
        }

        packages.find { it.sourceArtifact != RemoteArtifact.EMPTY } ?: packages.first()
    }

    return mapValues { (_, results) ->
        results.map { result ->
            result.copy(packages = result.packages.mapTo(mutableSetOf()) { packagesById.getValue(it.id) })
        }
    }
}

/**
 * Return the identifier of this lockfile entry. A VCS entry does not need to record a version, so its commit ID is
 * used instead.
 */
internal fun PylockFile.Package.toIdentifier(): Identifier =
    Identifier(type = PACKAGE_TYPE, namespace = "", name = normalizedName, version = version ?: vcs?.commitId.orEmpty())

/**
 * Return the binary and the source artifact of this lockfile entry. As a lockfile usually lists one wheel per
 * platform, the binary artifact is only set to a universal wheel, or to the single wheel if there is just one.
 */
private fun PylockFile.Package.getArtifacts(): Pair<RemoteArtifact, RemoteArtifact> {
    archive?.toRemoteArtifact()?.let { artifact ->
        val isWheel = artifact.url.getFilenameFromUrl().endsWith(".whl")
        return if (isWheel) artifact to RemoteArtifact.EMPTY else RemoteArtifact.EMPTY to artifact
    }

    val wheel = wheels.singleOrNull() ?: wheels.find { it.isUniversalWheel }

    return (wheel?.toRemoteArtifact() ?: RemoteArtifact.EMPTY) to (sdist?.toRemoteArtifact() ?: RemoteArtifact.EMPTY)
}

/**
 * Group these lockfile entries into scopes. An entry belongs to the scope of each dependency group that can require
 * it according to its marker, and to the [DEFAULT_SCOPE_NAME] scope if it can be required without any group. Only
 * the dependency relationships declared in the lockfile are used, and as none of the common tools writes them yet,
 * the scopes are usually flat lists. Entries for local directories are not packages, so an entry that depends on
 * such a directory gets the directory's dependencies instead.
 */
internal fun Collection<PylockFile.Package>.toScopes(): Set<Scope> {
    val packageEntries = filter { it.directory == null }

    val entriesByScopeName = sortedMapOf<String, MutableList<PylockFile.Package>>()

    packageEntries.forEach { entry ->
        val membership = entry.getGroupMembership()
        val scopeNames = buildSet {
            addAll(membership.groups)
            if (membership.isRequiredWithoutGroups) add(DEFAULT_SCOPE_NAME)
        }

        scopeNames.forEach { entriesByScopeName.getOrPut(it) { mutableListOf() } += entry }
    }

    val entriesByName = groupBy { it.normalizedName }
    val declaredDependencies = associateWith { entry ->
        entry.dependencies.mapNotNull { entriesByName.resolveDependency(it) }
    }

    fun PylockFile.Package.getPackageDependencies(visited: MutableSet<PylockFile.Package>): List<PylockFile.Package> =
        declaredDependencies.getValue(this).flatMap { dependency ->
            when {
                dependency.directory == null -> listOf(dependency)
                visited.add(dependency) -> dependency.getPackageDependencies(visited)
                else -> emptyList()
            }
        }

    val dependencies = packageEntries.associateWith { it.getPackageDependencies(mutableSetOf()).distinct() }

    return entriesByScopeName.mapTo(mutableSetOf()) { (name, entries) ->
        Scope(name, entries.toPackageReferences(dependencies))
    }
}

/**
 * Return the entry the given dependency [reference] refers to, or null if it does not refer to exactly one entry,
 * which indicates a broken lockfile.
 */
private fun Map<String, List<PylockFile.Package>>.resolveDependency(reference: TomlTable): PylockFile.Package? {
    val entries = reference.getReferencedName()?.let { this[it].orEmpty() } ?: values.flatten()
    val candidates = entries.filter { it.isReferencedBy(reference) }

    return candidates.singleOrNull().also {
        if (it == null) {
            logger.warn {
                "The dependency reference $reference resolves to ${candidates.size} lockfile entries instead of " +
                    "exactly one, so it is ignored."
            }
        }
    }
}

/**
 * Build the dependency trees of a scope from these entries. Only dependencies within the scope are followed, as an
 * entry of another dependency group is not installed with this scope. The roots are the entries no other entry of
 * the scope depends on. Entries that only depend on each other in a cycle get the first of them as a root.
 */
private fun List<PylockFile.Package>.toPackageReferences(
    dependencies: Map<PylockFile.Package, List<PylockFile.Package>>
): Set<PackageReference> {
    val entries = toSet()
    val scopeDependencies = associateWith { dependencies.getValue(it).filter { entry -> entry in entries } }
    val dependedOn = scopeDependencies.values.flatten().toSet()

    val covered = mutableSetOf<PylockFile.Package>()

    fun cover(entry: PylockFile.Package) {
        if (covered.add(entry)) scopeDependencies.getValue(entry).forEach(::cover)
    }

    val roots = filterNot { it in dependedOn }.toMutableList()
    roots.forEach(::cover)

    forEach { entry ->
        if (entry !in covered) {
            roots += entry
            cover(entry)
        }
    }

    return roots.mapTo(mutableSetOf()) { it.toPackageReference(scopeDependencies, ancestors = emptySet()) }
}

/**
 * Convert this lockfile entry to a package reference with its resolved [dependencies]. Entries among the [ancestors]
 * are skipped to break dependency cycles.
 */
private fun PylockFile.Package.toPackageReference(
    dependencies: Map<PylockFile.Package, List<PylockFile.Package>>,
    ancestors: Set<PylockFile.Package>
): PackageReference {
    val ancestorsAndSelf = ancestors + this

    val dependencyReferences = dependencies.getValue(this)
        .filterNot { it in ancestorsAndSelf }
        .mapTo(mutableSetOf()) { it.toPackageReference(dependencies, ancestorsAndSelf) }

    return PackageReference(id = toIdentifier(), dependencies = dependencyReferences)
}
