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

package org.ossreviewtoolkit.plugins.reporters.cyclonedx

import java.io.File

import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.text.startsWith

import org.apache.logging.log4j.kotlin.logger

import org.cyclonedx.Format
import org.cyclonedx.Version
import org.cyclonedx.generators.BomGeneratorFactory
import org.cyclonedx.model.Bom
import org.cyclonedx.model.Component
import org.cyclonedx.model.ExtensibleType
import org.cyclonedx.model.ExternalReference
import org.cyclonedx.model.LicenseChoice
import org.cyclonedx.model.vulnerability.Vulnerability.Rating.Method

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseSource
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.vulnerabilities.Cvss2Rating
import org.ossreviewtoolkit.model.vulnerabilities.Cvss3Rating
import org.ossreviewtoolkit.model.vulnerabilities.Cvss4Rating
import org.ossreviewtoolkit.model.vulnerabilities.Vulnerability
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.ort.ORT_NAME

/**
 * Add a [ExternalReference] of the given [type] to this [Bom] which points to [url] and has an optional [comment].
 */
internal fun Bom.addExternalReference(type: ExternalReference.Type, url: String, comment: String? = null) {
    if (url.isBlank()) return

    addExternalReference(
        ExternalReference().also { ref ->
            ref.type = type
            ref.url = url
            ref.comment = comment?.takeUnless { it.isBlank() }
        }
    )
}

/**
 * Add the given [ORT package][pkg] to this [Bom] by converting it to a CycloneDX [Component] using the metdata from
 * [input]. The [dependencyType] is added as an [ExtensibleType] to indicate "direct" vs "transitive" dependencies.
 */
internal fun Bom.addComponent(input: ReporterInput, pkg: Package, dependencyType: String) {
    val resolvedLicenseInfo = input.licenseInfoResolver.resolveLicenseInfo(pkg.id).filterExcluded()
        .applyChoices(input.ortResult.getPackageLicenseChoices(pkg.id))
        .applyChoices(input.ortResult.getRepositoryLicenseChoices())

    val concludedLicenseNames = resolvedLicenseInfo.getLicenseNames(LicenseSource.CONCLUDED)
    val declaredLicenseNames = resolvedLicenseInfo.getLicenseNames(LicenseSource.DECLARED)
    val detectedLicenseNames = resolvedLicenseInfo.getLicenseNames(LicenseSource.DETECTED)

    // Get all licenses, but note down their origins inside an extensible type.
    val licenseObjects = concludedLicenseNames.mapNamesToLicenses("concluded license", input) +
        declaredLicenseNames.mapNamesToLicenses("declared license", input) +
        detectedLicenseNames.mapNamesToLicenses("detected license", input)

    val binaryHash = pkg.binaryArtifact.hash.toCycloneDx()
    val sourceHash = pkg.sourceArtifact.hash.toCycloneDx()

    val (hash, purlQualifier) = if (binaryHash == null && sourceHash != null) {
        Pair(sourceHash, "?classifier=sources")
    } else {
        Pair(binaryHash, "")
    }

    val component = Component().apply {
        group = pkg.id.namespace
        name = pkg.id.name
        version = pkg.id.version
        description = pkg.description
        bomRef = pkg.id.toCoordinates()

        // TODO: Map package-manager-specific OPTIONAL scopes.
        scope = if (input.ortResult.isExcluded(pkg.id)) {
            Component.Scope.EXCLUDED
        } else {
            Component.Scope.REQUIRED
        }

        hashes = listOfNotNull(hash)

        if (licenseObjects.isNotEmpty()) licenses = LicenseChoice().apply { licenses = licenseObjects }

        // TODO: Find a way to associate copyrights to the license they belong to, see
        //       https://github.com/CycloneDX/cyclonedx-core-java/issues/58
        copyright = resolvedLicenseInfo.getCopyrights().joinToString {
            it.filterNot { character ->
                character.isIdentifierIgnorable()
            }
        }.takeUnless { it.isEmpty() }

        purl = pkg.purl + purlQualifier
        isModified = pkg.isModified

        // See https://github.com/CycloneDX/specification/issues/17 for how this differs from FRAMEWORK.
        type = Component.Type.LIBRARY

        extensibleTypes = listOf(ExtensibleType(ORT_NAME, "dependencyType", dependencyType))
    }

    component.addExternalReference(ExternalReference.Type.WEBSITE, pkg.homepageUrl)

    addComponent(component)
}

/**
 * Add the [advisorVulnerabilities] to this [Bom] by converting them from [ORT vulnerability objects][Vulnerability] to
 * [CycloneDX vulnerability objects][org.cyclonedx.model.vulnerability.Vulnerability].
 */
internal fun Bom.addVulnerabilities(advisorVulnerabilities: Map<Identifier, List<Vulnerability>>) {
    val allVulnerabilities = mutableListOf<org.cyclonedx.model.vulnerability.Vulnerability>()

    advisorVulnerabilities.forEach { (id, vulnerabilities) ->
        vulnerabilities.forEach { ortVulnerability ->
            val cdxVulnerability = org.cyclonedx.model.vulnerability.Vulnerability().apply {
                this.id = ortVulnerability.id
                description = ortVulnerability.description
                detail = ortVulnerability.summary
                ratings = ortVulnerability.references.mapNotNull { reference ->
                    val score = reference.score?.toDouble()
                    val system = reference.scoringSystem?.uppercase()

                    val method = when {
                        system == null -> null
                        Cvss2Rating.PREFIXES.any { system.startsWith(it) } -> Method.CVSSV2
                        Cvss3Rating.PREFIXES.any { system.startsWith(it) } -> Method.CVSSV3
                        Cvss4Rating.PREFIXES.any { system.startsWith(it) } -> Method.CVSSV4
                        else -> Method.fromString(reference.scoringSystem) ?: Method.OTHER
                    }

                    // Skip scores whose serialized value causes problems with validation, see
                    // https://github.com/oss-review-toolkit/ort/issues/9556.
                    if (method == Method.OTHER && "E" in "$score") {
                        logger.warn {
                            "Skipping score $score from ${reference.url} as it would cause problems with document" +
                                " validation."
                        }

                        return@mapNotNull null
                    }

                    org.cyclonedx.model.vulnerability.Vulnerability.Rating().apply {
                        source = org.cyclonedx.model.vulnerability.Vulnerability.Source()
                            .apply { url = reference.url.toString() }
                        this.score = score
                        severity = org.cyclonedx.model.vulnerability.Vulnerability.Rating.Severity
                            .fromString(reference.severity?.lowercase())
                        this.method = method
                        vector = reference.vector
                    }
                }

                affects = mutableListOf(
                    org.cyclonedx.model.vulnerability.Vulnerability.Affect()
                        .apply { ref = id.toCoordinates() }
                )
            }

            allVulnerabilities.add(cdxVulnerability)
        }

        this.vulnerabilities = allVulnerabilities
    }
}

/**
 * Return the string representation for this [Bom], [schemaVersion] and [format].
 */
internal fun Bom.createFormat(schemaVersion: Version, format: Format): String =
    when (format) {
        Format.XML -> BomGeneratorFactory.createXml(schemaVersion, this).toXmlString()
        Format.JSON -> {
            // JSON output cannot handle extensible types (see [1]), so simply remove them. As JSON output is guaranteed
            // to be the last format serialized, it is okay to modify the BOM here without doing a deep copy first.
            //
            // [1] https://github.com/CycloneDX/cyclonedx-core-java/issues/99.
            components.forEach { component ->
                // Clear the "dependencyType".
                component.extensibleTypes = null

                if (component.licenses?.licenses != null) {
                    component.licenses.licenses.forEach { license ->
                        // Clear the "origin".
                        license.extensibleTypes = null
                    }

                    // Remove duplicates that may occur due to clearing the distinguishing extensive type.
                    component.licenses.licenses = component.licenses.licenses.distinct()
                }
            }

            BomGeneratorFactory.createJson(schemaVersion, this).toJsonString()
        }
    }

/**
 * Write this [Bom] at the given [schemaVersion] in all [outputFormats] to the [outputDir] with [outputName] as
 * prefixes.
 */
internal fun Bom.writeFormats(
    schemaVersion: Version,
    outputDir: File,
    outputName: String,
    outputFormats: Set<Format>
): List<Result<File>> =
    outputFormats.map { format ->
        runCatching {
            val bomString = createFormat(schemaVersion, format)

            outputDir.resolve("$outputName.${format.extension}").apply {
                bufferedWriter().use { it.write(bomString) }
            }
        }
    }

/**
 * Add a [ExternalReference] of the given [type] to this [Component] which points to [url] and has an optional
 * [comment].
 */
internal fun Component.addExternalReference(type: ExternalReference.Type, url: String, comment: String? = null) {
    if (url.isBlank()) return

    addExternalReference(
        ExternalReference().also { ref ->
            ref.type = type
            ref.url = url
            ref.comment = comment?.takeUnless { it.isBlank() }
        }
    )
}
