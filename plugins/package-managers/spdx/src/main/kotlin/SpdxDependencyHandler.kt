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

import com.github.packageurl.PackageURL

import java.util.TreeMap

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
 * when looking for the element's own dependencies, independently of the ORT scope the dependency is added to: For
 * [LifecycleScopeType.BUILD], the dependencies between the builds that take the element as an input are followed, for
 * all other scopes the element's own dependency relationships in that scope are followed. The [context] is the SPDX
 * document the element belongs to, so that its relationships are always looked up in that document, even after other
 * documents have been processed.
 */
data class SpdxDependency(
    val element: SpdxPackage,
    val scope: LifecycleScopeType,
    val context: SpdxDocumentContext
)

/**
 * The relationships and packages of a single SPDX document, indexed for fast lookup. Note that this intentionally is no
 * data class, so that contexts of different documents are never equal, even if their contents are.
 */
class SpdxDocumentContext(relationships: List<Relationship>, packages: List<SpdxPackage>) {
    // A cache to directly map an SPDX [SpdxPackage] to a PURL, if any.
    private val purls = mutableMapOf<SpdxPackage, String?>()

    // Relationships keyed by the SPDX ID of the source element. This map answers the question: "Which relationships
    // originate from the element with the given SPDX ID?"
    private val relationshipsBySource = mutableMapOf<String?, MutableList<Relationship>>()

    // Relationships keyed by the SPDX ID of the target element. This map answers the question: "Which relationships
    // refer to the element with the given SPDX ID?"
    private val relationshipsByTarget = mutableMapOf<String, MutableList<Relationship>>()

    init {
        relationships.forEach { rel ->
            relationshipsBySource.getOrPut(rel.from?.objectUri) { mutableListOf() } += rel

            rel.tos.forEach { to ->
                relationshipsByTarget.getOrPut(to.objectUri) { mutableListOf() } += rel
            }
        }
    }

    // SPDX packages keyed by the ORT identifier they map to. Note that Yocto SPDX files often contain packages that
    // only differ in the SPDX ID, but not in other inline properties.
    private val packagesById = packages.groupBy { identifierFor(it) }

    fun getElementsReferredFrom(element: Element, vararg types: RelationshipType): Set<Element> =
        relationshipsBySource[element.objectUri]?.run {
            if (types.isNotEmpty()) filter { it.relationshipType in types } else this
        }?.flatMapTo(mutableSetOf()) { it.tos }.orEmpty()

    fun getElementsReferringTo(element: Element, vararg types: RelationshipType): Set<Element> =
        relationshipsByTarget[element.objectUri]?.run {
            if (types.isNotEmpty()) filter { it.relationshipType in types } else this
        }?.mapNotNullTo(mutableSetOf()) { it.from }.orEmpty()

    /**
     * Return the packages that are inputs to the given [build] as build dependencies. As builds without any input
     * packages, like those for package groups, only aggregate other builds, return their build dependencies instead,
     * see [getBuildDependencies].
     */
    fun getBuildInputs(build: Build, visited: MutableSet<Build> = mutableSetOf()): Set<SpdxDependency> {
        val inputPackages = getElementsReferredFrom(build, RelationshipType.HAS_INPUT).filterIsInstance<SpdxPackage>()
        if (inputPackages.isEmpty()) return getBuildDependencies(build, visited)

        return inputPackages.mapTo(mutableSetOf()) { SpdxDependency(it, LifecycleScopeType.BUILD, this) }
    }

    /**
     * Return what the given [build] depends on at build time. For other builds, these are their inputs, see
     * [getBuildInputs]. Packages the [build] directly depends on, like tools, are followed as runtime dependencies, as
     * their own dependencies are what they need at runtime.
     */
    fun getBuildDependencies(build: Build, visited: MutableSet<Build> = mutableSetOf()): Set<SpdxDependency> {
        if (!visited.add(build)) return emptySet()

        return getDependencies(build, LifecycleScopeType.BUILD).flatMapTo(mutableSetOf()) { dependency ->
            when (dependency) {
                is Build -> getBuildInputs(dependency, visited)
                is SpdxPackage -> setOf(SpdxDependency(dependency, LifecycleScopeType.RUNTIME, this))
                else -> emptySet()
            }
        }
    }

    /**
     * Return the elements the given [element] depends on in the given lifecycle [scope].
     */
    fun getDependencies(element: Element, scope: LifecycleScopeType): Set<Element> =
        relationshipsBySource[element.objectUri].orEmpty()
            .filter { it.relationshipType in dependencyTypes && it.lifecycleScopeOrRuntime == scope }
            .flatMapTo(mutableSetOf()) { it.tos }

    fun getPurl(pkg: SpdxPackage): String? = purls.getOrPut(pkg) { pkg.purl }

    /**
     * Return all packages in this document that map to the same ORT identifier as the given [pkg].
     */
    fun getPackagesWithSameId(pkg: SpdxPackage): List<SpdxPackage> = packagesById[identifierFor(pkg)] ?: listOf(pkg)

    fun identifierFor(pkg: SpdxPackage): Identifier =
        // Prefer a PURL as it might contain proper type and namespace information.
        getPurl(pkg)?.toPackageUrl()?.withSanitizedVersion()?.toIdentifier()
            // Yocto does not set a package version for source packages, so try to derive it from the source itself.
            ?: pkg.primaryPurpose.getOrNull()
                ?.takeIf { it == SoftwarePurpose.SOURCE }
                ?.let { pkg.getSourcePackageId() }
            ?: Identifier(
                type = PACKAGE_TYPE_SPDX,
                namespace = "",
                // Fall back to the unique SPDX ID as the name is optional.
                name = (pkg.name.getOrNull() ?: pkg.id.fileSystemEncode()).sanitize(),
                version = pkg.packageVersion.getOrDefault("").sanitize()
            )
}

/**
 * A [DependencyHandler] for SPDX documents. As the same handler is used for all SPDX documents analyzed by a package
 * manager instance, it does not hold any document state itself. Instead, each [SpdxDependency] carries the
 * [SpdxDocumentContext] of its document, so that the dependency graph builder can still correctly compare dependencies
 * of a package from an earlier document with those of a package from a later document.
 */
class SpdxDependencyHandler : DependencyHandler<SpdxDependency> {
    override fun identifierFor(dependency: SpdxDependency): Identifier =
        dependency.context.identifierFor(dependency.element)

    override fun dependenciesFor(dependency: SpdxDependency): List<SpdxDependency> {
        val context = dependency.context

        if (dependency.scope == LifecycleScopeType.BUILD) {
            // Build dependencies are usually not modeled between source packages, but between the builds that take the
            // source packages as inputs. So use the build dependencies of these builds.
            val buildDependencies = context.getElementsReferringTo(dependency.element, RelationshipType.HAS_INPUT)
                .filterIsInstance<Build>()
                .flatMapTo(mutableSetOf()) { context.getBuildDependencies(it) }
                .filterNot { it.element == dependency.element }

            // Source packages may also directly depend on packages at build time. Like for direct build dependencies of
            // the project, their own dependencies are what they need at runtime, so follow these.
            val directBuildDependencies = context.getDependencies(dependency.element, LifecycleScopeType.BUILD)
                .filterIsInstance<SpdxPackage>()

            // Packages contained in source packages, like bundled libraries, are part of them independently of the
            // lifecycle scope, so follow these like direct build dependencies.
            val containedPackages = context.getElementsReferredFrom(dependency.element, RelationshipType.CONTAINS)
                .filterIsInstance<SpdxPackage>()

            return buildDependencies + (directBuildDependencies + containedPackages).distinct()
                .map { SpdxDependency(it, LifecycleScopeType.RUNTIME, context) }
        }

        return context.getDependencies(dependency.element, dependency.scope)
            .filterIsInstance<SpdxPackage>()
            .map { SpdxDependency(it, dependency.scope, context) }
    }

    override fun linkageFor(dependency: SpdxDependency): PackageLinkage {
        val context = dependency.context

        // The linkage is determined per package from all relationships referring to it, not per edge from the
        // relationship of the respective dependent package. This is because the dependent package is not known here,
        // and the dependency graph builder would not keep apart references that only differ in linkage anyway.
        return when {
            context.getElementsReferringTo(dependency.element, RelationshipType.HAS_DYNAMIC_LINK).isNotEmpty() ->
                PackageLinkage.DYNAMIC

            context.getElementsReferringTo(
                dependency.element,
                RelationshipType.HAS_STATIC_LINK,
                RelationshipType.CONTAINS
            ).isNotEmpty() -> PackageLinkage.STATIC

            else -> PackageLinkage.DYNAMIC
        }
    }

    override fun createPackage(dependency: SpdxDependency, issues: MutableCollection<Issue>): Package =
        dependency.context.createPackage(dependency.element)
}

internal fun SpdxDocumentContext.createPackage(dependency: SpdxPackage): Package {
    val id = identifierFor(dependency)

    // As licenses are indirectly defined via relationships based on the SPDX ID, ensure to merge the licenses of all
    // packages that map to the same identifier.
    // TODO: This only merges licenses within the current document. As the dependency graph builder only creates a
    //       package for the first occurrence of an identifier, the metadata of packages with the same identifier in
    //       other documents is ignored.
    val packagesWithSameId = getPackagesWithSameId(dependency)
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
        purl = getPurl(dependency) ?: id.toPurl(),
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

private fun SpdxDocumentContext.getLicenses(element: Element, type: RelationshipType): List<String> =
    getElementsReferredFrom(element, type).filterIsInstance<LicenseExpression>().mapNotNull { it.licenseExpression }

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
    val host = authorityAndPath.substringBefore('/').substringAfterLast('@').let { hostAndPort ->
        // Drop any port, and the brackets around IPv6 addresses.
        if (hostAndPort.startsWith('[')) hostAndPort.substringBefore(']').drop(1) else hostAndPort.substringBefore(':')
    }

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
        namespace = namespace.sanitize(),
        name = sourceName.sanitize(),
        version = (packageVersion.getOrNull() ?: sourceVersion).sanitize()
    )
}

/**
 * Return this string with all colons replaced, as an [Identifier]'s properties must not contain colons. Colons occur,
 * for example, in versions with an epoch like "1:2.3" or in IPv6 addresses.
 */
internal fun String.sanitize() = replace(':', '_')

/**
 * Return this [PackageURL] with a sanitized version, see [sanitize].
 */
private fun PackageURL.withSanitizedVersion(): PackageURL =
    if (version?.contains(':') == true) {
        PackageURL(type, namespace, name, version.sanitize(), qualifiers?.let { TreeMap(it) }, subpath)
    } else {
        this
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
            val url = revisionSeparator?.let { urlWithRevision.take(it) } ?: urlWithRevision

            VcsInfo(
                type = vcsType,
                url = if (vcsType == VcsType.SUBVERSION && url.startsWith("ssh://")) "$prefix$url" else url,
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

// Also treat linked and contained packages as dependencies, as their licenses apply to the linking or containing
// package, too.
private val dependencyTypes = enumSetOf(
    RelationshipType.CONTAINS,
    RelationshipType.DEPENDS_ON,
    RelationshipType.HAS_DYNAMIC_LINK,
    RelationshipType.HAS_OPTIONAL_DEPENDENCY,
    RelationshipType.HAS_PROVIDED_DEPENDENCY,
    RelationshipType.HAS_STATIC_LINK
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
