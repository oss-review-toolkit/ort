/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.analyzer.managers.utils

import java.io.File
import java.net.URI

import kotlin.time.Duration.Companion.hours

import org.apache.logging.log4j.kotlin.Logging
import org.apache.maven.artifact.repository.LegacyLocalRepositoryManager
import org.apache.maven.bridge.MavenRepositorySystem
import org.apache.maven.execution.DefaultMavenExecutionRequest
import org.apache.maven.execution.DefaultMavenExecutionResult
import org.apache.maven.execution.MavenExecutionRequest
import org.apache.maven.execution.MavenExecutionRequestPopulator
import org.apache.maven.execution.MavenSession
import org.apache.maven.internal.aether.DefaultRepositorySystemSessionFactory
import org.apache.maven.model.Scm
import org.apache.maven.model.building.ModelBuildingRequest
import org.apache.maven.plugin.LegacySupport
import org.apache.maven.project.MavenProject
import org.apache.maven.project.ProjectBuilder
import org.apache.maven.project.ProjectBuildingException
import org.apache.maven.project.ProjectBuildingRequest
import org.apache.maven.project.ProjectBuildingResult
import org.apache.maven.properties.internal.EnvironmentUtils
import org.apache.maven.session.scope.internal.SessionScope

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
import org.eclipse.aether.repository.MirrorSelector
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.repository.WorkspaceReader
import org.eclipse.aether.resolution.ArtifactDescriptorRequest
import org.eclipse.aether.spi.connector.ArtifactDownload
import org.eclipse.aether.spi.connector.layout.RepositoryLayout
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutProvider
import org.eclipse.aether.spi.connector.transport.GetTask
import org.eclipse.aether.spi.connector.transport.TransporterProvider
import org.eclipse.aether.transfer.AbstractTransferListener
import org.eclipse.aether.transfer.NoRepositoryConnectorException
import org.eclipse.aether.transfer.TransferEvent
import org.eclipse.aether.util.repository.JreProxySelector

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageProvider
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.fromYaml
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.model.utils.parseRepoManifestPath
import org.ossreviewtoolkit.utils.common.DiskCache
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.gibibytes
import org.ossreviewtoolkit.utils.common.searchUpwardsForSubdirectory
import org.ossreviewtoolkit.utils.common.splitOnWhitespace
import org.ossreviewtoolkit.utils.common.withoutPrefix
import org.ossreviewtoolkit.utils.ort.DeclaredLicenseProcessor
import org.ossreviewtoolkit.utils.ort.OrtAuthenticator
import org.ossreviewtoolkit.utils.ort.OrtProxySelector
import org.ossreviewtoolkit.utils.ort.ProcessedDeclaredLicense
import org.ossreviewtoolkit.utils.ort.downloadText
import org.ossreviewtoolkit.utils.ort.okHttpClient
import org.ossreviewtoolkit.utils.ort.ortDataDirectory
import org.ossreviewtoolkit.utils.ort.showStackTrace
import org.ossreviewtoolkit.utils.spdx.SpdxOperator

fun Artifact.identifier() = "$groupId:$artifactId:$version"

/**
 * Return the path to this file or a corresponding message if the file is unknown.
 */
private val File?.safePath: String
    get() = this?.invariantSeparatorsPath ?: "<unknown file>"

class MavenSupport(private val workspaceReader: WorkspaceReader) {
    companion object : Logging {
        private val PACKAGING_TYPES = setOf(
            // Core packaging types, see https://maven.apache.org/pom.html#packaging.
            "pom", "jar", "maven-plugin", "ejb", "war", "ear", "rar",
            // Custom packaging types, see "resources/META-INF/plexus/components.xml".
            "aar", "apk", "bundle", "dll", "dylib", "eclipse-plugin", "gwt-app", "gwt-lib", "hk2-jar", "hpi",
            "jenkins-module", "orbit", "so", "zip"
        )

        // See http://maven.apache.org/pom.html#SCM.
        private val SCM_REGEX = Regex("scm:(?<type>[^:@]+):(?<url>.+)")
        private val USER_HOST_REGEX = Regex("scm:(?<user>[^:@]+)@(?<host>[^:]+)[:/](?<path>.+)")

        private val remoteArtifactCache = DiskCache(
            directory = ortDataDirectory.resolve("cache/analyzer/maven/remote-artifacts"),
            maxCacheSizeInBytes = 1.gibibytes,
            maxCacheEntryAgeInSeconds = 6.hours.inWholeSeconds
        )

        private fun createContainer(): PlexusContainer {
            val configuration = DefaultContainerConfiguration().apply {
                autoWiring = true
                classPathScanning = PlexusConstants.SCANNING_INDEX
                classWorld = ClassWorld("plexus.core", javaClass.classLoader)
            }

            return DefaultPlexusContainer(configuration).apply {
                loggerManager = object : BaseLoggerManager() {
                    override fun createLogger(name: String) = MavenLogger(MavenSupport.logger.delegate.level)
                }
            }
        }

        fun parseAuthors(mavenProject: MavenProject): Set<String> =
            buildSet {
                mavenProject.organization?.let {
                    if (!it.name.isNullOrEmpty()) add(it.name)
                }

                val developers = mavenProject.developers.mapNotNull { it.organization.orEmpty().ifEmpty { it.name } }
                addAll(developers)
            }

        fun parseLicenses(mavenProject: MavenProject): Set<String> =
            mavenProject.licenses.mapNotNullTo(mutableSetOf()) { license ->
                listOfNotNull(license.name, license.url, license.comments).firstOrNull { it.isNotBlank() }
            }

        fun processDeclaredLicenses(licenses: Set<String>): ProcessedDeclaredLicense =
            // See http://maven.apache.org/ref/3.6.3/maven-model/maven.html#project which says: "If multiple licenses
            // are listed, it is assumed that the user can select any of them, not that they must accept all."
            DeclaredLicenseProcessor.process(licenses, operator = SpdxOperator.OR)

        /**
         * When asking Maven for the SCM connection or the SCM URL of a POM that does not itself define these values,
         * Maven returns the values of the first parent POM (if any) that defines one and appends the artifactIds of all
         * child POMs to it, separated by slashes.
         * This behavior is fundamentally broken because it invalidates the SCM connection / URL for all VCS that cannot
         * limit cloning to a specific path within a repository, or use a different syntax for that. Also, the
         * assumption that the source code for a child artifact is stored in a top-level directory named like the
         * artifactId inside the parent artifact's repository is often not correct.
         * To address this, determine the SCM connection and URL of the parent (if any) that is closest to the root POM
         * and whose SCM connection / URL still is a prefix of the child POM's SCM values.
         */
        fun getOriginalScm(mavenProject: MavenProject): Scm? {
            val scm = mavenProject.scm
            var parent = mavenProject.parent

            while (parent != null) {
                parent.scm?.let { parentScm ->
                    parentScm.connection?.let { parentConnection ->
                        if (parentConnection.isNotBlank() && scm.connection.startsWith(parentConnection)) {
                            scm.connection = parentScm.connection
                        }
                    }

                    parentScm.url?.let { parentUrl ->
                        if (parentUrl.isNotBlank() && scm.url.startsWith(parentUrl)) {
                            scm.url = parentScm.url
                        }
                    }
                }

                parent = parent.parent
            }

            return scm
        }

        fun parseVcsInfo(project: MavenProject): VcsInfo {
            val scm = getOriginalScm(project)
            val connection = scm?.connection
            if (connection.isNullOrEmpty()) return VcsInfo.EMPTY

            val tag = scm.tag?.takeIf { it != "HEAD" }.orEmpty()

            return SCM_REGEX.matchEntire(connection)?.let { match ->
                val type = match.groups["type"]!!.value
                val url = match.groups["url"]!!.value

                getVcsInfo(type, url, tag)
            } ?: run {
                USER_HOST_REGEX.matchEntire(connection)?.let { match ->
                    // Some projects omit the provider and use the SCP-like Git URL syntax, for example
                    // "scm:git@github.com:facebook/facebook-android-sdk.git".
                    val user = match.groups["user"]!!.value
                    val host = match.groups["host"]!!.value
                    val path = match.groups["path"]!!.value

                    if (user == "git" || host.startsWith("git")) {
                        VcsInfo(type = VcsType.GIT, url = "https://$host/$path", revision = tag)
                    } else {
                        VcsInfo.EMPTY
                    }
                } ?: run {
                    if (connection.startsWith("git://") || connection.endsWith(".git")) {
                        // It is a common mistake to omit the "scm:[provider]:" prefix. Add fall-backs for nevertheless
                        // clear cases.
                        logger.info {
                            "Maven SCM connection '$connection' of project ${project.artifact} lacks the required " +
                                    "'scm' prefix."
                        }

                        VcsInfo(type = VcsType.GIT, url = connection, revision = tag)
                    } else {
                        logger.info {
                            "Ignoring Maven SCM connection '$connection' of project ${project.artifact} due to an " +
                                    "unexpected format."
                        }

                        VcsInfo.EMPTY
                    }
                }
            }
        }

        private fun getVcsInfo(type: String, url: String, tag: String) =
            when {
                // Maven does not officially support git-repo as an SCM, see
                // http://maven.apache.org/scm/scms-overview.html, so come up with the convention to use the
                // "manifest" query parameter for the path to the manifest inside the repository. An earlier
                // version of this workaround expected the query string to be only the path to the manifest, for
                // backward compatibility convert such URLs to the new syntax.
                type == "git-repo" -> {
                    val manifestPath = url.parseRepoManifestPath()
                        ?: url.substringAfter('?').takeIf { it.isNotBlank() && it.endsWith(".xml") }
                    val urlWithManifest = url.takeIf { manifestPath == null }
                        ?: "${url.substringBefore('?')}?manifest=$manifestPath"

                    VcsInfo(
                        type = VcsType.GIT_REPO,
                        url = urlWithManifest,
                        revision = tag
                    )
                }

                type == "svn" -> {
                    val revision = tag.takeIf { it.isEmpty() } ?: "tags/$tag"
                    VcsInfo(type = VcsType.SUBVERSION, url = url, revision = revision)
                }

                url.startsWith("//") -> {
                    // Work around the common mistake to omit the Maven SCM provider.
                    val fixedUrl = "$type:$url"

                    // Try to detect the Maven SCM provider from the URL only, e.g. by looking at the host or
                    // special URL paths.
                    VcsHost.parseUrl(fixedUrl).copy(revision = tag).also {
                        logger.info { "Fixed up invalid SCM connection without a provider to $it." }
                    }
                }

                else -> {
                    val trimmedUrl = if (!url.startsWith("git://")) url.removePrefix("git:") else url

                    VcsHost.fromUrl(trimmedUrl)?.let { host ->
                        host.toVcsInfo(trimmedUrl)?.let { vcsInfo ->
                            // Fixup paths that are specified as part of the URL and contain the project name as
                            // a prefix.
                            val projectPrefix = "${host.getProject(trimmedUrl)}-"
                            vcsInfo.path.withoutPrefix(projectPrefix)?.let { path ->
                                vcsInfo.copy(path = path)
                            }
                        }
                    } ?: VcsInfo(type = VcsType.forName(type), url = trimmedUrl, revision = tag)
                }
            }

        /**
         * Split the provided [checksum] by whitespace and return a [Hash] for the first element that matches the
         * provided algorithm. If no element matches, return [Hash.NONE]. This works around the issue that Maven
         * checksum files sometimes contain arbitrary strings before or after the actual checksum.
         */
        internal fun parseChecksum(checksum: String, algorithm: String) =
            checksum.splitOnWhitespace().firstNotNullOfOrNull {
                runCatching { Hash.create(it, algorithm) }.getOrNull()
            } ?: Hash.NONE

        /**
         * Return true if an artifact that has not been requested from Maven Central is also available on Maven Central
         * but with a different hash, otherwise return false.
         */
        private fun isArtifactModified(artifact: Artifact, remoteArtifact: RemoteArtifact): Boolean =
            with(remoteArtifact) {
                if (url.isBlank() || PackageProvider.get(url) == PackageProvider.MAVEN_CENTRAL) return false

                val name = url.substringAfterLast('/')
                val algorithm = hash.algorithm.name.lowercase()

                val mavenCentralUrl = with(artifact) {
                    val group = groupId.replace('.', '/')
                    "https://repo.maven.apache.org/maven2/$group/$artifactId/$version/$name.$algorithm"
                }

                val checksum = okHttpClient.downloadText(mavenCentralUrl).getOrElse { return false }
                !hash.verify(parseChecksum(checksum, hash.algorithm.name))
            }
    }

    val container = createContainer()
    private val repositorySystemSession = createRepositorySystemSession(workspaceReader)

    // The MavenSettingsBuilder class is deprecated, but internally it uses its successor SettingsBuilder. Calling
    // MavenSettingsBuilder requires less code than calling SettingsBuilder, so use it until it is removed.
    @Suppress("DEPRECATION")
    private fun createMavenExecutionRequest(): MavenExecutionRequest {
        val request = DefaultMavenExecutionRequest()

        val props = System.getProperties()
        EnvironmentUtils.addEnvVars(props)
        request.systemProperties = props

        val populator = containerLookup<MavenExecutionRequestPopulator>()

        val settingsBuilder = containerLookup<org.apache.maven.settings.MavenSettingsBuilder>()
        // TODO: Add a way to configure the location of a user settings file and pass it to the method below which will
        //       merge the user settings with the global settings. The default location of the global settings file is
        //       "${user.home}/.m2/settings.xml". The settings file locations can already be overwritten using the
        //       system properties "org.apache.maven.global-settings" and "org.apache.maven.user-settings".
        val settings = settingsBuilder.buildSettings()

        populator.populateFromSettings(request, settings)
        populator.populateDefaults(request)
        repositorySystemSession.injectProxy(request)

        return request
    }

    private fun createRepositorySystemSession(workspaceReader: WorkspaceReader): RepositorySystemSession {
        val mavenRepositorySystem = containerLookup<MavenRepositorySystem>()
        val aetherRepositorySystem = containerLookup<RepositorySystem>()
        val repositorySystemSessionFactory = containerLookup<DefaultRepositorySystemSessionFactory>()

        val repositorySystemSession = repositorySystemSessionFactory
            .newRepositorySession(createMavenExecutionRequest())

        repositorySystemSession.mirrorSelector = HttpsMirrorSelector(repositorySystemSession.mirrorSelector)

        val localRepository = mavenRepositorySystem.createLocalRepository(
            createMavenExecutionRequest(),
            org.apache.maven.repository.RepositorySystem.defaultUserLocalRepository
        )

        val session = LegacyLocalRepositoryManager.overlay(
            localRepository, repositorySystemSession,
            aetherRepositorySystem
        )

        val skipDownloadWorkspaceReader = SkipBinaryDownloadsWorkspaceReader(workspaceReader)

        return DefaultRepositorySystemSession(session).apply {
            setWorkspaceReader(skipDownloadWorkspaceReader)
            OrtAuthenticator.install()
            OrtProxySelector.install()
            proxySelector = JreProxySelector()
        }
    }

    /**
     * Makes sure that the [MavenExecutionRequest] is correctly configured with the current proxy.
     *
     * This is necessary in the special case that in the Maven environment no repositories are
     * defined, and hence Maven Central is used as default. Then, for the Maven Central repository
     * no proxy is set.
     */
    private fun RepositorySystemSession.injectProxy(request: MavenExecutionRequest) {
        containerLookup<MavenRepositorySystem>().injectProxy(this, request.remoteRepositories)
    }

    /**
     * Looks up an instance of the class provided from the Maven Plexus container.
     */
    inline fun <reified T> containerLookup(hint: String = "default"): T =
        container.lookup(T::class.java, hint)

    /**
     * Build the Maven projects defined in the provided [pomFiles] without resolving dependencies. The result can later
     * be used to determine if a dependency points to another local project or to an external artifact.
     *
     * Note that build extensions are resolved by this function. This is required because extensions can provide
     * additional repository layouts or transports which are required to resolve dependencies. For details see the Maven
     * Wagon documentation [1].
     *
     * [1]: https://maven.apache.org/wagon/
     */
    fun prepareMavenProjects(pomFiles: List<File>): Map<String, ProjectBuildingResult> {
        val projectBuilder = containerLookup<ProjectBuilder>()
        val projectBuildingRequest = createProjectBuildingRequest(false).apply {
            repositorySession = DefaultRepositorySystemSession(repositorySession).apply {
                workspaceReader = this@MavenSupport.workspaceReader
            }
        }
        val projectBuildingResults = try {
            projectBuilder.build(pomFiles, false, projectBuildingRequest)
        } catch (e: ProjectBuildingException) {
            e.showStackTrace()

            logger.warn {
                "There have been issues building the Maven project models, this could lead to errors during " +
                        "dependency analysis: ${e.collectMessages()}"
            }

            e.results
        }

        val result = mutableMapOf<String, ProjectBuildingResult>()

        projectBuildingResults.forEach { projectBuildingResult ->
            if (projectBuildingResult.project == null) {
                logger.warn {
                    "Project for POM file '${projectBuildingResult.pomFile.absolutePath}' could not be built:\n" +
                            projectBuildingResult.problems.joinToString("\n")
                }
            } else {
                val project = projectBuildingResult.project
                val identifier = "${project.groupId}:${project.artifactId}:${project.version}"

                result[identifier] = projectBuildingResult
            }
        }

        return result
    }

    fun buildMavenProject(pomFile: File): ProjectBuildingResult {
        val projectBuilder = containerLookup<ProjectBuilder>()
        val projectBuildingRequest = createProjectBuildingRequest(true)

        return try {
            wrapMavenSession {
                projectBuilder.build(pomFile, projectBuildingRequest)
            }
        } catch (e: ProjectBuildingException) {
            e.showStackTrace()

            val resultForPomFile = e.results?.find { projectBuildingResult ->
                projectBuildingResult.pomFile == pomFile
            }

            if (resultForPomFile != null) {
                logger.warn {
                    "There was an error building project '${e.projectId}' at '${e.pomFile.safePath}'. " +
                            "Still continuing with the incompletely built project '${resultForPomFile.projectId}' at " +
                            "'${resultForPomFile.pomFile.safePath}': ${e.collectMessages()}"
                }

                resultForPomFile
            } else {
                logger.error {
                    "Failed to build project '${e.projectId}' at '${e.pomFile.safePath}': ${e.collectMessages()}"
                }

                throw e
            }
        }
    }

    fun createProjectBuildingRequest(resolveDependencies: Boolean): ProjectBuildingRequest {
        val projectBuildingRequest = createMavenExecutionRequest().projectBuildingRequest

        return projectBuildingRequest.apply {
            isResolveDependencies = resolveDependencies
            repositorySession = repositorySystemSession
            validationLevel = ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL
        }
    }

    private fun requestRemoteArtifact(
        artifact: Artifact,
        repositories: List<RemoteRepository>,
        useReposFromDependencies: Boolean
    ): RemoteArtifact {
        val allRepositories = if (useReposFromDependencies) {
            val repoSystem = containerLookup<RepositorySystem>()

            // Create an artifact descriptor to get the list of repositories from the related POM file.
            val artifactDescriptorRequest = ArtifactDescriptorRequest(artifact, repositories, "project")
            val artifactDescriptorResult = repoSystem
                .readArtifactDescriptor(repositorySystemSession, artifactDescriptorRequest)
            (repositories + artifactDescriptorResult.repositories).distinct()
        } else {
            repositories
        }

        val cacheKey = "$artifact@$allRepositories"

        remoteArtifactCache.read(cacheKey)?.let {
            logger.debug { "Reading remote artifact for '$artifact' from disk cache." }
            return it.fromYaml()
        }

        // Filter out local repositories, as remote artifacts should never point to files on the local disk.
        val remoteRepositories = allRepositories.filterNot {
            // Some (Linux) file URIs do not start with "file://" but look like "file:/opt/android-sdk-linux".
            it.url.startsWith("file:/")
        }.map { repository ->
            val proxy = repositorySystemSession.proxySelector.getProxy(repository)
            val authentication = repositorySystemSession.authenticationSelector.getAuthentication(repository)
            RemoteRepository.Builder(repository).setAuthentication(authentication).setProxy(proxy).build()
        }

        if (allRepositories.size > remoteRepositories.size) {
            logger.debug { "Ignoring local repositories ${allRepositories - remoteRepositories}." }
        }

        logger.debug { "Searching for '$artifact' in $remoteRepositories." }

        data class ArtifactLocationInfo(
            val repository: RemoteRepository,
            val layout: RepositoryLayout,
            val location: URI,
            val downloadUrl: String
        )

        val repositoryLayoutProvider = containerLookup<RepositoryLayoutProvider>()

        val locationInfo = remoteRepositories.mapNotNull { repository ->
            val repositoryLayout = runCatching {
                repositoryLayoutProvider.newRepositoryLayout(repositorySystemSession, repository)
            }.onFailure {
                it.showStackTrace()

                logger.warn { "Could not search for '$artifact' in '$repository': ${it.collectMessages()}" }
            }.getOrNull()

            repositoryLayout?.let { layout ->
                val location = layout.getLocation(artifact, false)
                val downloadUrl = "${repository.url.trimEnd('/')}/$location"
                ArtifactLocationInfo(repository, layout, location, downloadUrl)
            }
        }

        val remoteRepositoryManager = containerLookup<RemoteRepositoryManager>()
        val repositoryConnectorProvider = containerLookup<RepositoryConnectorProvider>()
        val transporterProvider = containerLookup<TransporterProvider>()

        // Check the remote repositories for the availability of the artifact.
        // TODO: Currently only the first hit is stored, could query the rest of the repositories if required.
        locationInfo.forEach { info ->
            logger.debug { "Trying to download artifact '$artifact' from ${info.downloadUrl}." }

            val snapshot = artifact.isSnapshot
            val policy = remoteRepositoryManager.getPolicy(
                repositorySystemSession, info.repository, !snapshot, snapshot
            )

            val localPath = repositorySystemSession.localRepositoryManager
                .getPathForRemoteArtifact(artifact, info.repository, "project")
            val downloadFile = File(repositorySystemSession.localRepositoryManager.repository.basedir, localPath)

            val artifactDownload = ArtifactDownload(artifact, "project", downloadFile, policy.checksumPolicy)
            artifactDownload.isExistenceCheck = true
            artifactDownload.listener = object : AbstractTransferListener() {
                override fun transferFailed(event: TransferEvent?) {
                    MavenSupport.logger.debug {
                        "Transfer failed for repository with ID '${info.repository.id}': $event"
                    }
                }

                override fun transferSucceeded(event: TransferEvent?) {
                    MavenSupport.logger.debug { "Transfer succeeded: $event" }
                }
            }

            try {
                wrapMavenSession {
                    repositoryConnectorProvider.newRepositoryConnector(repositorySystemSession, info.repository).use {
                        it.get(listOf(artifactDownload), null)
                    }
                }
            } catch (e: NoRepositoryConnectorException) {
                e.showStackTrace()

                logger.warn { "Could not create connector for repository '${info.repository}': ${e.collectMessages()}" }

                return@forEach
            }

            if (artifactDownload.exception == null) {
                logger.debug { "Found '$artifact' in '${info.repository}'." }

                val checksumLocations = info.layout.getChecksumLocations(artifact, false, info.location)

                // TODO: Could store multiple checksums in model instead of only the first.
                val checksumLocation = checksumLocations.first()

                val transporter = transporterProvider.newTransporter(repositorySystemSession, info.repository)

                val hash = runCatching {
                    val task = GetTask(checksumLocation.location)
                    transporter.get(task)

                    parseChecksum(task.dataString, checksumLocation.checksumAlgorithmFactory.name)
                }.getOrElse {
                    it.showStackTrace()

                    logger.warn { "Could not get checksum for '$artifact': ${it.collectMessages()}" }

                    // Fall back to an empty hash.
                    Hash.NONE
                }

                return RemoteArtifact(info.downloadUrl, hash).also { remoteArtifact ->
                    logger.debug { "Writing remote artifact for '$artifact' to disk cache." }
                    remoteArtifactCache.write(cacheKey, remoteArtifact.toYaml())
                }
            } else {
                logger.debug { artifactDownload.exception.collectMessages() }
            }
        }

        return RemoteArtifact.EMPTY.also { remoteArtifact ->
            val downloadUrls = locationInfo.map { it.downloadUrl }.distinct()

            // While for dependencies that have e.g. no sources artifact published it is completely valid to have an
            // empty remote artifact and to cache that result, for cases where none of the tried URLs ends with an
            // extension for a known packaging type probably some bug is the root cause for the artifact not being
            // found, and thus such results should not be cached, but retried.
            if (downloadUrls.any { url -> PACKAGING_TYPES.any { url.endsWith(".$it") } }) {
                logger.debug { "Writing empty remote artifact for '$artifact' to disk cache." }

                remoteArtifactCache.write(cacheKey, remoteArtifact.toYaml())
            } else {
                logger.warn { "Could not find artifact $artifact in any of $downloadUrls." }
            }
        }
    }

    /**
     * Create a [Package] for the given [artifact] which is searched for in the list of [repositories]. If
     * [useReposFromDependencies] is true, repositories declared in dependencies are also searched as a fallback. The
     * [localProjects] map contains local [MavenProject]s associated by their identifier for which no remote
     * repositories will be queried, but the VCS working tree information will be used to create the [Package]. If
     * [sbtMode] is enabled, it is assumed that the POM files referenced from the [localProjects] were generated by
     * "sbt makePom" and are therefore located below a "target" subdirectory of the actual project.
     */
    fun parsePackage(
        artifact: Artifact,
        repositories: List<RemoteRepository>,
        useReposFromDependencies: Boolean = true,
        localProjects: Map<String, MavenProject> = emptyMap(),
        sbtMode: Boolean = false
    ): Package {
        val mavenRepositorySystem = containerLookup<MavenRepositorySystem>()
        val projectBuilder = containerLookup<ProjectBuilder>()
        val projectBuildingRequest = createProjectBuildingRequest(false)

        projectBuildingRequest.remoteRepositories = repositories.map { repo ->
            // As the ID might be used as the key when generating a metadata file name, avoid the URL being used as the
            // ID as the URL is likely to contain characters like ":" which not all file systems support.
            val id = repo.id.takeUnless { it == repo.url } ?: repo.host
            mavenRepositorySystem.createRepository(repo.url, id, true, null, true, null, null)
        } + projectBuildingRequest.remoteRepositories

        val localProject = localProjects[artifact.identifier()]

        val mavenProject = localProject?.also {
            logger.info { "'${artifact.identifier()}' refers to a local project." }
        } ?: artifact.let {
            val pomArtifact = mavenRepositorySystem
                .createArtifact(it.groupId, it.artifactId, it.version, "", "pom")

            try {
                wrapMavenSession {
                    projectBuilder.build(pomArtifact, projectBuildingRequest).project
                }
            } catch (e: ProjectBuildingException) {
                e.showStackTrace()

                val failedProject = e.results?.find { projectBuildingResult ->
                    projectBuildingResult.projectId == it.identifier()
                }

                if (failedProject != null) {
                    logger.warn {
                        "There was an error building '${it.identifier()}', continuing with the incompletely built " +
                                "project: ${e.collectMessages()}"
                    }
                    failedProject.project
                } else {
                    logger.error { "Failed to build '${it.identifier()}': ${e.collectMessages()}" }
                    throw e
                }
            }
        }

        val declaredLicenses = parseLicenses(mavenProject)
        val declaredLicensesProcessed = processDeclaredLicenses(declaredLicenses)

        val binaryRemoteArtifact = localProject?.let {
            RemoteArtifact.EMPTY
        } ?: requestRemoteArtifact(artifact, repositories, useReposFromDependencies)

        val isBinaryArtifactModified = isArtifactModified(artifact, binaryRemoteArtifact)

        val sourceRemoteArtifact = when {
            localProject != null -> RemoteArtifact.EMPTY
            artifact.extension == "pom" -> binaryRemoteArtifact
            else -> {
                val sourceArtifact = artifact.let {
                    DefaultArtifact(it.groupId, it.artifactId, "sources", "jar", it.version)
                }

                requestRemoteArtifact(sourceArtifact, repositories, useReposFromDependencies)
            }
        }

        val isSourceArtifactModified = isArtifactModified(artifact, sourceRemoteArtifact)

        val vcsFromPackage = parseVcsInfo(mavenProject)
        val localDirectory = localProject?.file?.parentFile?.let {
            // TODO: Once SBT is implemented independently of Maven we can completely remove the "localProjects"
            //       parameter to this function as no other caller is actually using it.
            if (sbtMode) {
                it.searchUpwardsForSubdirectory("target") ?: it
            } else {
                it
            }
        }

        val browsableScmUrl = getOriginalScm(mavenProject)?.url
        val homepageUrl = mavenProject.url
        val vcsFallbackUrls = listOfNotNull(browsableScmUrl, homepageUrl).toTypedArray()

        val vcsProcessed = localDirectory?.let {
            PackageManager.processProjectVcs(it, vcsFromPackage, *vcsFallbackUrls)
        } ?: PackageManager.processPackageVcs(vcsFromPackage, *vcsFallbackUrls)

        val isSpringMetadataProject = with(mavenProject) {
            listOf("boot", "cloud").any {
                groupId == "org.springframework.$it" && (
                        artifactId.startsWith("spring-$it-starter") ||
                        artifactId.startsWith("spring-$it-contract-spec")
                )
            }
        }

        return Package(
            id = Identifier(
                type = "Maven",
                namespace = mavenProject.groupId,
                name = mavenProject.artifactId,
                version = mavenProject.version
            ),
            authors = parseAuthors(mavenProject),
            declaredLicenses = declaredLicenses,
            declaredLicensesProcessed = declaredLicensesProcessed,
            description = mavenProject.description.orEmpty(),
            homepageUrl = homepageUrl.orEmpty(),
            binaryArtifact = binaryRemoteArtifact,
            sourceArtifact = sourceRemoteArtifact,
            vcs = vcsFromPackage,
            vcsProcessed = vcsProcessed,
            isMetadataOnly = (mavenProject.packaging == "pom" && binaryRemoteArtifact.url.endsWith(".pom"))
                    || isSpringMetadataProject,
            isModified = isBinaryArtifactModified || isSourceArtifactModified
        )
    }

    /**
     * Create a [MavenSession] and setup the [LegacySupport] and [SessionScope] because this is required to load
     * extensions using Maven Wagon.
     */
    private fun <R> wrapMavenSession(block: () -> R): R {
        val request = DefaultMavenExecutionRequest()
        val result = DefaultMavenExecutionResult()

        @Suppress("DEPRECATION")
        val mavenSession = MavenSession(container, repositorySystemSession, request, result)

        val legacySupport = containerLookup<LegacySupport>()
        legacySupport.session = mavenSession

        val sessionScope = containerLookup<SessionScope>()
        sessionScope.enter()

        try {
            sessionScope.seed(MavenSession::class.java, mavenSession)
            return block()
        } finally {
            sessionScope.exit()
            legacySupport.session = null
        }
    }
}

/**
 * Several Maven repositories have disabled HTTP access and require HTTPS now. To be able to still analyze old Maven
 * projects that use the HTTP URLs, this [MirrorSelector] implementation automatically creates an HTTPS mirror if a
 * [RemoteRepository] uses a disabled HTTP URL. Without that Maven would abort with an exception as soon as it tries to
 * download an Artifact from any of those repositories.
 *
 * **See also:**
 *
 * [GitHub Security Lab issue](https://github.com/github/security-lab/issues/21)
 * [Medium article](https://medium.com/p/d069d253fe23)
 */
private class HttpsMirrorSelector(private val originalMirrorSelector: MirrorSelector?) : MirrorSelector {
    companion object : Logging {
        private val DISABLED_HTTP_REPOSITORY_URLS = listOf(
            "http://jcenter.bintray.com",
            "http://repo.maven.apache.org",
            "http://repo1.maven.org",
            "http://repo.spring.io"
        )
    }

    override fun getMirror(repository: RemoteRepository?): RemoteRepository? {
        originalMirrorSelector?.getMirror(repository)?.let { return it }

        if (repository == null || DISABLED_HTTP_REPOSITORY_URLS.none { repository.url.startsWith(it) }) return null

        logger.debug {
            "HTTP access to ${repository.id} (${repository.url}) was disabled. Automatically switching to HTTPS."
        }

        return RemoteRepository.Builder(
            "${repository.id}-https-mirror",
            repository.contentType,
            "https://${repository.url.removePrefix("http://")}"
        ).apply {
            setRepositoryManager(false)
            setSnapshotPolicy(repository.getPolicy(true))
            setReleasePolicy(repository.getPolicy(false))
            setMirroredRepositories(listOf(repository))
        }.build()
    }
}

/**
 * A specialized [WorkspaceReader] implementation used when building a Maven project that prevents unnecessary
 * downloads of binary artifacts.
 *
 * When building a Maven project from a POM using Maven's [ProjectBuilder] API clients have no control over the
 * downloads of dependencies: If dependencies are to be resolved, all the artifacts of these dependencies are
 * automatically downloaded. For the purpose of just constructing the dependency tree, this is not needed and only
 * costs time and bandwidth.
 *
 * Unfortunately, there is no official API to prevent the download of dependencies. However, Maven can be tricked to
 * believe that the artifacts are already present on the local disk - then the download is skipped. This is what
 * this implementation does. It reports that all binary artifacts are available locally, and only treats POMs
 * correctly, as they may be required for the dependency analysis.
 */
private class SkipBinaryDownloadsWorkspaceReader(
    /** The real workspace reader to delegate to. */
    val delegate: WorkspaceReader
) : WorkspaceReader by delegate {
    /**
     * Locate the given artifact on the local disk. This implementation does a correct location only for POM files;
     * for all other artifacts it returns a non-null file. Note: For the purpose of analyzing the project's
     * dependencies the artifact files are never accessed. Therefore, the concrete file returned here does not
     * actually matter; it just has to be non-null to indicate that the artifact is present locally.
     */
    override fun findArtifact(artifact: Artifact): File? {
        return if (artifact.extension == "pom") {
            delegate.findArtifact(artifact)
        } else {
            File(artifact.artifactId)
        }
    }
}
