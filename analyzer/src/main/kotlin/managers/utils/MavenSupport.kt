/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2021 Bosch.IO GmbH
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

import com.fasterxml.jackson.module.kotlin.readValue

import java.io.File
import java.util.regex.Pattern

import org.apache.logging.log4j.Level
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
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutProvider
import org.eclipse.aether.spi.connector.transport.GetTask
import org.eclipse.aether.spi.connector.transport.TransporterProvider
import org.eclipse.aether.transfer.AbstractTransferListener
import org.eclipse.aether.transfer.NoRepositoryConnectorException
import org.eclipse.aether.transfer.NoRepositoryLayoutException
import org.eclipse.aether.transfer.TransferEvent
import org.eclipse.aether.util.repository.JreProxySelector

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.utils.parseRepoManifestPath
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.utils.common.DiskCache
import org.ossreviewtoolkit.utils.common.collectMessagesAsString
import org.ossreviewtoolkit.utils.common.isMavenCentralUrl
import org.ossreviewtoolkit.utils.common.searchUpwardsForSubdirectory
import org.ossreviewtoolkit.utils.common.withoutPrefix
import org.ossreviewtoolkit.utils.core.DeclaredLicenseProcessor
import org.ossreviewtoolkit.utils.core.OkHttpClientHelper
import org.ossreviewtoolkit.utils.core.ProcessedDeclaredLicense
import org.ossreviewtoolkit.utils.core.installAuthenticatorAndProxySelector
import org.ossreviewtoolkit.utils.core.log
import org.ossreviewtoolkit.utils.core.logOnce
import org.ossreviewtoolkit.utils.core.ortDataDirectory
import org.ossreviewtoolkit.utils.core.showStackTrace
import org.ossreviewtoolkit.utils.spdx.SpdxOperator

fun Artifact.identifier() = "$groupId:$artifactId:$version"

class MavenSupport(private val workspaceReader: WorkspaceReader) {
    companion object {
        private const val MAX_DISK_CACHE_SIZE_IN_BYTES = 1024L * 1024L * 1024L
        private const val MAX_DISK_CACHE_ENTRY_AGE_SECONDS = 6 * 60 * 60

        // See http://maven.apache.org/pom.html#SCM.
        private val SCM_REGEX = Pattern.compile("scm:(?<type>[^:@]+):(?<url>.+)")!!
        private val USER_HOST_REGEX = Pattern.compile("scm:(?<user>[^:@]+)@(?<host>[^:]+):(?<url>.+)")!!

        private val remoteArtifactCache =
            DiskCache(
                ortDataDirectory.resolve("cache/remote_artifacts"),
                MAX_DISK_CACHE_SIZE_IN_BYTES, MAX_DISK_CACHE_ENTRY_AGE_SECONDS
            )

        private fun createContainer(): PlexusContainer {
            val configuration = DefaultContainerConfiguration().apply {
                autoWiring = true
                classPathScanning = PlexusConstants.SCANNING_INDEX
                classWorld = ClassWorld("plexus.core", javaClass.classLoader)
            }

            return DefaultPlexusContainer(configuration).apply {
                loggerManager = object : BaseLoggerManager() {
                    override fun createLogger(name: String) = MavenLogger(log.delegate.level)
                }
            }
        }

        fun parseAuthors(mavenProject: MavenProject) =
            sortedSetOf<String>().apply {
                mavenProject.organization?.let {
                    if (!it.name.isNullOrEmpty()) add(it.name)
                }

                val developers = mavenProject.developers.mapNotNull { it.organization.orEmpty().ifEmpty { it.name } }
                addAll(developers)
            }

        fun parseLicenses(mavenProject: MavenProject) =
            mavenProject.licenses.mapNotNull { license ->
                license.name ?: license.url ?: license.comments
            }.toSortedSet()

        fun processDeclaredLicenses(licenses: Set<String>): ProcessedDeclaredLicense =
            // See http://maven.apache.org/ref/3.6.3/maven-model/maven.html#project which says: "If multiple licenses
            // are listed, it is assumed that the user can select any of them, not that they must accept all."
            DeclaredLicenseProcessor.process(licenses, operator = SpdxOperator.OR)

        fun parseVcsInfo(mavenProject: MavenProject) = parseScm(getOriginalScm(mavenProject))

        /**
         * When asking Maven for the SCM URL of a POM that does not itself define an SCM URL, Maven returns the SCM
         * URL of the first parent POM (if any) that defines one and appends the artifactIds of all child POMs to it,
         * separated by slashes.
         * This behavior is fundamentally broken because it invalidates the SCM URL for all VCS that cannot limit
         * cloning to a specific path within a repository, or use a different syntax for that. Also, the assumption
         * that the source code for a child artifact is stored in a top-level directory named like the artifactId
         * inside the parent artifact's repository is often not correct.
         * To address this, determine the SCM URL of the parent (if any) that is closest to the root POM and whose
         * SCM URL still is a prefix of the child POM's SCM URL.
         */
        fun getOriginalScm(mavenProject: MavenProject): Scm? {
            var scm = mavenProject.scm
            var parent = mavenProject.parent

            while (parent != null) {
                parent.scm?.let { parentScm ->
                    parentScm.connection?.let { parentConnection ->
                        if (parentConnection.isNotBlank() && scm.connection.startsWith(parentConnection)) {
                            scm = parentScm
                        }
                    }
                }

                parent = parent.parent
            }

            return scm
        }

        private fun parseScm(scm: Scm?): VcsInfo {
            val connection = scm?.connection.orEmpty()
            val tag = scm?.tag?.takeIf { it != "HEAD" }.orEmpty()

            if (connection.isEmpty()) return VcsInfo.EMPTY

            return SCM_REGEX.matcher(connection).let { matcher ->
                if (matcher.matches()) {
                    val type = matcher.group("type")
                    val url = matcher.group("url")

                    when {
                        // CVS URLs usually start with ":pserver:" or ":ext:", but as ":" is also the delimiter used by
                        // the Maven SCM plugin, no double ":" is used in the connection string and we need to fix it up
                        // here.
                        type == "cvs" && !url.startsWith(":") -> {
                            VcsInfo(type = VcsType.CVS, url = ":$url", revision = tag)
                        }

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
                                log.info { "Fixed up invalid SCM connection '$connection' without a provider to $it." }
                            }
                        }

                        else -> {
                            VcsHost.fromUrl(url)?.let { host ->
                                host.toVcsInfo(url)?.let { vcsInfo ->
                                    // Fixup paths that are specified as part of the URL and contain the project name as
                                    // a prefix.
                                    val projectPrefix = "${host.getProject(url)}-"
                                    vcsInfo.path.withoutPrefix(projectPrefix)?.let { path ->
                                        vcsInfo.copy(path = path)
                                    }
                                }
                            } ?: VcsInfo(type = VcsType(type), url = url, revision = tag)
                        }
                    }
                } else {
                    val userHostMatcher = USER_HOST_REGEX.matcher(connection)

                    if (userHostMatcher.matches()) {
                        // Some projects omit the provider and use the SCP-like Git URL syntax, for example
                        // "scm:git@github.com:facebook/facebook-android-sdk.git".
                        val host = userHostMatcher.group("host")
                        val url = userHostMatcher.group("url")

                        VcsInfo(type = VcsType.GIT, url = "https://$host/$url", revision = tag)
                    } else if (connection.startsWith("git://") || connection.endsWith(".git")) {
                        // It is a common mistake to omit the "scm:[provider]:" prefix. Add fall-backs for nevertheless
                        // clear cases.
                        log.info { "Maven SCM connection URL '$connection' lacks the required 'scm' prefix." }

                        VcsInfo(type = VcsType.GIT, url = connection, revision = tag)
                    } else {
                        log.info { "Ignoring Maven SCM connection URL '$connection' of unexpected format." }

                        VcsInfo.EMPTY
                    }
                }
            }
        }

        /**
         * Trim the data from checksum files as it sometimes contains a path after the actual checksum.
         */
        private fun trimChecksumData(checksum: String) = checksum.trimStart().takeWhile { !it.isWhitespace() }

        /**
         * Return true if an artifact that has not been requested from Maven Central is also available on Maven Central
         * but with a different hash, otherwise return false.
         */
        private fun isArtifactModified(artifact: Artifact, remoteArtifact: RemoteArtifact): Boolean {
            if (remoteArtifact.url.isBlank() || isMavenCentralUrl(remoteArtifact.url)) return false

            val mavenCentralUrl = with(artifact) {
                val group = groupId.replace('.', '/')
                val name = remoteArtifact.url.substringAfterLast('/')
                val hash = remoteArtifact.hash.algorithm.name.lowercase()
                "https://repo.maven.apache.org/maven2/$group/$artifactId/$version/$name.$hash"
            }

            val checksum = OkHttpClientHelper.downloadText(mavenCentralUrl).getOrNull() ?: return false
            return !trimChecksumData(checksum).equals(remoteArtifact.hash.value, ignoreCase = true)
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
            installAuthenticatorAndProxySelector()
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

            log.warn {
                "There have been issues building the Maven project models, this could lead to errors during " +
                        "dependency analysis: ${e.collectMessagesAsString()}"
            }

            e.results
        }

        val result = mutableMapOf<String, ProjectBuildingResult>()

        projectBuildingResults.forEach { projectBuildingResult ->
            if (projectBuildingResult.project == null) {
                log.warn {
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
                log.warn {
                    "There was an error building project '${e.projectId}' at '${e.pomFile.invariantSeparatorsPath}'. " +
                            "Still continuing with the incompletely built project '${resultForPomFile.projectId}' at " +
                            "'${resultForPomFile.pomFile.invariantSeparatorsPath}': ${e.collectMessagesAsString()}"
                }

                resultForPomFile
            } else {
                log.error {
                    "Failed to build project '${e.projectId}' at '${e.pomFile.invariantSeparatorsPath}': " +
                            e.collectMessagesAsString()
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

    private fun requestRemoteArtifact(artifact: Artifact, repositories: List<RemoteRepository>): RemoteArtifact {
        remoteArtifactCache.read(artifact.toString())?.let {
            log.debug { "Reading remote artifact for '$artifact' from disk cache." }
            return yamlMapper.readValue(it)
        }

        val repoSystem = containerLookup<RepositorySystem>()
        val remoteRepositoryManager = containerLookup<RemoteRepositoryManager>()
        val repositoryLayoutProvider = containerLookup<RepositoryLayoutProvider>()
        val repositoryConnectorProvider = containerLookup<RepositoryConnectorProvider>()
        val transporterProvider = containerLookup<TransporterProvider>()

        // Create an artifact descriptor to get the list of repositories from the related POM file.
        val artifactDescriptorRequest = ArtifactDescriptorRequest(artifact, repositories, "project")
        val artifactDescriptorResult = repoSystem
            .readArtifactDescriptor(repositorySystemSession, artifactDescriptorRequest)
        val allRepositories = (artifactDescriptorResult.repositories + repositories).toSet()

        // Filter out local repositories, as remote artifacts should never point to files on the local disk.
        val remoteRepositories = allRepositories.filterNot {
            // Some (Linux) file URIs do not start with "file://" but look like "file:/opt/android-sdk-linux".
            it.url.startsWith("file:/")
        }.map { repository ->
            val proxy = repositorySystemSession.proxySelector.getProxy(repository)
            RemoteRepository.Builder(repository).setProxy(proxy).build()
        }

        if (log.delegate.isDebugEnabled) {
            val localRepositories = allRepositories - remoteRepositories
            if (localRepositories.isNotEmpty()) {
                // No need to use curly-braces-syntax for logging here as the log level check is already done above.
                log.debug { "Ignoring local repositories $localRepositories." }
            }
        }

        log.debug { "Searching for '$artifact' in $remoteRepositories." }

        // Check the remote repositories for the availability of the artifact.
        // TODO: Currently only the first hit is stored, could query the rest of the repositories if required.
        remoteRepositories.forEach { repository ->
            val repositoryLayout = try {
                repositoryLayoutProvider.newRepositoryLayout(repositorySystemSession, repository)
            } catch (e: NoRepositoryLayoutException) {
                e.showStackTrace()

                log.warn { "Could not search for '$artifact' in '$repository': ${e.collectMessagesAsString()}" }

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
                    MavenSupport.log.debug { "Transfer failed: $event" }
                }

                override fun transferSucceeded(event: TransferEvent?) {
                    MavenSupport.log.debug { "Transfer succeeded: $event" }
                }
            }

            try {
                wrapMavenSession {
                    repositoryConnectorProvider.newRepositoryConnector(repositorySystemSession, repository).use {
                        it.get(listOf(artifactDownload), null)
                    }
                }
            } catch (e: NoRepositoryConnectorException) {
                e.showStackTrace()

                log.warn { "Could not create connector for repository '$repository': ${e.collectMessagesAsString()}" }

                return@forEach
            }

            if (artifactDownload.exception == null) {
                log.debug { "Found '$artifact' in '$repository'." }

                val checksums = repositoryLayout.getChecksums(artifact, false, remoteLocation)
                log.debug { "Checksums: $checksums" }

                // TODO: Could store multiple checksums in model instead of only the first.
                val checksum = checksums.first()

                val transporter = transporterProvider.newTransporter(repositorySystemSession, repository)

                val actualChecksum = runCatching {
                    val task = GetTask(checksum.location)
                    transporter.get(task)

                    trimChecksumData(task.dataString)
                }.getOrElse {
                    it.showStackTrace()

                    log.warn { "Could not get checksum for '$artifact': ${it.collectMessagesAsString()}" }

                    // Fall back to an empty checksum string.
                    ""
                }

                val downloadUrl = "${repository.url.trimEnd('/')}/$remoteLocation"
                val hash = if (actualChecksum.isBlank()) Hash.NONE else Hash.create(actualChecksum, checksum.algorithm)
                return RemoteArtifact(downloadUrl, hash).also {
                    log.debug { "Writing remote artifact for '$artifact' to disk cache." }
                    remoteArtifactCache.write(artifact.toString(), yamlMapper.writeValueAsString(it))
                }
            } else {
                log.debug {
                    "Could not find '$artifact' in '$repository': " +
                            artifactDownload.exception.collectMessagesAsString()
                }
            }
        }

        val level = if (artifact.classifier == "sources") Level.DEBUG else Level.WARN
        log.log(level) { "Unable to find '$artifact' in any of ${remoteRepositories.map { it.url }}." }

        return RemoteArtifact.EMPTY.also {
            log.debug { "Writing empty remote artifact for '$artifact' to disk cache." }
            remoteArtifactCache.write(artifact.toString(), yamlMapper.writeValueAsString(it))
        }
    }

    /**
     * Create a [Package] for the given [artifact] which is searched for in the list of [repositories]. [localProjects]
     * contains local [MavenProject]s associated by their identifier for which no remote repositories will be queried,
     * but the VCS working tree information will be used to create the [Package]. If [sbtMode] is enabled, it is assumed
     * that the POM files referenced from the [localProjects] were generated by "sbt makePom" and are therefore located
     * below a "target" subdirectory of the actual project.
     */
    fun parsePackage(
        artifact: Artifact, repositories: List<RemoteRepository>,
        localProjects: Map<String, MavenProject> = emptyMap(), sbtMode: Boolean = false
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
            log.info { "'${artifact.identifier()}' refers to a local project." }
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
                    log.warn {
                        "There was an error building '${it.identifier()}', continuing with the incompletely built " +
                                "project: ${e.collectMessagesAsString()}"
                    }
                    failedProject.project
                } else {
                    log.error { "Failed to build '${it.identifier()}': ${e.collectMessagesAsString()}" }
                    throw e
                }
            }
        }

        val declaredLicenses = parseLicenses(mavenProject)
        val declaredLicensesProcessed = processDeclaredLicenses(declaredLicenses)

        val binaryRemoteArtifact = localProject?.let {
            RemoteArtifact.EMPTY
        } ?: requestRemoteArtifact(artifact, repositories)

        val isBinaryArtifactModified = isArtifactModified(artifact, binaryRemoteArtifact)

        val sourceRemoteArtifact = when {
            localProject != null -> RemoteArtifact.EMPTY
            artifact.extension == "pom" -> binaryRemoteArtifact
            else -> {
                val sourceArtifact = artifact.let {
                    DefaultArtifact(it.groupId, it.artifactId, "sources", "jar", it.version)
                }

                requestRemoteArtifact(sourceArtifact, repositories)
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

        val isSpringStarterProject = with(mavenProject) {
            listOf("boot", "cloud").any {
                groupId == "org.springframework.$it" && artifactId.startsWith("spring-$it-starter-")
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
            isMetaDataOnly = mavenProject.packaging == "pom" || isSpringStarterProject,
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
    companion object {
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

        logOnce(Level.INFO) {
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
     * actually matter; it just have to be non-null to indicate that the artifact is present locally.
     */
    override fun findArtifact(artifact: Artifact): File? {
        return if (artifact.extension == "pom") {
            delegate.findArtifact(artifact)
        } else {
            File(artifact.artifactId)
        }
    }
}
