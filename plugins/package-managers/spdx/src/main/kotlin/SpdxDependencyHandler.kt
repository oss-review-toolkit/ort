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

package org.ossreviewtoolkit.plugins.packagemanagers.spdx

import kotlin.jvm.optionals.getOrDefault
import kotlin.jvm.optionals.getOrNull

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.model.orNone
import org.ossreviewtoolkit.model.utils.DependencyHandler
import org.ossreviewtoolkit.model.utils.toIdentifier
import org.ossreviewtoolkit.model.utils.toPackageUrl
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.utils.common.enumSetOf
import org.ossreviewtoolkit.utils.common.fileSystemEncode
import org.ossreviewtoolkit.utils.common.withoutPrefix
import org.ossreviewtoolkit.utils.spdxexpression.toExpression
import org.ossreviewtoolkit.utils.spdxexpression.toSpdxOrNull

import org.spdx.library.model.v3_0_1.build.Build
import org.spdx.library.model.v3_0_1.core.Element
import org.spdx.library.model.v3_0_1.core.ExternalIdentifierType
import org.spdx.library.model.v3_0_1.core.Hash as SpdxHash
import org.spdx.library.model.v3_0_1.core.LifecycleScopeType
import org.spdx.library.model.v3_0_1.core.LifecycleScopedRelationship
import org.spdx.library.model.v3_0_1.core.Relationship
import org.spdx.library.model.v3_0_1.core.RelationshipType
import org.spdx.library.model.v3_0_1.simplelicensing.LicenseExpression
import org.spdx.library.model.v3_0_1.software.SoftwarePurpose
import org.spdx.library.model.v3_0_1.software.SpdxPackage

/**
 * An SPDX [element] as a dependency in the given lifecycle [scope]. The scope determines which relationships to follow
 * when looking for the element's own dependencies.
 */
data class SpdxDependency(val element: SpdxPackage, val scope: LifecycleScopeType)

/**
 * A [DependencyHandler] for SPDX documents. As the same handler is used for all SPDX documents analyzed by a package
 * manager instance, [setDocument] has to be called before working with the elements of a document.
 *
 * Note that the dependency graph builder only queries this handler while adding dependencies, so all data of earlier
 * documents is fully resolved by the time the next document is set. The only exception is the comparison of the
 * dependencies of a package that already is part of the graph from an earlier document, which then are looked up in the
 * current document by their SPDX ID.
 */
class SpdxDependencyHandler : DependencyHandler<SpdxDependency> {
    // A cache to directly map an SPDX [SpdxPackage] to a PURL, if any.
    private val purls = mutableMapOf<SpdxPackage, String?>()

    // SPDX packages keyed by the ORT identifier they map to. Note that Yocto SPDX files often contain packages that
    // only differ in the SPDX ID, but not in other inline properties.
    private val packagesById = mutableMapOf<Identifier, List<SpdxPackage>>()

    // Relationships keyed by the SPDX ID of the source element. This map answers the question: "Which relationships
    // originate from the element with the given SPDX ID?"
    private val relationshipsBySource = mutableMapOf<String?, MutableList<Relationship>>()

    // Relationships keyed by the SPDX ID of the target element. This map answers the question: "Which relationships
    // refer to the element with the given SPDX ID?"
    private val relationshipsByTarget = mutableMapOf<String, MutableList<Relationship>>()

    /**
     * Set the [relationships] and [packages] of the SPDX document to work with, replacing those of any previous
     * document.
     */
    fun setDocument(relationships: List<Relationship>, packages: List<SpdxPackage>) {
        purls.clear()
        packagesById.clear()
        relationshipsBySource.clear()
        relationshipsByTarget.clear()

        relationships.forEach { rel ->
            relationshipsBySource.getOrPut(rel.from?.objectUri) { mutableListOf() } += rel

            rel.tos.forEach { to ->
                relationshipsByTarget.getOrPut(to.objectUri) { mutableListOf() } += rel
            }
        }

        packagesById += packages.groupBy { identifierFor(it) }
    }

    fun getElementsReferredFrom(element: Element, vararg types: RelationshipType): Set<Element> =
        relationshipsBySource[element.objectUri]?.run {
            if (types.isNotEmpty()) filter { it.relationshipType in types } else this
        }?.flatMapTo(mutableSetOf()) { it.tos }.orEmpty()

    fun getElementsReferringTo(element: Element, vararg types: RelationshipType): Set<Element> =
        relationshipsByTarget[element.objectUri]?.run {
            if (types.isNotEmpty()) filter { it.relationshipType in types } else this
        }?.mapNotNullTo(mutableSetOf()) { it.from }.orEmpty()

    /**
     * Return the packages that are inputs to the given [build]. As builds without any input packages, like those for
     * package groups, only aggregate other builds, return the input packages of the builds they depend on instead.
     */
    fun getInputPackages(build: Build, visited: MutableSet<Build> = mutableSetOf()): Set<SpdxPackage> {
        if (!visited.add(build)) return emptySet()

        val inputPackages = getElementsReferredFrom(build, RelationshipType.HAS_INPUT).filterIsInstance<SpdxPackage>()
        if (inputPackages.isNotEmpty()) return inputPackages.toSet()

        return getDependencies(build, LifecycleScopeType.BUILD).filterIsInstance<Build>()
            .flatMapTo(mutableSetOf()) { getInputPackages(it, visited) }
    }

    override fun identifierFor(dependency: SpdxDependency): Identifier = identifierFor(dependency.element)

    private fun identifierFor(pkg: SpdxPackage): Identifier {
        val purl = purls.getOrPut(pkg) { pkg.purl }

        // Prefer a PURL as it might contain proper type and namespace information.
        return purl?.toPackageUrl()?.toIdentifier()
            // Yocto does not set a package version for source packages, so try to derive it from the source itself.
            ?: pkg.primaryPurpose.getOrNull()
                ?.takeIf { it == SoftwarePurpose.SOURCE }
                ?.let { pkg.getSourcePackageId() }
            ?: Identifier(
                type = PACKAGE_TYPE_SPDX,
                namespace = "",
                // Fall back to the unique SPDX ID as the name is optional.
                name = pkg.name.getOrNull() ?: pkg.id.fileSystemEncode(),
                version = pkg.packageVersion.getOrDefault("")
            )
    }

    override fun dependenciesFor(dependency: SpdxDependency): List<SpdxDependency> {
        val dependencies = when (dependency.scope) {
            // Build dependencies are not modeled between source packages, but between the builds that take the source
            // packages as inputs. So map the dependencies between these builds back to their input packages.
            LifecycleScopeType.BUILD -> getElementsReferringTo(dependency.element, RelationshipType.HAS_INPUT)
                .filterIsInstance<Build>()
                .flatMap { getDependencies(it, LifecycleScopeType.BUILD) }
                .filterIsInstance<Build>()
                .flatMapTo(mutableSetOf()) { getInputPackages(it) } - dependency.element

            else -> getDependencies(dependency.element, dependency.scope).filterIsInstance<SpdxPackage>()
        }

        return dependencies.map { SpdxDependency(it, dependency.scope) }
    }

    private fun getDependencies(element: Element, scope: LifecycleScopeType): Set<Element> =
        relationshipsBySource[element.objectUri].orEmpty()
            .filter { it.relationshipType in dependencyTypes && it.lifecycleScopeOrRuntime == scope }
            .flatMapTo(mutableSetOf()) { it.tos }

    override fun linkageFor(dependency: SpdxDependency) =
        when {
            getElementsReferringTo(dependency.element, RelationshipType.HAS_DYNAMIC_LINK).isNotEmpty() ->
                PackageLinkage.DYNAMIC

            getElementsReferringTo(dependency.element, RelationshipType.HAS_STATIC_LINK, RelationshipType.CONTAINS)
                .isNotEmpty() -> PackageLinkage.STATIC

            else -> PackageLinkage.DYNAMIC
        }

    override fun createPackage(dependency: SpdxDependency, issues: MutableCollection<Issue>): Package =
        createPackage(dependency.element)

    private fun createPackage(dependency: SpdxPackage): Package {
        purls.getOrPut(dependency) { dependency.purl }

        val id = identifierFor(dependency)

        // As licenses are indirectly defined via relationships based on the SPDX ID, ensure to merge the licenses of
        // all packages that map to the same identifier.
        val packagesWithSameId = packagesById[id] ?: listOf(dependency)
        val declaredLicenses = packagesWithSameId.flatMapTo(mutableSetOf()) {
            getLicenses(it, RelationshipType.HAS_DECLARED_LICENSE)
        }

        val concludedLicenses = packagesWithSameId.flatMapTo(mutableSetOf()) {
            getLicenses(it, RelationshipType.HAS_CONCLUDED_LICENSE)
        }

        val description = dependency.description.getOrNull() ?: dependency.summary.getOrNull()

        // A download location either points to a VCS or to a source artifact.
        val vcs = dependency.getVcsInfo()
        val url = dependency.downloadLocation.getOrNull()
        val sourceArtifact = if (vcs == null && !url.isNullOrBlank()) {
            val hash = dependency.verifiedUsings.filterIsInstance<SpdxHash>().firstOrNull()?.let { spdxHash ->
                val algorithm = spdxHash.algorithm
                val value = spdxHash.hashValue
                if (algorithm != null && value != null) {
                    Hash(value, HashAlgorithm.fromString(algorithm.longName))
                } else {
                    Hash.NONE
                }
            }.orNone()

            RemoteArtifact(url, hash)
        } else {
            RemoteArtifact.EMPTY
        }

        return Package(
            id = id,
            purl = purls[dependency] ?: id.toPurl(),
            cpe = dependency.cpe,
            authors = emptySet(),
            declaredLicenses = declaredLicenses,
            concludedLicense = if (concludedLicenses.size > 1) {
                logger.warn { "Multiple concluded licenses found for ID '${dependency.id}', using their conjunction." }
                concludedLicenses.mapNotNull { it.toSpdxOrNull() }.toExpression()
            } else {
                concludedLicenses.singleOrNull()?.toSpdxOrNull()
            },
            description = description.orEmpty(),
            homepageUrl = dependency.homePage.getOrNull().orEmpty(),
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = sourceArtifact,
            vcs = vcs.orEmpty()
        )
    }

    private fun getLicenses(element: Element, type: RelationshipType): List<String> =
        getElementsReferredFrom(element, type).filterIsInstance<LicenseExpression>().mapNotNull { it.licenseExpression }
}

/**
 * Return an identifier for this source package. As Yocto names source packages after their archive file or VCS
 * repository without setting a version, derive the name and version from the download location or the name. Use the
 * download location's host as the namespace to distinguish source packages from the packages built from them. For VCS
 * locations, also add the repository's parent path to the namespace and the path inside the repository to the name, to
 * distinguish repositories of the same name and different directories in the same repository.
 */
internal fun SpdxPackage.getSourcePackageId(): Identifier? {
    val vcs = getVcsInfo()
    val location = vcs?.url ?: downloadLocation.getOrNull().orEmpty()
    val authorityAndPath = location.substringAfter("://", missingDelimiterValue = "")
    val host = authorityAndPath.substringBefore('/').substringAfterLast('@')

    val (namespace, sourceName, sourceVersion) = if (vcs != null) {
        val repoPath = authorityAndPath.substringAfter('/', missingDelimiterValue = "")
            .trimEnd('/')
            .removeSuffix(".git")
        val repoParent = repoPath.substringBeforeLast('/', missingDelimiterValue = "")
        val repoName = repoPath.substringAfterLast('/')

        Triple(
            listOf(host, repoParent).filter { it.isNotEmpty() }.joinToString("/"),
            listOf(repoName, vcs.path.trim('/')).filter { it.isNotEmpty() }.joinToString("/"),
            vcs.revision
        )
    } else {
        val fileName = name.getOrNull() ?: location.substringAfterLast('/')
        val baseName = fileName.replace(ARCHIVE_EXTENSION_REGEX, "")
        val match = NAME_VERSION_REGEX.matchEntire(baseName)

        Triple(host, match?.groupValues?.get(1) ?: baseName, match?.groupValues?.get(2).orEmpty())
    }

    if (sourceName.isEmpty()) return null

    return Identifier(
        type = PACKAGE_TYPE_SPDX,
        namespace = namespace,
        name = sourceName,
        version = packageVersion.getOrNull() ?: sourceVersion
    )
}

/**
 * Return the [VcsInfo] for the VCS location the download location of this package points to, or null if it does not
 * point to a VCS location. VCS locations are specified like "git+https://host/path/repo.git@revision#path".
 */
internal fun SpdxPackage.getVcsInfo(): VcsInfo? {
    val location = downloadLocation.getOrNull() ?: return null

    return SPDX_VCS_PREFIXES.firstNotNullOfOrNull { (prefix, vcsType) ->
        location.withoutPrefix(prefix)?.let { urlWithRevisionAndPath ->
            val path = urlWithRevisionAndPath.substringAfterLast('#', missingDelimiterValue = "")
            val urlWithRevision = urlWithRevisionAndPath.removeSuffix("#$path")

            // Only look for a revision after the host part, as the URL's user info is separated by "@", too.
            val pathStart = urlWithRevision.indexOf('/', urlWithRevision.indexOf("://") + 3)
            val revisionSeparator = urlWithRevision.lastIndexOf('@').takeIf { pathStart in 0..<it }

            VcsInfo(
                type = vcsType,
                url = revisionSeparator?.let { urlWithRevision.take(it) } ?: urlWithRevision,
                revision = revisionSeparator?.let { urlWithRevision.drop(it + 1) }.orEmpty(),
                path = path
            )
        }
    }
}

private val SPDX_VCS_PREFIXES = mapOf(
    "git+" to VcsType.GIT,
    "hg+" to VcsType.MERCURIAL,
    "bzr+" to VcsType.UNKNOWN,
    "svn+" to VcsType.SUBVERSION
)

private val ARCHIVE_EXTENSION_REGEX = Regex("""(\.orig)?\.(tar(\.[a-z0-9]+)?|tgz|tbz2?|txz|zip)$""")

// Match lazily up to the first separator followed by a digit, as names may contain separators and digits themselves.
private val NAME_VERSION_REGEX = Regex("""^(.+?)[-_](\d.*)$""")

private val dependencyTypes = enumSetOf(
    RelationshipType.DEPENDS_ON,
    RelationshipType.HAS_OPTIONAL_DEPENDENCY,
    RelationshipType.HAS_PROVIDED_DEPENDENCY
)

private val Relationship.lifecycleScopeOrRuntime
    // Treat dependency relationships without a lifecycle scope as runtime dependencies, as that is what a plain
    // "dependsOn" relationship usually expresses.
    get() = (this as? LifecycleScopedRelationship)?.scope?.getOrNull() ?: LifecycleScopeType.RUNTIME

private val SpdxPackage.purl
    get() = externalIdentifiers.filter {
        it.externalIdentifierType == ExternalIdentifierType.PACKAGE_URL
    }.firstNotNullOfOrNull { it.identifier }

private val SpdxPackage.cpe
    get() = externalIdentifiers.filter {
        it.externalIdentifierType in cpeTypes
    }.firstNotNullOfOrNull { it.identifier }

private val cpeTypes = enumSetOf(ExternalIdentifierType.CPE22, ExternalIdentifierType.CPE23)
