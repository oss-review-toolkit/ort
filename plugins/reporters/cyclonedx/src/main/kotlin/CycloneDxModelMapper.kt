/*
 * Copyright (C) 2019 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import java.util.Date
import java.util.SortedSet
import java.util.UUID

import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.filter
import kotlin.collections.forEach
import kotlin.text.startsWith

import org.apache.logging.log4j.kotlin.logger

import org.cyclonedx.model.AttachmentText
import org.cyclonedx.model.Bom
import org.cyclonedx.model.Component
import org.cyclonedx.model.Dependency
import org.cyclonedx.model.ExternalReference
import org.cyclonedx.model.Hash
import org.cyclonedx.model.License
import org.cyclonedx.model.LicenseChoice
import org.cyclonedx.model.Metadata
import org.cyclonedx.model.OrganizationalContact
import org.cyclonedx.model.OrganizationalEntity
import org.cyclonedx.model.Property
import org.cyclonedx.model.license.Expression
import org.cyclonedx.model.metadata.ToolInformation
import org.cyclonedx.model.vulnerability.Vulnerability.Rating.Method

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseSource
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.licenses.ResolvedLicenseInfo
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.model.vulnerabilities.Cvss2Rating
import org.ossreviewtoolkit.model.vulnerabilities.Cvss3Rating
import org.ossreviewtoolkit.model.vulnerabilities.Cvss4Rating
import org.ossreviewtoolkit.model.vulnerabilities.Vulnerability
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.ort.ORT_FULL_NAME
import org.ossreviewtoolkit.utils.ort.ORT_NAME
import org.ossreviewtoolkit.utils.ort.ORT_VERSION
import org.ossreviewtoolkit.utils.spdx.SpdxLicense

internal class CycloneDxModelMapper(
    private val input: ReporterInput,
    private val config: CycloneDxReporterConfig
) {
    fun createSingleBom(): Bom =
        Bom().apply {
            val projects = input.ortResult.getProjects(omitExcluded = true).sortedBy { it.id }

            serialNumber = "urn:uuid:${UUID.randomUUID()}"

            metadata = createBomMetadata().apply {
                component = getSingleBomMetadataComponent(projects, input.ortResult)
            }

            components = mutableListOf()

            // In case of multiple projects it is not always clear for which project to create the BOM:
            //
            // - If a multi-module project only produces a single application that gets distributed, then usually only a
            //   single BOM for that application is generated.
            // - If a multi-module project produces multiple applications (e.g. if there is one module per independent
            //   microservice), then usually for each project a BOM is generated as there are multiple things being
            //   distributed.
            //
            // As this distinction is hard to make programmatically (without additional information about the
            // distributable), just create a single BOM for all projects in that case for now. As there also is no
            // single correct project to pick for adding external references in that case, simply only use the global
            // repository VCS information here.
            val vcs = input.ortResult.repository.vcsProcessed
            addExternalReference(
                ExternalReference.Type.VCS,
                vcs.url,
                "URL to the ${vcs.type} repository of the projects"
            )

            val allDirectDependencies = projects.flatMapTo(mutableSetOf()) { project ->
                input.ortResult.dependencyNavigator.projectDependencies(project, maxDepth = 1)
            }

            input.ortResult.getPackages(omitExcluded = true)
                .map { it.metadata }
                .sortedBy { it.id }
                .forEach { pkg ->
                    val dependencyType = if (pkg.id in allDirectDependencies) "direct" else "transitive"
                    addComponent(input, pkg, dependencyType)
                }

            addDependencies(input, metadata.component.bomRef, allDirectDependencies)

            addVulnerabilities(input.ortResult.getVulnerabilities())
        }

    fun createProjectBom(project: Project): Bom =
        Bom().apply {
            serialNumber = "urn:uuid:${UUID.randomUUID()}"

            metadata = createBomMetadata().apply {
                component = Component().apply {
                    // Actually the project could be a library as well, but there is no automatic way to tell.
                    type = Component.Type.APPLICATION

                    bomRef = project.id.toCoordinates()

                    group = project.id.namespace
                    name = project.id.name
                    version = project.id.version

                    authors = project.authors.map { OrganizationalContact().apply { name = it } }
                    supplier = authors.takeUnless { it.isEmpty() }?.let {
                        OrganizationalEntity().apply { contacts = authors }
                    }

                    description = project.description
                }
            }

            components = mutableListOf()

            // Add information about projects as external references at the BOM level.
            addExternalReference(
                ExternalReference.Type.VCS,
                project.vcsProcessed.url,
                "URL to the project's ${project.vcsProcessed.type} repository"
            )

            addExternalReference(ExternalReference.Type.WEBSITE, project.homepageUrl)

            addExternalReference(ExternalReference.Type.BUILD_SYSTEM, project.id.type)

            addExternalReference(
                ExternalReference.Type.OTHER,
                project.id.toPurl(),
                "Package-URL of the project"
            )

            val dependencyPackages = input.ortResult.dependencyNavigator
                .projectDependencies(project, matcher = { !input.ortResult.isExcluded(it.id) })
                .mapNotNull { input.ortResult.getPackage(it)?.metadata }
                .sortedBy { it.id }

            val directDependencies = input.ortResult.dependencyNavigator.projectDependencies(project, maxDepth = 1)
            dependencyPackages.forEach { pkg ->
                val dependencyType = if (pkg.id in directDependencies) "direct" else "transitive"
                addComponent(input, pkg, dependencyType)
            }

            addDependencies(input, metadata.component.bomRef, directDependencies)

            addVulnerabilities(input.ortResult.getVulnerabilities())
        }

    private fun createBomMetadata(): Metadata =
        Metadata().apply {
            timestamp = Date()
            toolChoice = ToolInformation().apply {
                components = listOf(
                    Component().apply {
                        type = Component.Type.APPLICATION
                        name = ORT_FULL_NAME
                        version = ORT_VERSION
                    }
                )
            }

            licenses = LicenseChoice().apply { expression = Expression(config.dataLicense) }
        }

    /**
     * Return a [Component] which represents the union of the given [projects] as a single component. The component is
     * derived by a heuristic which works best for multi-module projects in which the modules share common properties.
     *
     * If these default values are not suitable, it is possible to override some of them via the reporter [config].
     */
    private fun getSingleBomMetadataComponent(projects: Collection<Project>, result: OrtResult): Component =
        Component().apply {
            type = config.singleBomComponentType

            val namespaces = projects.mapTo(mutableSetOf()) { it.id.namespace }
            val versions = projects.mapTo(mutableSetOf()) { it.id.version }

            with(result.repository.vcsProcessed) {
                bomRef = "$url@$revision"

                group = namespaces.singleOrNull()
                name = config.singleBomComponentName.takeUnless { it.isEmpty() }
                    ?: findTopLevelProject(projects)?.id?.name ?: url
                version = versions.singleOrNull() ?: revision
            }
        }
}

/**
 * Try to obtain a single top-level project in [projects]. The top-level project is identified based on the
 * number of components in the definition file path. If there are multiple projects with a minimum number of
 * path components, there is no single top-level project, and the function returns *null*.
 */
private fun findTopLevelProject(projects: Collection<Project>): Project? =
    projects.groupBy { project ->
        project.definitionFilePath.count { it == '/' }
    }.minByOrNull { it.key }?.value?.singleOrNull()

/**
 * Enrich this [Bom] with information about the hierarchy of dependencies, starting with the [parentRef] and its direct
 * dependencies given as ORT [ids]. The [visited] set internally tracks the already visited parents to break cycles.
 */
private fun Bom.addDependencies(
    input: ReporterInput,
    parentRef: String,
    ids: Set<Identifier>,
    visited: MutableSet<String> = mutableSetOf()
) {
    // Return early if dependencies for this parent have already been recorded. Note that this disregards the case where
    // dependencies of a parent differ depending e.g. on the project scope due to version conflict resolution. That is
    // because CycloneDX itself only records dependency information globally at the BOM level, and does not support
    // different dependencies for a given parent depending on context.
    if (parentRef in visited) return
    visited += parentRef

    val dependency = Dependency(parentRef).apply {
        dependencies = ids.map { id -> Dependency(id.toCoordinates()) }
    }

    if (dependency.dependencies.isNotEmpty()) addDependency(dependency)

    ids.forEach { id ->
        val directDependencies = input.ortResult.getDependencies(id, maxLevel = 1, omitExcluded = true)
        addDependencies(input, id.toCoordinates(), directDependencies, visited)
    }
}

/**
 * Add a [ExternalReference] of the given [type] to this [Bom] which points to [url] and has an optional [comment].
 */
private fun Bom.addExternalReference(type: ExternalReference.Type, url: String, comment: String? = null) {
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
 * Add the given [ORT package][pkg] to this [Bom] by converting it to a CycloneDX [Component] using the metadata from
 * [input]. The [dependencyType] is added as a [Property] to indicate "direct" vs "transitive" dependencies.
 */
private fun Bom.addComponent(input: ReporterInput, pkg: Package, dependencyType: String) {
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
        // See https://github.com/CycloneDX/specification/issues/17 for how this differs from FRAMEWORK.
        type = Component.Type.LIBRARY

        bomRef = pkg.id.toCoordinates()

        group = pkg.id.namespace
        name = pkg.id.name
        version = pkg.id.version

        authors = pkg.authors.map { OrganizationalContact().apply { name = it } }
        supplier = authors.takeUnless { it.isEmpty() }?.let {
            OrganizationalEntity().apply { contacts = authors }
        }

        description = pkg.description

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

        addProperty(Property("$ORT_NAME:dependencyType", dependencyType))
    }

    component.addExternalReference(ExternalReference.Type.WEBSITE, pkg.homepageUrl)

    addComponent(component)
}

/**
 * Add the [advisorVulnerabilities] to this [Bom] by converting them from [ORT vulnerability objects][Vulnerability] to
 * [CycloneDX vulnerability objects][org.cyclonedx.model.vulnerability.Vulnerability].
 */
private fun Bom.addVulnerabilities(advisorVulnerabilities: Map<Identifier, List<Vulnerability>>) {
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

                    // Skip EPSS scores whose serialized value causes problems with validation, see
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
 * Map each name of a license in the collection to a license object with the appropriate license text.
 */
private fun Collection<String>.mapNamesToLicenses(origin: String, input: ReporterInput): List<License> =
    map { licenseName ->
        val spdxId = SpdxLicense.forId(licenseName)?.id
        val licenseText = input.licenseFactProvider.getLicenseText(licenseName)?.text

        // Prefer to set the id in case of an SPDX "core" license and only use the name as a fallback, also
        // see https://github.com/CycloneDX/cyclonedx-core-java/issues/8.
        License().apply {
            id = spdxId
            name = licenseName.takeIf { spdxId == null }

            addProperty(Property("$ORT_NAME:origin", origin))

            if (licenseText != null) {
                setLicenseText(
                    AttachmentText().apply {
                        contentType = "plain/text"
                        text = licenseText
                    }
                )
            }
        }
    }

/**
 * Return the license names of all licenses that have any of the given [sources] disregarding the excluded state.
 */
private fun ResolvedLicenseInfo.getLicenseNames(vararg sources: LicenseSource): SortedSet<String> =
    licenses.filter { license -> sources.any { it in license.sources } }.mapTo(sortedSetOf()) { it.license.toString() }

/**
 * Map an ORT hash object to a CycloneDX hash object.
 */
private fun org.ossreviewtoolkit.model.Hash.toCycloneDx(): Hash? =
    Hash.Algorithm.entries.find { it.spec == algorithm.toString() }?.let { Hash(it, value) }

/**
 * Add a [ExternalReference] of the given [type] to this [Component] which points to [url] and has an optional
 * [comment].
 */
private fun Component.addExternalReference(type: ExternalReference.Type, url: String, comment: String? = null) {
    if (url.isBlank()) return

    addExternalReference(
        ExternalReference().also { ref ->
            ref.type = type
            ref.url = url
            ref.comment = comment?.takeUnless { it.isBlank() }
        }
    )
}
