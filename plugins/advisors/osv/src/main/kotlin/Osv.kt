/*
 * Copyright (C) 2022 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.advisors.osv

import com.github.packageurl.PackageURL

import java.lang.invoke.MethodHandles
import java.time.Instant

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

import org.apache.logging.log4j.kotlin.logger
import org.apache.logging.log4j.kotlin.loggerOf

import org.ossreviewtoolkit.clients.osv.Affected
import org.ossreviewtoolkit.clients.osv.Event
import org.ossreviewtoolkit.clients.osv.OsvServiceWrapper
import org.ossreviewtoolkit.clients.osv.Range
import org.ossreviewtoolkit.clients.osv.VulnerabilitiesForPackageRequest
import org.ossreviewtoolkit.clients.osv.Vulnerability
import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorSummary
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.utils.toPackageUrl
import org.ossreviewtoolkit.model.vulnerabilities.VulnerabilityReference
import org.ossreviewtoolkit.plugins.advisors.api.AdviceProvider
import org.ossreviewtoolkit.plugins.advisors.api.AdviceProviderFactory
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.percentEncode
import org.ossreviewtoolkit.utils.common.toUri
import org.ossreviewtoolkit.utils.ort.OkHttpClientHelper

/**
 * An advice provider that obtains vulnerability information from [Open Source Vulnerabilities](https://osv.dev/).
 *
 * For the list of data sources see [here](https://google.github.io/osv.dev/data/#current-data-sources).
 */
@OrtPlugin(
    id = "OSV",
    displayName = "OSV",
    summary = "An advisor that retrieves vulnerability information from the Open Source Vulnerabilities database.",
    factory = AdviceProviderFactory::class
)
class Osv(
    override val descriptor: PluginDescriptor = OsvFactory.descriptor,
    config: OsvConfiguration
) : AdviceProvider {
    override val details = AdvisorDetails(descriptor.id)

    private val service = OsvServiceWrapper(
        serverUrl = config.serverUrl,
        httpClient = OkHttpClientHelper.buildClient()
    )

    override suspend fun retrievePackageFindings(packages: Set<Package>): Map<Package, AdvisorResult> {
        val startTime = Instant.now()

        val vulnerabilityIdsForPackageId = getVulnerabilityIdsForPackages(packages)
        val allVulnerabilityIds = vulnerabilityIdsForPackageId.values.flatten().toSet()
        val vulnerabilityForId = getVulnerabilitiesForIds(allVulnerabilityIds).associateBy { it.id }

        return packages.mapNotNull { pkg ->
            vulnerabilityIdsForPackageId[pkg.id]?.let { ids ->
                pkg to AdvisorResult(
                    advisor = details,
                    summary = AdvisorSummary(startTime = startTime, endTime = Instant.now()),
                    vulnerabilities = ids.map { vulnerabilityForId.getValue(it).toOrtVulnerability(pkg.purl) }
                )
            }
        }.toMap()
    }

    private fun getVulnerabilityIdsForPackages(packages: Set<Package>): Map<Identifier, List<String>> {
        val requests = packages.mapNotNull { pkg ->
            createRequest(pkg)?.let { pkg to it }
        }

        val result = service.getVulnerabilityIdsForPackages(requests.map { it.second })
        val results = mutableListOf<Pair<Identifier, List<String>>>()

        result.map { allVulnerabilities ->
            // OSV returns vulnerability results in the same order as packages were requested, so use the list index to
            // identify to which package a result belongs. This means that also empty results are returned as otherwise
            // list indices would not match, so filter these out.
            allVulnerabilities.mapIndexedNotNullTo(results) { i, pkgVulnerabilities ->
                pkgVulnerabilities.takeUnless { it.isEmpty() }?.let { requests[i].first.id to it }
            }
        }.onFailure {
            logger.error {
                "Requesting vulnerability IDs for packages failed: ${it.collectMessages()}"
            }
        }

        return results.toMap()
    }

    private fun getVulnerabilitiesForIds(ids: Set<String>): List<Vulnerability> {
        val result = service.getVulnerabilitiesForIds(ids)

        return result.getOrElse {
            logger.error {
                "Requesting vulnerabilities for IDs failed: ${it.collectMessages()}"
            }

            emptyList()
        }
    }
}

private fun Vulnerability.toOrtVulnerability(purl: String): org.ossreviewtoolkit.model.vulnerabilities.Vulnerability {
    // The ORT and OSV vulnerability data models are different in that ORT uses a severity for each reference (assuming
    // that different references could use different severities), whereas OSV manages severities and references on the
    // same level, which means it is not possible to identify whether a reference belongs to a specific severity.
    // To map between these different model, simply use the "cartesian product" to create an ORT reference for each
    // combination of an OSV severity and reference.
    val ortReferences = mutableListOf<VulnerabilityReference>()

    severity.map {
        it.type.name to it.score
    }.ifEmpty {
        listOf(null to null)
    }.forEach { (scoringSystem, vector) ->
        references.mapNotNullTo(ortReferences) { reference ->
            val url = reference.url.trim().let { if (it.startsWith("://")) "https$it" else it }

            url.toUri().onFailure {
                logger.debug { "Could not parse reference URL for vulnerability '$id': ${it.collectMessages()}." }
            }.map {
                // Use the 'severity' property of the unspecified 'databaseSpecific' object.
                // See also https://github.com/google/osv.dev/issues/484.
                val specificSeverity = databaseSpecific?.get("severity")
                val severityRating = (specificSeverity as? JsonPrimitive)?.contentOrNull

                // OSV never provides the numeric base score as it can be calculated from the vector string.
                VulnerabilityReference(it, scoringSystem, severityRating, null, vector)
            }.getOrNull()
        }
    }

    return org.ossreviewtoolkit.model.vulnerabilities.Vulnerability(
        id = id,
        summary = summary,
        description = details,
        references = ortReferences,
        firstFixedVersions = purl.toPackageUrl()?.let { affected.toFirstFixedVersions(it) }.orEmpty()
    )
}

/**
 * Extract the first fixed versions from the given [Affected] items that match the given [purl].
 */
private fun Collection<Affected>.toFirstFixedVersions(purl: PackageURL): Set<String> {
    val namespaceNameList = listOfNotNull(purl.namespace, purl.name)

    // In the case of GitHub purls, the package object is null, and the range object contains a repository URL that can
    // be used to identify the package.
    if (purl.type == "github") {
        return flatMapTo(mutableSetOf()) { affected ->
            affected.ranges.filter { range ->
                val encodedNamespaceName = namespaceNameList.joinToString("/") { it.percentEncode() }
                range.repo?.contains(encodedNamespaceName) ?: false
            }.flatMap { it.getFixedVersions() }
        }
    }

    // For other purl types, the package object is used to identify the package. The package name is a required field in
    // the package object, so it is used to check for a match. The name depends on the ecosystem, and if the ecosystem
    // supports namespaces, the namespace is prepended to the name. The separator between the namespace and the name
    // also depends on the ecosystem. See https://ossf.github.io/osv-schema/#affectedpackage-field for details.
    return filter { affected ->
        val separator = if (purl.type == "maven") ":" else "/"
        affected.pkg?.name == namespaceNameList.joinToString(separator) { it }
    }.flatMapTo(mutableSetOf()) { affected ->
        affected.ranges.flatMap { it.getFixedVersions() }
    }
}

private fun Range.getFixedVersions(): List<String> =
    events.mapNotNull { event -> event.takeIf { it.type == Event.Type.FIXED }?.value }

private val logger = loggerOf(MethodHandles.lookup().lookupClass())

private fun createRequest(pkg: Package): VulnerabilitiesForPackageRequest? {
    val purl = pkg.purl.toPackageUrl()

    return when {
        purl != null -> createRequestForPurl(purl)

        // TODO: Consider doing this in more cases, like for the upcoming generic "git" type of PURLs, see
        //       https://github.com/package-url/purl-spec/issues/780.
        pkg.vcsProcessed.revision.isNotEmpty() -> VulnerabilitiesForPackageRequest(
            commit = pkg.vcsProcessed.revision
        )

        else -> {
            logger.warn {
                "${pkg.id.toCoordinates()} does not provide any metadata to identify vulnerabilities."
            }

            null
        }
    }
}

private fun createRequestForPurl(purl: PackageURL): VulnerabilitiesForPackageRequest =
    if (purl.type == "github") {
        // Work around OSV not yet being able to match against GitHub purls.
        val pkg = org.ossreviewtoolkit.clients.osv.Package(
            ecosystem = "GIT", // See https://google.github.io/osv.dev/post-v1-query/#queries-for-git-records.
            name = "https://github.com/${purl.namespace}/${purl.name}.git"
        )

        when {
            // The version may be missing for packages without a resolved version. In that case, fall back to a
            // package-only query, which lets OSV return all vulnerabilities for the package regardless of the version.
            purl.version.isNullOrEmpty() -> VulnerabilitiesForPackageRequest(pkg = pkg)

            purl.version.isSha1() -> VulnerabilitiesForPackageRequest(pkg = pkg, commit = purl.version)

            else -> VulnerabilitiesForPackageRequest(pkg = pkg, version = purl.version)
        }
    } else {
        VulnerabilitiesForPackageRequest(pkg = org.ossreviewtoolkit.clients.osv.Package(purl = purl.toString()))
    }

private val SHA1_REGEX = Regex("^[0-9a-fA-F]{40}$")

private fun String.isSha1(): Boolean = SHA1_REGEX.matches(this)
