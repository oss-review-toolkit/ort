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

package org.ossreviewtoolkit.utils.cyclonedx

import org.cyclonedx.model.Component
import org.cyclonedx.model.ExternalReference

import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.utils.PurlType
import org.ossreviewtoolkit.model.utils.parsePurl
import org.ossreviewtoolkit.model.utils.toOrtType
import org.ossreviewtoolkit.utils.ort.DeclaredLicenseProcessor
import org.ossreviewtoolkit.utils.ort.ProcessedDeclaredLicense
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl
import org.ossreviewtoolkit.utils.spdx.toSpdx

internal const val DEFAULT_ORT_TYPE = "CycloneDX"

/**
 * Convert this CycloneDX [Component] to an ORT [Identifier].
 *
 * The identifier type is derived from the PURL if available, otherwise [defaultType] is used.
 * Namespace, name, and version are extracted from the PURL if present, falling back to the
 * component's group, name, and version properties.
 */
fun Component.toIdentifier(defaultType: String = DEFAULT_ORT_TYPE): Identifier {
    val parsedPurl = parsePurl(purl)

    // Only use PURL type if we have a PURL; otherwise use the default type.
    val type = parsedPurl?.let {
        val purlType = it.getPurlType() ?: PurlType.GENERIC
        purlType.toOrtType()
    } ?: defaultType

    return Identifier(
        type = type,
        namespace = parsedPurl?.namespace?.takeIf { it.isNotBlank() } ?: group.orEmpty(),
        name = parsedPurl?.name?.takeIf { it.isNotBlank() } ?: name,
        version = parsedPurl?.version?.takeIf { it.isNotBlank() } ?: version.orEmpty()
    )
}

/**
 * Convert this CycloneDX [Component] to an ORT [Project].
 *
 * This creates a Project with minimal parameters derived from the component. Call sites can use
 * `.copy()` to override `vcsProcessed`, `scopeNames`, or other properties as needed.
 *
 * The [definitionFilePath] is the path to the definition file relative to the analysis root.
 * The [defaultType] is the identifier type to use if no PURL is present.
 */
fun Component.toProject(definitionFilePath: String, defaultType: String = DEFAULT_ORT_TYPE): Project {
    val vcsUrl = findExternalReferenceUrl(ExternalReference.Type.VCS)
    val vcs = if (vcsUrl.isNotBlank()) VcsHost.parseUrl(vcsUrl) else VcsInfo.EMPTY
    val (declaredLicenses, declaredLicensesProcessed) = extractLicenseInfo()

    return Project(
        id = toIdentifier(defaultType),
        definitionFilePath = definitionFilePath,
        authors = extractAuthors(),
        declaredLicenses = declaredLicenses,
        declaredLicensesProcessed = declaredLicensesProcessed,
        vcs = vcs,
        homepageUrl = findExternalReferenceUrl(ExternalReference.Type.WEBSITE),
        description = description.orEmpty()
    )
}

/**
 * Convert this CycloneDX [Component] to an ORT [Package].
 *
 * The [defaultType] is the identifier type to use if no PURL is present.
 */
fun Component.toPackage(defaultType: String = DEFAULT_ORT_TYPE): Package {
    val (declaredLicenses, declaredLicensesProcessed) = extractLicenseInfo()

    return Package(
        id = toIdentifier(defaultType),
        purl = purl.orEmpty(),
        declaredLicenses = declaredLicenses,
        declaredLicensesProcessed = declaredLicensesProcessed,
        authors = extractAuthors(),
        description = description.orEmpty(),
        homepageUrl = findExternalReferenceUrl(ExternalReference.Type.WEBSITE),
        binaryArtifact = RemoteArtifact.EMPTY,
        sourceArtifact = extractSourceArtifact(),
        vcs = extractVcs()
    )
}

/**
 * Extract declared licenses and processed license info from the component.
 *
 * When an SPDX expression is present, the individual license IDs are extracted for `declaredLicenses`,
 * while the original expression is preserved in [ProcessedDeclaredLicense.spdxExpression].
 *
 * Returns a pair of (declaredLicenses, declaredLicensesProcessed).
 */
internal fun Component.extractLicenseInfo(): Pair<Set<String>, ProcessedDeclaredLicense> {
    licenses?.expression?.value?.takeIf { it.isNotBlank() }?.let { expression ->
        val spdxExpression = runCatching { expression.toSpdx() }.getOrNull()

        return if (spdxExpression != null) {
            val individualLicenses = spdxExpression.licenses().toSet()
            individualLicenses to ProcessedDeclaredLicense(spdxExpression)
        } else {
            // If parsing fails, treat the expression as a single unknown license.
            val licenses = setOf(expression)
            licenses to DeclaredLicenseProcessor.process(licenses)
        }
    }

    val individualLicenses = licenses?.licenses
        ?.mapNotNullTo(mutableSetOf()) { it.id ?: it.name }
        .orEmpty()

    return individualLicenses to DeclaredLicenseProcessor.process(individualLicenses)
}

/**
 * Extract declared licenses from the component.
 * Supports both individual license entries and SPDX expressions.
 */
internal fun Component.extractLicenses(): Set<String> = extractLicenseInfo().first

/**
 * Extract authors from the component.
 * Preference order: authors[] -> manufacturer -> legacy author field.
 */
internal fun Component.extractAuthors(): Set<String> {
    val authorsFromArray = authors.orEmpty()
        .mapNotNull { it.name ?: it.email }
        .filter { it.isNotBlank() }
        .toSet()

    if (authorsFromArray.isNotEmpty()) return authorsFromArray

    return setOfNotNull(manufacturer?.name?.takeIf { it.isNotBlank() })
}

/**
 * Extract VCS information from the component's external references.
 */
internal fun Component.extractVcs(): VcsInfo {
    val vcsUrl = findExternalReferenceUrl(
        type = ExternalReference.Type.VCS,
        preferVcsHostParseable = true
    )

    if (vcsUrl.isBlank()) return VcsInfo.EMPTY

    // Split on '#' to separate URL from fragment. The fragment in VCS URLs (e.g., from PURL conventions)
    // represents a subpath, not a revision, so parse only the base URL and use the fragment as path.
    val baseUrl = vcsUrl.substringBefore('#')
    val fragment = vcsUrl.substringAfter('#', "")

    val vcs = VcsHost.parseUrl(normalizeVcsUrl(baseUrl))

    return if (fragment.isNotBlank()) vcs.copy(path = fragment) else vcs
}

/**
 * Extract source artifact using ExternalReference.Type.DISTRIBUTION.
 */
internal fun Component.extractSourceArtifact(): RemoteArtifact {
    val ref = externalReferences.orEmpty()
        .find { it.type == ExternalReference.Type.DISTRIBUTION }
        ?: return RemoteArtifact.EMPTY

    val url = ref.url?.takeIf { it.isNotBlank() } ?: return RemoteArtifact.EMPTY

    val hashes = ref.hashes
        ?.mapNotNull { cdxHash ->
            val algorithm = mapHashAlgorithm(cdxHash.algorithm)
            if (algorithm != HashAlgorithm.NONE && algorithm != HashAlgorithm.UNKNOWN) {
                Hash(cdxHash.value, algorithm)
            } else {
                null
            }
        }

    // Use size as a heuristic to pick the strongest hash (larger size = lower collision likelihood).
    val hash = hashes?.maxByOrNull { it.algorithm.size } ?: Hash.NONE

    return RemoteArtifact(url = url, hash = hash)
}

/**
 * Find a stable external reference URL.
 *
 * - Uses only non-blank URLs
 * - For VCS, can prefer URLs parseable by [VcsHost]
 */
internal fun Component.findExternalReferenceUrl(
    type: ExternalReference.Type,
    preferVcsHostParseable: Boolean = false
): String {
    val urls = externalReferences.orEmpty()
        .filter { it.type == type }
        .mapNotNull { it.url?.takeIf(String::isNotBlank) }

    if (urls.isEmpty()) return ""

    if (preferVcsHostParseable) {
        urls.find { VcsHost.parseUrl(it) != VcsInfo.EMPTY }?.also { return it }
    }

    return urls.first()
}

private fun mapHashAlgorithm(cdxAlgorithm: String?): HashAlgorithm =
    if (cdxAlgorithm.isNullOrBlank()) {
        HashAlgorithm.NONE
    } else {
        HashAlgorithm.fromString(cdxAlgorithm)
    }
