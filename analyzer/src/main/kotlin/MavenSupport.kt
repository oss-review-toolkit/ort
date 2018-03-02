/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

package com.here.ort.analyzer

import ch.frankel.slf4k.*

import com.here.ort.model.HashAlgorithm
import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.VcsInfo
import com.here.ort.utils.DiskCache
import com.here.ort.utils.collectMessages
import com.here.ort.utils.getUserConfigDirectory
import com.here.ort.utils.log
import com.here.ort.utils.printStackTrace
import com.here.ort.utils.yamlMapper

import org.apache.maven.artifact.repository.LegacyLocalRepositoryManager
import org.apache.maven.bridge.MavenRepositorySystem
import org.apache.maven.execution.DefaultMavenExecutionRequest
import org.apache.maven.execution.MavenExecutionRequestPopulator
import org.apache.maven.model.building.ModelBuildingRequest
import org.apache.maven.project.MavenProject
import org.apache.maven.project.ProjectBuilder
import org.apache.maven.project.ProjectBuildingRequest
import org.apache.maven.project.ProjectBuildingResult
import org.apache.maven.repository.internal.MavenRepositorySystemUtils

import org.codehaus.plexus.DefaultContainerConfiguration
import org.codehaus.plexus.DefaultPlexusContainer
import org.codehaus.plexus.PlexusConstants
import org.codehaus.plexus.PlexusContainer
import org.codehaus.plexus.classworlds.ClassWorld
import org.codehaus.plexus.logging.BaseLoggerManager

import org.eclipse.aether.DefaultRepositorySystemSession
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.impl.RemoteRepositoryManager
import org.eclipse.aether.impl.RepositoryConnectorProvider
import org.eclipse.aether.repository.LocalRepositoryManager
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.ArtifactDescriptorRequest
import org.eclipse.aether.spi.connector.ArtifactDownload
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutProvider
import org.eclipse.aether.spi.connector.transport.GetTask
import org.eclipse.aether.spi.connector.transport.TransporterProvider
import org.eclipse.aether.transfer.AbstractTransferListener
import org.eclipse.aether.transfer.NoRepositoryLayoutException
import org.eclipse.aether.transfer.TransferEvent

import java.io.File
import java.util.regex.Pattern

fun Artifact.identifier() = "$groupId:$artifactId:$version"

class MavenSupport(localRepositoryManagerConverter: (LocalRepositoryManager) -> LocalRepositoryManager) {
    companion object {
        private const val GIGABYTE = 1024L * 1024L
        private const val HOUR = 60 * 60

        val SCM_REGEX = Pattern.compile("scm:(?<type>[^:]+):(?<url>.+)")!!

        private val remoteArtifactCache =
                DiskCache(File(getUserConfigDirectory(), "${Main.TOOL_NAME}/cache/remote_artifacts"),
                        GIGABYTE, 6 * HOUR)
    }

    val container = createContainer()
    private val repositorySystemSession = createRepositorySystemSession(localRepositoryManagerConverter)

    private fun createContainer(): PlexusContainer {
        val configuration = DefaultContainerConfiguration().apply {
            autoWiring = true
            classPathScanning = PlexusConstants.SCANNING_INDEX
            classWorld = ClassWorld("plexus.core", javaClass.classLoader)
        }

        return DefaultPlexusContainer(configuration).apply {
            loggerManager = object : BaseLoggerManager() {
                override fun createLogger(name: String) = MavenLogger(log.effectiveLevel)
            }
        }
    }

    private fun createRepositorySystemSession(
            localRepositoryManagerConverter: (LocalRepositoryManager) -> LocalRepositoryManager = { it })
            : RepositorySystemSession {
        val mavenRepositorySystem = container.lookup(MavenRepositorySystem::class.java, "default")
        val aetherRepositorySystem = container.lookup(RepositorySystem::class.java, "default")
        val repositorySystemSession = MavenRepositorySystemUtils.newSession()
        val mavenExecutionRequest = DefaultMavenExecutionRequest()
        val localRepository = mavenRepositorySystem.createLocalRepository(mavenExecutionRequest,
                org.apache.maven.repository.RepositorySystem.defaultUserLocalRepository)

        val session = LegacyLocalRepositoryManager.overlay(localRepository, repositorySystemSession,
                aetherRepositorySystem)

        val localRepositoryManager = localRepositoryManagerConverter(session.localRepositoryManager)

        return if (localRepositoryManager == session.localRepositoryManager) {
            session
        } else {
            DefaultRepositorySystemSession(session).setLocalRepositoryManager(localRepositoryManager)
        }
    }

    fun buildMavenProject(pomFile: File): ProjectBuildingResult {
        val projectBuilder = container.lookup(ProjectBuilder::class.java, "default")
        val projectBuildingRequest = createProjectBuildingRequest(true)

        return projectBuilder.build(pomFile, projectBuildingRequest)
    }

    fun createProjectBuildingRequest(resolveDependencies: Boolean): ProjectBuildingRequest {
        val request = DefaultMavenExecutionRequest()

        val populator = container.lookup(MavenExecutionRequestPopulator::class.java, "default")
        populator.populateDefaults(request)

        val projectBuildingRequest = request.projectBuildingRequest

        return projectBuildingRequest.apply {
            isResolveDependencies = resolveDependencies
            repositorySession = repositorySystemSession
            systemProperties["java.home"] = System.getProperty("java.home")
            systemProperties["java.version"] = System.getProperty("java.version")
            validationLevel = ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL
        }
    }

    private fun requestRemoteArtifact(artifact: Artifact, repositories: List<RemoteRepository>): RemoteArtifact {
        remoteArtifactCache.read(artifact.toString())?.let {
            log.debug { "Reading remote artifact for $artifact from disk cache." }
            return yamlMapper.readValue(it, RemoteArtifact::class.java)
        }

        val repoSystem = container.lookup(RepositorySystem::class.java, "default")
        val remoteRepositoryManager = container.lookup(RemoteRepositoryManager::class.java, "default")
        val repositoryLayoutProvider = container.lookup(RepositoryLayoutProvider::class.java, "default")
        val repositoryConnectorProvider = container.lookup(RepositoryConnectorProvider::class.java, "default")
        val transporterProvider = container.lookup(TransporterProvider::class.java, "default")

        // Create an artifact descriptor to get the list of repositories from the related POM file.
        val artifactDescriptorRequest = ArtifactDescriptorRequest(artifact, repositories, "project")
        val artifactDescriptorResult = repoSystem
                .readArtifactDescriptor(repositorySystemSession, artifactDescriptorRequest)
        val allRepositories = (artifactDescriptorResult.repositories + repositories).distinct()

        // Filter local repositories, as remote artifacts should never point to files on the local disk.
        val remoteRepositories = allRepositories.filterNot { it.url.startsWith("file:/") }
        log.debug { "Found potential repositories for '$artifact': $remoteRepositories" }
        log.debug { "Ignoring local repositories: ${allRepositories - remoteRepositories}" }

        // Check the remote repositories for the availability of the artifact.
        // TODO: Currently only the first hit is stored, could query the rest of the repositories if required.
        remoteRepositories.forEach { repository ->
            val repositoryLayout = try {
                repositoryLayoutProvider.newRepositoryLayout(repositorySystemSession, repository)
            } catch (e: NoRepositoryLayoutException) {
                if (printStackTrace) {
                    e.printStackTrace()
                }

                log.warn { "Could not search for '$artifact'in '$repository': ${e.message}" }

                return@forEach
            }

            val remoteLocation = repositoryLayout.getLocation(artifact, false)
            log.debug { "Remote location for '$artifact': $remoteLocation" }

            val snapshot = artifact.isSnapshot
            val policy = remoteRepositoryManager.getPolicy(repositorySystemSession, repository, !snapshot, snapshot)

            val localPath = repositorySystemSession.localRepositoryManager
                    .getPathForRemoteArtifact(artifact, repository, "project")
            val downloadFile = File(repositorySystemSession.localRepositoryManager.repository.basedir, localPath)

            val artifactDownload = ArtifactDownload(artifact, "project", downloadFile, policy.checksumPolicy)
            artifactDownload.isExistenceCheck = true
            artifactDownload.listener = object : AbstractTransferListener() {
                override fun transferFailed(event: TransferEvent?) {
                    log.debug { "Transfer failed: $event" }
                }

                override fun transferSucceeded(event: TransferEvent?) {
                    log.debug { "Transfer succeeded: $event" }
                }
            }

            val repositoryConnector = repositoryConnectorProvider
                    .newRepositoryConnector(repositorySystemSession, repository)
            repositoryConnector.get(listOf(artifactDownload), null)

            if (artifactDownload.exception == null) {
                log.debug { "Found '$artifact' in $repository." }

                // TODO: Could store multiple checksums in model instead of only the first.
                val checksums = repositoryLayout.getChecksums(artifact, false, remoteLocation)
                log.debug { "Checksums: $checksums" }

                val checksum = checksums.first()
                val tempFile = File.createTempFile("checksum", checksum.algorithm)

                val transporter = transporterProvider.newTransporter(repositorySystemSession, repository)
                val actualChecksum = try {
                    transporter.get(GetTask(checksum.location).setDataFile(tempFile))

                    // Sometimes the checksum file contains a path after the actual checksum, so strip everything after
                    // the first space.
                    tempFile.useLines { it.first().substringBefore(" ") }
                } catch (e: Exception) {
                    if (com.here.ort.utils.printStackTrace) {
                        e.printStackTrace()
                    }

                    log.warn { "Could not get checksum for '$artifact': ${e.message}" }

                    // Fall back to an empty checksum string.
                    ""
                }

                if (!tempFile.delete()) {
                    log.warn { "Unable to delete temporary file '$tempFile'." }
                }

                val downloadUrl = "${repository.url.trimEnd('/')}/$remoteLocation"
                return RemoteArtifact(downloadUrl, actualChecksum, HashAlgorithm.fromString(checksum.algorithm)).also {
                    log.debug { "Writing remote artifact for $artifact to disk cache." }
                    remoteArtifactCache.write(artifact.toString(), yamlMapper.writeValueAsString(it))
                }
            } else {
                log.debug {
                    "Could not find '$artifact' in '$repository': ${artifactDownload.exception.collectMessages()}"
                }
            }
        }

        log.warn { "Unable to find '$artifact' in any of ${remoteRepositories.map { it.url }}." }

        return RemoteArtifact.EMPTY.also {
            log.debug { "Writing empty remote artifact for $artifact to disk cache." }
            remoteArtifactCache.write(artifact.toString(), yamlMapper.writeValueAsString(it))
        }
    }

    /**
     * Create an instance of [Package] from the information found in a POM file.
     *
     * @param artifact The artifact for which the [Package] will be created.
     * @param repositories A list of remote repositories to search for [artifact].
     * @param cachedProjects Instances of [MavenProject] which have already been created, mapped by their identifier. If
     *                       a project is found in this map no remote repositories will be queried.
     */
    fun parsePackage(artifact: Artifact, repositories: List<RemoteRepository>,
                     cachedProjects: Map<String, MavenProject> = emptyMap()): Package {
        val mavenRepositorySystem = container.lookup(MavenRepositorySystem::class.java, "default")
        val projectBuilder = container.lookup(ProjectBuilder::class.java, "default")
        val projectBuildingRequest = createProjectBuildingRequest(true)

        projectBuildingRequest.remoteRepositories = repositories.map {
            mavenRepositorySystem.createRepository(it.url, it.id, true, null, true, null, null)
        }

        val cachedProject = cachedProjects[artifact.identifier()]

        val mavenProject = cachedProject ?: artifact.let {
            val pomArtifact = mavenRepositorySystem
                    .createArtifact(it.groupId, it.artifactId, it.version, "", "pom")

            projectBuilder.build(pomArtifact, projectBuildingRequest).project
        }

        val binaryRemoteArtifact = requestRemoteArtifact(artifact, repositories)

        val sourceRemoteArtifact = if (artifact.extension == "pom") {
            binaryRemoteArtifact
        } else {
            val sourceArtifact = artifact.let {
                DefaultArtifact(it.groupId, it.artifactId, "sources", "jar", it.version)
            }

            requestRemoteArtifact(sourceArtifact, repositories)
        }

        val vcsFromPackage = parseVcsInfo(mavenProject)

        return Package(
                id = Identifier(
                        provider = "Maven",
                        namespace = mavenProject.groupId,
                        name = mavenProject.artifactId,
                        version = mavenProject.version
                ),
                declaredLicenses = parseLicenses(mavenProject),
                description = mavenProject.description ?: "",
                homepageUrl = mavenProject.url ?: "",
                binaryArtifact = binaryRemoteArtifact,
                sourceArtifact = sourceRemoteArtifact,
                vcs = vcsFromPackage,
                vcsProcessed = PackageManager.processPackageVcs(vcsFromPackage)
        )
    }

    fun parseLicenses(mavenProject: MavenProject) =
            mavenProject.licenses.mapNotNull { it.name ?: it.url ?: it.comments }.toSortedSet()

    fun parseVcsInfo(mavenProject: MavenProject): VcsInfo {
        // When asking Maven for the SCM URL of a POM that does not itself define an SCM URL, Maven returns the SCM
        // URL of the parent POM (if any) and appends the child POM's artifactId to it. This behavior is
        // fundamentally broken because it invalidates the URL for many SCMs that cannot clone / checkout a specific
        // path from a repository. Also, the assumption that the source code for a child artifact is stored in a
        // top-level directory named like the artifactId inside the parent artifact's repository is often not
        // correct.
        // To fix this, determine the SCM URL of the root parent (if there are parents) and use that as the child's
        // SCM URL.
        var scm = mavenProject.scm
        var parent = mavenProject.parent

        while (parent != null) {
            parent.scm?.let {
                it.connection?.let {
                    if (it.isNotBlank() && scm.connection.startsWith(it)) {
                        scm = parent.scm
                    }
                }
            }

            parent = parent.parent
        }

        if (scm == null) return VcsInfo.EMPTY

        val connection = scm.connection ?: ""
        val tag = scm.tag?.takeIf { it != "HEAD" } ?: ""

        val (type, url) = SCM_REGEX.matcher(connection).let {
            if (it.matches()) {
                val type = it.group("type")
                val url = it.group("url")

                // CVS URLs usually start with ":pserver:" or ":ext:", but as ":" is also the delimiter used by the
                // Maven SCM plugin, no double ":" is used in the connection string and we need to fix it up here.
                if (type == "cvs" && !url.startsWith(":")) {
                    Pair(type, ":$url")
                } else {
                    Pair(type, url)
                }
            } else {
                Pair("", "")
            }
        }

        return VcsInfo(type, url, tag, "")
    }
}
