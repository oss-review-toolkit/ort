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
import org.ossreviewtoolkit.model.orNone
import org.ossreviewtoolkit.model.utils.DependencyHandler
import org.ossreviewtoolkit.model.utils.toIdentifier
import org.ossreviewtoolkit.model.utils.toPackageUrl
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.utils.common.enumSetOf
import org.ossreviewtoolkit.utils.common.withoutPrefix
import org.ossreviewtoolkit.utils.spdx.toExpression
import org.ossreviewtoolkit.utils.spdx.toSpdx
import org.ossreviewtoolkit.utils.spdx.toSpdxOrNull

import org.spdx.library.model.v3_0_1.core.Element
import org.spdx.library.model.v3_0_1.core.ExternalIdentifierType
import org.spdx.library.model.v3_0_1.core.Hash as SpdxHash
import org.spdx.library.model.v3_0_1.core.Relationship
import org.spdx.library.model.v3_0_1.core.RelationshipType
import org.spdx.library.model.v3_0_1.simplelicensing.LicenseExpression
import org.spdx.library.model.v3_0_1.software.SoftwarePurpose
import org.spdx.library.model.v3_0_1.software.SpdxPackage

class SpdxDependencyHandler(relationships: List<Relationship>) : DependencyHandler<SpdxPackage> {
    // A cache to directly map an [SpdxPackage] to a PURL, if any.
    private val purls = mutableMapOf<SpdxPackage, String?>()

    // Relationships keyed by the SPDX ID of the source element. This map answers the question: "Which relationships
    // originate from the element with the given SPDX ID?"
    private val relationshipsBySource = relationships.groupBy { it.from?.objectUri }

    // Relationships keyed by the SPDX ID of the target element. This map answers the question: "Which relationships
    // refer to the element with the given SPDX ID?"
    private val relationshipsByTarget = mutableMapOf<String, MutableList<Relationship>>()

    init {
        relationships.forEach { rel ->
            rel.tos.forEach { to ->
                relationshipsByTarget.getOrPut(to.objectUri) { mutableListOf() } += rel
            }
        }
    }

    fun getElementsReferredFrom(element: Element, vararg types: RelationshipType): Set<Element> =
        relationshipsBySource[element.objectUri]?.run {
            if (types.isNotEmpty()) filter { it.relationshipType in types } else this
        }?.flatMapTo(mutableSetOf()) { it.tos }.orEmpty()

    fun getElementsReferringTo(element: Element, vararg types: RelationshipType): Set<Element> =
        relationshipsByTarget[element.objectUri]?.run {
            if (types.isNotEmpty()) filter { it.relationshipType in types } else this
        }?.mapNotNullTo(mutableSetOf()) { it.from }.orEmpty()

    fun whatLeadsTo(spdxId: String, path: List<String>): List<String> {
        relationshipsByTarget[spdxId]?.forEach { rel ->
            println(rel.from!!.objectUri + " --- ${rel.relationshipType} --> " + spdxId)
            whatLeadsTo(rel.from!!.objectUri, path + rel.from!!.objectUri)
        }
        return path
    }

    fun whatLeadsTo(spdxId: String): List<String> =
        whatLeadsTo(spdxId, emptyList())

    override fun identifierFor(dependency: SpdxPackage): Identifier {
        val purl = purls.getOrPut(dependency) { dependency.purl }

        // Prefer a PURL as it might contain proper type and namespace information.
        return purl?.toPackageUrl()?.toIdentifier()
            // Yocto does not set a package version for source packages, so try to derive it from the name, and to get
            // a distinguishing name based on the ID.
            ?: dependency.primaryPurpose.getOrNull()?.takeIf { it == SoftwarePurpose.SOURCE }
                ?.let { dependency.getOrtIdFromSpdxId() } ?: Identifier(
                    type = PACKAGE_TYPE_SPDX,
                    namespace = "",
                    name = dependency.name.get(),
                    version = dependency.packageVersion.getOrDefault("")
                )
    }

    override fun dependenciesFor(dependency: SpdxPackage): List<SpdxPackage> =
        getElementsReferredFrom(dependency, RelationshipType.DEPENDS_ON).filterIsInstance<SpdxPackage>() +
            getElementsReferredFrom(dependency, RelationshipType.HAS_OPTIONAL_DEPENDENCY).filterIsInstance<SpdxPackage>() +
            getElementsReferredFrom(dependency, RelationshipType.HAS_PROVIDED_DEPENDENCY).filterIsInstance<SpdxPackage>()

    override fun linkageFor(dependency: SpdxPackage) =
        when {
            getElementsReferringTo(dependency, RelationshipType.HAS_DYNAMIC_LINK).isNotEmpty() -> PackageLinkage.DYNAMIC
            getElementsReferringTo(dependency, RelationshipType.HAS_STATIC_LINK).isNotEmpty() -> PackageLinkage.STATIC
            else -> PackageLinkage.DYNAMIC
        }

    override fun createPackage(dependency: SpdxPackage, issues: MutableCollection<Issue>): Package {
        val id = identifierFor(dependency)
        val purl = purls.getOrPut(dependency) { dependency.purl }

        val declaredLicenses = getElementsReferredFrom(dependency, RelationshipType.HAS_DECLARED_LICENSE)
            .filterIsInstance<LicenseExpression>()
            .mapNotNullTo(mutableSetOf()) { it.licenseExpression }

        val concludedLicenses = getElementsReferredFrom(dependency, RelationshipType.HAS_CONCLUDED_LICENSE)
            .filterIsInstance<LicenseExpression>()
            .mapNotNullTo(mutableSetOf()) { it.licenseExpression }

        val description = dependency.description.getOrNull() ?: dependency.summary.getOrNull()

        val url = dependency.downloadLocation.getOrNull()
        val sourceArtifact = if (!url.isNullOrBlank()) {
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
            purl = purl ?: id.toPurl(),
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
            vcs = VcsInfo.EMPTY
        )
    }
}

private fun SpdxPackage.getOrtIdFromSpdxId(): Identifier? {
    val nameSections = name.get().split('-', '_')
    if (nameSections.size < 2) return null

    val (namePrefix, version) = nameSections.let {
        it.dropLast(1).joinToString("-") to it.last().substringBefore(".tar.")
    }

    if (!version.first().isDigit()) return null

    val nameFromId = id.withoutPrefix("http://spdx.org/spdxdocs/")
        ?.substringBefore('/')
        ?.split('-')
        ?.dropLast(5) // Drop the UUID.
        ?.joinToString("-")

    return Identifier(
        type = PACKAGE_TYPE_SPDX,
        namespace = "",
        name = nameFromId ?: namePrefix,
        version = packageVersion.getOrDefault(version)
    )
}

private val SpdxPackage.purl
    get() = externalIdentifiers.filter {
        it.externalIdentifierType == ExternalIdentifierType.PACKAGE_URL
    }.firstNotNullOfOrNull { it.identifier }

private val SpdxPackage.cpe
    get() = externalIdentifiers.filter {
        it.externalIdentifierType in cpeTypes
    }.firstNotNullOfOrNull { it.identifier }

private val cpeTypes = enumSetOf(ExternalIdentifierType.CPE22, ExternalIdentifierType.CPE23)
