/*
 * Copyright (C) 2020 Bosch.IO GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package org.ossreviewtoolkit.commands

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import kotlin.time.measureTimedValue

import org.eclipse.sw360.antenna.http.HttpClientFactoryImpl
import org.eclipse.sw360.antenna.http.config.HttpClientConfig
import org.eclipse.sw360.antenna.sw360.client.adapter.SW360Connection
import org.eclipse.sw360.antenna.sw360.client.adapter.SW360ConnectionFactory
import org.eclipse.sw360.antenna.sw360.client.adapter.SW360ProjectClientAdapter
import org.eclipse.sw360.antenna.sw360.client.adapter.SW360ReleaseClientAdapter
import org.eclipse.sw360.antenna.sw360.client.config.SW360ClientConfig
import org.eclipse.sw360.antenna.sw360.client.rest.resource.SW360Visibility
import org.eclipse.sw360.antenna.sw360.client.rest.resource.projects.SW360Project
import org.eclipse.sw360.antenna.sw360.client.rest.resource.releases.SW360Release
import org.eclipse.sw360.antenna.sw360.client.utils.SW360ClientException

import org.ossreviewtoolkit.GlobalOptions
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.config.Sw360StorageConfiguration
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.utils.collectMessagesAsString
import org.ossreviewtoolkit.utils.expandTilde
import org.ossreviewtoolkit.utils.formatSizeInMib
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.perf

class UploadResultToSw360Command : CliktCommand(
    name = "upload-result-to-sw360",
    help = "Upload an ORT result to SW360.",
    epilog = "EXPERIMENTAL: The command is still in development and usage will likely change in the near future. The " +
            "command expects that a Sw360Storage for the scanner is configured."
) {
    private val ortFile by option(
        "--ort-file", "-i",
        help = "The ORT result file to read as input."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()
        .inputGroup()

    private val globalOptionsForSubcommands by requireObject<GlobalOptions>()

    override fun run() {
        val (ortResult, duration) = measureTimedValue { ortFile.readValue<OrtResult>() }

        log.perf {
            "Read ORT result from '${ortFile.name}' (${ortFile.formatSizeInMib}) in ${duration.inMilliseconds}ms."
        }

        val sw360Config = globalOptionsForSubcommands.config.scanner?.storages?.values
            ?.filterIsInstance<Sw360StorageConfiguration>()?.singleOrNull()

        requireNotNull(sw360Config) {
            "No SW360 storage is configured for the scanner."
        }

        val sw360JsonMapper = jsonMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val sw360Connection = createSw360Connection(sw360Config, sw360JsonMapper)
        val sw360ReleaseClient = sw360Connection.releaseAdapter
        val sw360ProjectClient = sw360Connection.projectAdapter

        getProjectWithPackages(ortResult).forEach { (project, pkgList) ->
            val linkedReleases = pkgList.mapNotNull { pkg ->
                val name = listOfNotNull(pkg.id.namespace, pkg.id.name).joinToString("/")
                sw360ReleaseClient.getSparseReleaseByNameAndVersion(name, pkg.id.version)
                    .flatMap { sw360ReleaseClient.enrichSparseRelease(it) }
                    .orElse(createSw360Release(pkg, sw360ReleaseClient))
            }

            val sw360Project = sw360ProjectClient.getProjectByNameAndVersion(project.id.name, project.id.version)
                .orElse(createSw360Project(project, sw360ProjectClient))

            sw360Project?.let {
                sw360ProjectClient.addSW360ReleasesToSW360Project(it.id, linkedReleases)
            }
        }
    }

    private fun createSw360Project(project: Project, client: SW360ProjectClientAdapter): SW360Project? {
        val sw360Project = SW360Project()
            .setName(project.id.name)
            .setVersion(project.id.version)
            .setDescription("A ${project.id.type} project with the PURL ${project.id.toPurl()}.")
            .setVisibility(SW360Visibility.BUISNESSUNIT_AND_MODERATORS)

        return try {
            client.createProject(sw360Project)?.also {
                log.debug { "Project '${it.name}-${it.version}' created in SW360." }
            }
        } catch (e: SW360ClientException) {
            log.error {
                "Could not create the project '${project.id.toCoordinates()}' in SW360: " + e.collectMessagesAsString()
            }

            null
        }
    }

    private fun createSw360Release(pkg: Package, client: SW360ReleaseClientAdapter): SW360Release? {
        val sw360Release = SW360Release()
            .setMainLicenseIds(pkg.declaredLicenses)
            .setName(pkg.id.name)
            .setVersion(pkg.id.version)

        return try {
            client.createRelease(sw360Release)?.also {
                log.debug { "Release '${it.name}-${it.version}' created in SW360." }
            }
        } catch (e: SW360ClientException) {
            log.error {
                "Could not create the release for '${pkg.id.toCoordinates()}' in SW360: " + e.collectMessagesAsString()
            }

            null
        }
    }

    private fun createSw360Connection(config: Sw360StorageConfiguration, jsonMapper: ObjectMapper): SW360Connection {
        val httpClientConfig = HttpClientConfig
            .basicConfig()
            .withObjectMapper(jsonMapper)
        val httpClient = HttpClientFactoryImpl().newHttpClient(httpClientConfig)

        val sw360ClientConfig = SW360ClientConfig.createConfig(
            config.restUrl,
            config.authUrl,
            config.username,
            config.password,
            config.clientId,
            config.clientPassword,
            httpClient,
            jsonMapper
        )

        return SW360ConnectionFactory().newConnection(sw360ClientConfig)
    }

    private fun getProjectWithPackages(ortResult: OrtResult): Map<Project, List<Package>> =
        ortResult.getProjects(omitExcluded = true).associateWith { project ->
            project.collectDependencies().mapNotNull { ortResult.getUncuratedPackageById(it) }
        }
}
