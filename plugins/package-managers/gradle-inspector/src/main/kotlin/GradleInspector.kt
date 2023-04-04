/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.gradleinspector

import OrtDependency
import OrtDependencyTreeModel

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.SortedSet
import java.util.concurrent.TimeUnit

import org.apache.logging.log4j.kotlin.Logging

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.internal.consumer.DefaultGradleConnector

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.utils.parseRepoManifestPath
import org.ossreviewtoolkit.utils.common.splitOnWhitespace
import org.ossreviewtoolkit.utils.common.withoutPrefix
import org.ossreviewtoolkit.utils.ort.DeclaredLicenseProcessor
import org.ossreviewtoolkit.utils.ort.OkHttpClientHelper
import org.ossreviewtoolkit.utils.ort.createOrtTempFile
import org.ossreviewtoolkit.utils.spdx.SpdxOperator

/**
 * The names of Gradle (Groovy, Kotlin script) build files for a Gradle project.
 */
private val GRADLE_BUILD_FILES = listOf("build.gradle", "build.gradle.kts")

/**
 * The names of Gradle (Groovy, Kotlin script) settings files for a Gradle build.
 */
private val GRADLE_SETTINGS_FILES = listOf("settings.gradle", "settings.gradle.kts")

/**
 * The name of the option to specify the Gradle version.
 */
const val OPTION_GRADLE_VERSION = "gradleVersion"

/**
 * The [Gradle](https://gradle.org/) package manager for Java.
 *
 * This package manager supports the following [options][PackageManagerConfiguration.options]:
 * - *gradleVersion*: The version of Gradle to use when analyzing projects. Defaults to the version defined in the
 *   Gradle wrapper properties.
 */
class GradleInspector(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig) {
    internal companion object : Logging

    class Factory : AbstractPackageManagerFactory<GradleInspector>("GradleInspector", isEnabledByDefault = false) {
        // Gradle prefers Groovy ".gradle" files over Kotlin ".gradle.kts" files, but "build" files have to come before
        // "settings" files as we should consider "settings" files only if the same directory does not also contain a
        // "build" file.
        override val globsForDefinitionFiles = GRADLE_BUILD_FILES + GRADLE_SETTINGS_FILES

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = GradleInspector(type, analysisRoot, analyzerConfig, repoConfig)
    }

    private fun extractInitScript(): File {
        fun extractResource(name: String, target: File) = target.apply {
            val resource = checkNotNull(GradleInspector::class.java.getResource(name)) {
                "Resource '$name' not found."
            }

            logger.debug { "Extracting resource '${resource.path.substringAfterLast('/')}' to '$target'..." }

            resource.openStream().use { inputStream ->
                outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }

        val pluginJar = extractResource("/gradle-plugin.jar", createOrtTempFile(prefix = "plugin", suffix = ".jar"))

        val initScriptText = javaClass.getResource("/init.gradle.template").readText()
            .replace("<REPLACE_PLUGIN_JAR>", pluginJar.invariantSeparatorsPath)

        val initScript = createOrtTempFile("init", ".gradle")

        logger.debug { "Extracting Gradle init script to '$initScript'..." }

        return initScript.apply { writeText(initScriptText) }
    }

    private fun GradleConnector.getOrtDependencyTreeModel(projectDir: File): OrtDependencyTreeModel =
        forProjectDirectory(projectDir).connect().use { connection ->
            val initScriptFile = extractInitScript()

            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()

            // In order to debug the plugin, pass the "-Dorg.gradle.debug=true" option to the JVM running ORT. This will
            // then block execution of the plugin until a remote debug session is attached to port 5005 (by default),
            // also see https://docs.gradle.org/current/userguide/troubleshooting.html#sec:troubleshooting_build_logic.
            val model = connection.model(OrtDependencyTreeModel::class.java)
                .addProgressListener(ProgressListener { logger.debug { it.displayName } })
                .setStandardOutput(stdout)
                .setStandardError(stderr)
                .withArguments("--init-script", initScriptFile.path)
                .get()

            if (stdout.size() > 0) {
                logger.debug {
                    "Analyzing the project in '$projectDir' produced the following standard output:\n" +
                            stdout.toString().prependIndent("\t")
                }
            }

            if (stderr.size() > 0) {
                logger.warn {
                    "Analyzing the project in '$projectDir' produced the following error output:\n" +
                            stderr.toString().prependIndent("\t")
                }
            }

            if (!initScriptFile.delete()) {
                logger.warn { "Init script file '$initScriptFile' could not be deleted." }
            }

            model
        }

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        val projectDir = definitionFile.parentFile

        val gradleConnector = GradleConnector.newConnector()

        val gradleVersion = options[OPTION_GRADLE_VERSION]
        if (gradleVersion != null) {
            gradleConnector.useGradleVersion(gradleVersion)
        }

        if (gradleConnector is DefaultGradleConnector) {
            // Note that the Gradle Tooling API always uses the Gradle daemon, see
            // https://docs.gradle.org/current/userguide/third_party_integration.html#sec:embedding_daemon.
            gradleConnector.daemonMaxIdleTime(1, TimeUnit.SECONDS)
        }

        val dependencyTreeModel = gradleConnector.getOrtDependencyTreeModel(projectDir)

        val issues = mutableListOf<Issue>()

        dependencyTreeModel.errors.distinct().mapTo(issues) {
            createAndLogIssue(source = managerName, message = it, severity = Severity.ERROR)
        }

        dependencyTreeModel.warnings.distinct().mapTo(issues) {
            createAndLogIssue(source = managerName, message = it, severity = Severity.WARNING)
        }

        val projectId = Identifier(
            type = managerName,
            namespace = dependencyTreeModel.group,
            name = dependencyTreeModel.name,
            version = dependencyTreeModel.version
        )

        val packageDependencies = mutableSetOf<OrtDependency>()

        val scopes = dependencyTreeModel.configurations.filterNot {
            excludes.isScopeExcluded(it.name)
        }.mapTo(sortedSetOf()) {
            Scope(name = it.name, dependencies = it.dependencies.toPackageRefs(packageDependencies))
        }

        val project = Project(
            id = projectId,
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
            authors = emptySet(),
            declaredLicenses = emptySet(),
            vcs = VcsInfo.EMPTY,
            vcsProcessed = processProjectVcs(definitionFile.parentFile),
            homepageUrl = "",
            scopeDependencies = scopes
        )

        val packages = packageDependencies.associateBy {
            // Deduplicate OrtDependency serialization proxy objects by Identifier.
            Identifier("Maven", it.groupId, it.artifactId, it.version)
        }.mapNotNullTo(mutableSetOf()) { (id, dep) ->
            val model = dep.mavenModel ?: run {
                issues += createAndLogIssue(
                    source = "Gradle",
                    message = "No Maven model available for '${id.toCoordinates()}'."
                )

                return@mapNotNullTo Package.EMPTY.copy(id = id)
            }

            val vcs = dep.toVcsInfo()
            val vcsFallbackUrls = listOfNotNull(model.vcs?.browsableUrl, model.homepageUrl).toTypedArray()
            val vcsProcessed = processPackageVcs(vcs, *vcsFallbackUrls)

            Package(
                id = id,
                authors = model.authors,
                declaredLicenses = model.licenses,
                declaredLicensesProcessed = DeclaredLicenseProcessor.process(
                    model.licenses,
                    // See http://maven.apache.org/ref/3.6.3/maven-model/maven.html#project saying: "If multiple
                    // licenses are listed, it is assumed that the user can select any of them, not that they must
                    // accept all."
                    operator = SpdxOperator.OR
                ),
                description = model.description.orEmpty(),
                homepageUrl = model.homepageUrl.orEmpty(),
                binaryArtifact = createRemoteArtifact(dep.pomFile, dep.classifier, dep.extension),
                sourceArtifact = createRemoteArtifact(dep.pomFile, "sources", "jar"),
                vcs = vcs,
                vcsProcessed = vcsProcessed
            )
        }

        val result = ProjectAnalyzerResult(project, packages, issues)
        return listOf(result)
    }
}

/**
 * Recursively convert a collection of [OrtDependency] objects to a set of [PackageReference] objects for use in [Scope]
 * while flattening all dependencies into the [packageDependencies] collection.
 */
private fun Collection<OrtDependency>.toPackageRefs(
    packageDependencies: MutableCollection<OrtDependency>
): SortedSet<PackageReference> =
    mapTo(sortedSetOf()) { dep ->
        val (id, linkage) = if (dep.localPath != null) {
            val id = Identifier("Gradle", dep.groupId, dep.artifactId, dep.version)
            id to PackageLinkage.PROJECT_DYNAMIC
        } else {
            packageDependencies += dep

            val id = Identifier("Maven", dep.groupId, dep.artifactId, dep.version)
            id to PackageLinkage.DYNAMIC
        }

        PackageReference(id, linkage, dep.dependencies.toPackageRefs(packageDependencies))
    }

/**
 * Create a [RemoteArtifact] based on the given [pomUrl], [classifier], [extension] and hash [algorithm]. The hash value
 * is retrieved remotely.
 */
private fun createRemoteArtifact(
    pomUrl: String?, classifier: String = "", extension: String = "jar", algorithm: String = "sha1"
): RemoteArtifact {
    val artifactBaseUrl = pomUrl?.removeSuffix(".pom") ?: return RemoteArtifact.EMPTY

    val artifactUrl = buildString {
        append(artifactBaseUrl)
        if (classifier.isNotEmpty()) append("-$classifier")
        if (extension.isNotEmpty()) append(".$extension")
    }

    // TODO: How to handle authentication for private repositories here, or rely on Gradle for the download?
    val checksum = OkHttpClientHelper.downloadText("$artifactUrl.$algorithm")
        .getOrElse { return RemoteArtifact.EMPTY }

    return RemoteArtifact(artifactUrl, parseChecksum(checksum, algorithm))
}

/**
 * Split the provided [checksum] by whitespace and return a [Hash] for the first element that matches the provided
 * algorithm. If no element matches, return [Hash.NONE]. This works around the issue that Maven checksum files sometimes
 * contain arbitrary strings before or after the actual checksum.
 */
private fun parseChecksum(checksum: String, algorithm: String) =
    checksum.splitOnWhitespace().firstNotNullOfOrNull {
        runCatching { Hash.create(it, algorithm) }.getOrNull()
    } ?: Hash.NONE

// See http://maven.apache.org/pom.html#SCM.
private val SCM_REGEX = Regex("scm:(?<type>[^:@]+):(?<url>.+)")
private val USER_HOST_REGEX = Regex("scm:(?<user>[^:@]+)@(?<host>[^:]+)[:/](?<path>.+)")

private fun OrtDependency.toVcsInfo() =
    mavenModel?.vcs?.run {
        SCM_REGEX.matchEntire(connection)?.let { match ->
            val type = match.groups["type"]!!.value
            val url = match.groups["url"]!!.value

            handleValidScmInfo(type, url, tag)
        } ?: handleInvalidScmInfo(connection, tag)
    } ?: VcsInfo.EMPTY

private fun OrtDependency.handleValidScmInfo(type: String, url: String, tag: String) =
    when {
        // CVS URLs usually start with ":pserver:" or ":ext:", but as ":" is also the delimiter used by the Maven SCM
        // plugin, no double ":" is used in the connection string, and we need to fix it up here.
        type == "cvs" && !url.startsWith(":") -> {
            VcsInfo(type = VcsType.CVS, url = ":$url", revision = tag)
        }

        // Maven does not officially support git-repo as an SCM, see http://maven.apache.org/scm/scms-overview.html, so
        // come up with the convention to use the "manifest" query parameter for the path to the manifest inside the
        // repository. An earlier version of this workaround expected the query string to be only the path to the
        // manifest, for backward compatibility convert such URLs to the new syntax.
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

            // Try to detect the Maven SCM provider from the URL only, e.g. by looking at the host or special URL paths.
            VcsHost.parseUrl(fixedUrl).copy(revision = tag).also {
                GradleInspector.logger.info {
                    "Fixed up invalid SCM connection without a provider in '$groupId:$artifactId:$version' to $it."
                }
            }
        }

        else -> {
            val trimmedUrl = if (!url.startsWith("git://")) url.removePrefix("git:") else url

            VcsHost.fromUrl(trimmedUrl)?.let { host ->
                host.toVcsInfo(trimmedUrl)?.let { vcsInfo ->
                    // Fixup paths that are specified as part of the URL and contain the project name as a prefix.
                    val projectPrefix = "${host.getProject(trimmedUrl)}-"
                    vcsInfo.path.withoutPrefix(projectPrefix)?.let { path ->
                        vcsInfo.copy(path = path)
                    }
                }
            } ?: VcsInfo(type = VcsType.forName(type), url = trimmedUrl, revision = tag)
        }
    }

private fun OrtDependency.handleInvalidScmInfo(connection: String, tag: String) =
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
        val dep = "$groupId:$artifactId:$version"

        if (connection.startsWith("git://") || connection.endsWith(".git")) {
            // It is a common mistake to omit the "scm:[provider]:" prefix. Add fall-backs for nevertheless clear
            // cases.
            GradleInspector.logger.info {
                "Maven SCM connection '$connection' in '$dep' lacks the required 'scm' prefix."
            }

            VcsInfo(type = VcsType.GIT, url = connection, revision = tag)
        } else {
            if (connection.isNotEmpty()) {
                GradleInspector.logger.info {
                    "Ignoring Maven SCM connection '$connection' in '$dep' due to an unexpected format."
                }
            }

            VcsInfo.EMPTY
        }
    }
