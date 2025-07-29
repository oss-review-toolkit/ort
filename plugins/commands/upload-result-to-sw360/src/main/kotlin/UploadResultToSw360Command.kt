/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.commands.uploadresulttosw360

import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import org.apache.logging.log4j.kotlin.logger

import org.eclipse.sw360.clients.adapter.AttachmentUploadRequest
import org.eclipse.sw360.clients.adapter.SW360ProjectClientAdapter
import org.eclipse.sw360.clients.adapter.SW360ReleaseClientAdapter
import org.eclipse.sw360.clients.rest.resource.SW360Visibility
import org.eclipse.sw360.clients.rest.resource.attachments.SW360AttachmentType
import org.eclipse.sw360.clients.rest.resource.projects.SW360Project
import org.eclipse.sw360.clients.rest.resource.releases.SW360Release
import org.eclipse.sw360.clients.utils.SW360ClientException

import org.ossreviewtoolkit.downloader.Downloader
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.config.Sw360StorageConfiguration
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.commands.api.OrtCommand
import org.ossreviewtoolkit.plugins.commands.api.OrtCommandFactory
import org.ossreviewtoolkit.plugins.commands.api.utils.inputGroup
import org.ossreviewtoolkit.plugins.commands.api.utils.readOrtResult
import org.ossreviewtoolkit.scanner.storages.Sw360Storage
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.common.packZip
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.ort.createOrtTempDir

@OrtPlugin(
    displayName = "Upload Result to SW360",
    description = "Upload an ORT result to SW360.",
    factory = OrtCommandFactory::class
)
class UploadResultToSw360Command(
    descriptor: PluginDescriptor = UploadResultToSw360CommandFactory.descriptor
) : OrtCommand(descriptor) {
    private val ortFile by option(
        "--ort-file", "-i",
        help = "The ORT result file to read as input."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()
        .inputGroup()

    private val attachSources by option(
        "--attach-sources", "-a",
        help = "Download sources of packages and upload them as attachments to SW360 releases."
    ).flag()

    override fun run() {
        val ortResult = readOrtResult(ortFile)

        val sw360Config = ortConfig.scanner.storages?.values
            ?.filterIsInstance<Sw360StorageConfiguration>()?.singleOrNull()

        requireNotNull(sw360Config) {
            "No SW360 storage is configured for the scanner."
        }

        val sw360Connection = Sw360Storage.createConnection(sw360Config)
        val sw360ReleaseClient = sw360Connection.releaseAdapter
        val sw360ProjectClient = sw360Connection.projectAdapter
        val downloader = Downloader(ortConfig.downloader)

        getProjectWithPackages(ortResult).forEach { (project, pkgList) ->
            val linkedReleases = pkgList.mapNotNull { pkg ->
                val name = createReleaseName(pkg.id)
                val release = sw360ReleaseClient.getSparseReleaseByNameAndVersion(name, pkg.id.version)
                    .flatMap { sw360ReleaseClient.enrichSparseRelease(it) }
                    .orElseGet { createSw360Release(pkg, sw360ReleaseClient) }

                if (attachSources) {
                    val tempDirectory = createOrtTempDir()
                    try {
                        // First, download the sources of the package into a source directory, whose parent directory
                        // is temporary.
                        val sourcesDirectory = tempDirectory / "sources"
                        downloader.download(pkg, sourcesDirectory)

                        // After downloading the source files successfully in a source directory, create a ZIP file of
                        // the sources directory and save it in the root directory of it. Finally, the created ZIP file
                        // of the sources can be uploaded to SW360 as an attachment of the release.
                        val zipFile = tempDirectory / "${pkg.id.toPath("-")}.zip"
                        val archiveResult = sourcesDirectory.packZip(zipFile)

                        val uploadResult = sw360ReleaseClient.uploadAttachments(
                            AttachmentUploadRequest.builder(release)
                                .addAttachment(archiveResult.toPath(), SW360AttachmentType.SOURCE)
                                .build()
                        )

                        if (uploadResult.isSuccess) {
                            logger.info {
                                "Successfully uploaded source attachment '${zipFile.name}' to release " +
                                    "${release.id}:${release.name}"
                            }
                        } else {
                            logger.error { "Could not upload source attachment: " + uploadResult.failedUploads() }
                        }
                    } finally {
                        tempDirectory.safeDeleteRecursively()
                    }
                }

                release
            }

            val sw360Project = sw360ProjectClient.getProjectByNameAndVersion(project.id.name, project.id.version)
                .orElseGet { createSw360Project(project, sw360ProjectClient) }

            sw360Project?.let {
                sw360ProjectClient.addSW360ReleasesToSW360Project(it.id, linkedReleases)
            }
        }
    }

    private fun createSw360Project(project: Project, client: SW360ProjectClientAdapter): SW360Project? {
        val sw360Project = SW360Project()
            .setName(project.id.name)
            .setVersion(project.id.version)
            .setDescription("A ${project.id.type} project with the purl ${project.id.toPurl()}.")
            .setVisibility(SW360Visibility.BUISNESSUNIT_AND_MODERATORS)

        return try {
            client.createProject(sw360Project)?.also {
                logger.debug { "Project '${it.name}-${it.version}' created in SW360." }
            }
        } catch (e: SW360ClientException) {
            logger.error {
                "Could not create the project '${project.id.toCoordinates()}' in SW360: " + e.collectMessages()
            }

            null
        }
    }

    private fun createSw360Release(pkg: Package, client: SW360ReleaseClientAdapter): SW360Release? {
        // TODO: This omits operators and exceptions from licenses. We yet need to find a way to pass these to SW360.
        val licenseShortNames = pkg.declaredLicensesProcessed.spdxExpression?.licenses().orEmpty().toSet()

        val unmappedLicenses = pkg.declaredLicensesProcessed.unmapped
        if (unmappedLicenses.isNotEmpty()) {
            logger.warn {
                "The following licenses could not be mapped in order to create a SW360 release: $unmappedLicenses"
            }
        }

        val sw360Release = SW360Release()
            .setMainLicenseIds(licenseShortNames)
            .setName(createReleaseName(pkg.id))
            .setVersion(pkg.id.version)

        return try {
            client.createRelease(sw360Release)?.also {
                logger.debug { "Release '${it.name}-${it.version}' created in SW360." }
            }
        } catch (e: SW360ClientException) {
            logger.error {
                "Could not create the release for '${pkg.id.toCoordinates()}' in SW360: " + e.collectMessages()
            }

            null
        }
    }

    private fun getProjectWithPackages(ortResult: OrtResult): Map<Project, List<Package>> =
        ortResult.getProjects(omitExcluded = true).associateWith { project ->
            // Upload the uncurated packages because SW360 also is a package curation provider.
            ortResult.dependencyNavigator.projectDependencies(project)
                .mapNotNull { ortResult.getUncuratedPackageOrProject(it) }
        }

    private fun createReleaseName(pkgId: Identifier) =
        listOf(pkgId.namespace, pkgId.name).filter { it.isNotEmpty() }.joinToString("/")
}
