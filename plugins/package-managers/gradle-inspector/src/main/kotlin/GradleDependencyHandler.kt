/*
 * Copyright (C) 2024 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import OrtComponent
import OrtComponentIdentifier
import OrtComponentReference
import OrtDependencyTreeModel

import java.lang.invoke.MethodHandles

import org.apache.logging.log4j.kotlin.logger
import org.apache.logging.log4j.kotlin.loggerOf

import org.ossreviewtoolkit.analyzer.PackageManager.Companion.processPackageVcs
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.model.orNone
import org.ossreviewtoolkit.model.utils.DependencyHandler
import org.ossreviewtoolkit.model.utils.parseRepoManifestPath
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.splitOnWhitespace
import org.ossreviewtoolkit.utils.common.withoutPrefix
import org.ossreviewtoolkit.utils.ort.DeclaredLicenseProcessor
import org.ossreviewtoolkit.utils.ort.downloadText
import org.ossreviewtoolkit.utils.ort.okHttpClient
import org.ossreviewtoolkit.utils.spdxexpression.SpdxOperator

/**
 * A specialized [DependencyHandler] implementation for Gradle's dependency model.
 */
internal class GradleDependencyHandler(
    /** The type of projects to handle. */
    private val projectType: String
) : DependencyHandler<OrtComponentReference> {
    private val componentForId = mutableMapOf<OrtComponentIdentifier, OrtComponent>()

    private fun OrtComponentReference.component(): OrtComponent = componentForId.getValue(componentId)

    fun setTreeModel(treeModel: OrtDependencyTreeModel) {
        treeModel.components.associateByTo(componentForId) { it.componentId }
    }

    override fun identifierFor(dependency: OrtComponentReference): Identifier =
        with(dependency.componentId) {
            Identifier(
                type = dependency.component().getIdentifierType(projectType),
                namespace = groupId,
                name = artifactId,
                version = version
            )
        }

    override fun dependenciesFor(dependency: OrtComponentReference): List<OrtComponentReference> =
        dependency.dependencies

    override fun linkageFor(dependency: OrtComponentReference): PackageLinkage =
        if (dependency.component().isProjectDependency) PackageLinkage.PROJECT_DYNAMIC else PackageLinkage.DYNAMIC

    override fun createPackage(dependency: OrtComponentReference, issues: MutableCollection<Issue>): Package? {
        val component = dependency.component()

        // Only look for a package if there was no error resolving the dependency and it is no project dependency.
        if (component.error != null || component.isProjectDependency) return null

        val id = identifierFor(dependency)
        val model = component.mavenModel ?: run {
            issues += createAndLogIssue(
                source = GradleInspectorFactory.descriptor.displayName,
                message = "No Maven model available for '${id.toCoordinates()}'."
            )

            return null
        }

        val hasNoArtifacts = component.pomFile == null
        val isPomArtifact = component.extension == "pom"

        val isPlatformDependency = component.variants.keys.any {
            it.startsWith("platform-") || it.startsWith("enforced-platform-")
        }

        val isKotlinMultiPlatform = component.variants.values.any {
            "org.jetbrains.kotlin.platform.type" in it.keys
        }

        val binaryArtifact = when {
            hasNoArtifacts -> RemoteArtifact.EMPTY

            isKotlinMultiPlatform -> with(component) {
                // TODO: Support more than just "jvm" targets for KMP projects.
                createRemoteArtifact(pomFile, classifier, "jar")
            }

            else -> with(component) {
                createRemoteArtifact(pomFile, classifier, extension.takeUnless { it == "bundle" })
            }
        }

        val sourceArtifact = when {
            hasNoArtifacts -> RemoteArtifact.EMPTY
            isPomArtifact && !isKotlinMultiPlatform -> binaryArtifact
            else -> createRemoteArtifact(component.pomFile, "sources", "jar")
        }

        val vcs = component.toVcsInfo()
        val vcsFallbackUrls = listOfNotNull(model.vcs?.browsableUrl, model.homepageUrl).toTypedArray()
        val vcsProcessed = processPackageVcs(vcs, *vcsFallbackUrls)

        return Package(
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
            binaryArtifact = binaryArtifact,
            sourceArtifact = sourceArtifact,
            vcs = vcs,
            vcsProcessed = vcsProcessed,
            isMetadataOnly = hasNoArtifacts || (isPomArtifact && !isKotlinMultiPlatform) || isPlatformDependency
        )
    }

    override fun areDependenciesEqual(
        dependenciesA: List<OrtComponentReference>,
        dependenciesB: List<OrtComponentReference>
    ): Boolean {
        if (dependenciesA === dependenciesB) return true
        if (dependenciesA.isEmpty() && dependenciesB.isEmpty()) return true

        val depsA = dependenciesA.distinct()
        val depsB = dependenciesB.distinct()
        if (depsA.size != depsB.size) return false

        val idToDepA = depsA.associateBy { identifierFor(it) }
        val idToDepB = depsB.associateBy { identifierFor(it) }
        if (idToDepA.keys != idToDepB.keys) return false

        // Gradle guarantees dependencies to be same for Identifiers of the same variant, so exit early to skip deep
        // comparison of transitive dependencies in that case.
        val idVariantPairsA = idToDepA.map { (id, dep) -> id to dep.component().variants }
        val idVariantPairsB = idToDepB.map { (id, dep) -> id to dep.component().variants }
        if (idVariantPairsA == idVariantPairsB) return true

        return idToDepA.all { (id, depA) ->
            val depB = idToDepB[id] ?: return false
            areDependenciesEqual(dependenciesFor(depA), dependenciesFor(depB))
        }
    }

    /**
     * The type of this Gradle dependency. In case of a project, it is [projectType]. Otherwise it is "Maven" unless
     * there is no POM, then it is "Unknown".
     */
    fun OrtComponent.getIdentifierType(projectType: String) =
        when {
            isProjectDependency -> projectType
            pomFile != null -> "Maven"
            else -> "Unknown"
        }

    /**
     * A flag to indicate whether this Gradle dependency refers to a project, or to a package.
     */
    val OrtComponent.isProjectDependency: Boolean
        get() = localPath != null
}

// See http://maven.apache.org/pom.html#SCM.
private val SCM_REGEX = Regex("scm:(?<type>[^:@]+):(?<url>.+)")
private val USER_HOST_REGEX = Regex("scm:(?<user>[^:@]+)@(?<host>[^:]+)[:/](?<path>.+)")

private val logger = loggerOf(MethodHandles.lookup().lookupClass())

private fun OrtComponent.toVcsInfo(): VcsInfo =
    mavenModel?.vcs?.run {
        @Suppress("UnsafeCallOnNullableType")
        SCM_REGEX.matchEntire(connection)?.let { match ->
            val type = match.groups["type"]!!.value
            val url = match.groups["url"]!!.value

            handleValidScmInfo(type, url, tag)
        } ?: handleInvalidScmInfo(connection, tag)
    }.orEmpty()

private fun OrtComponent.handleValidScmInfo(type: String, url: String, tag: String): VcsInfo =
    when {
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
                with(componentId) {
                    logger.info {
                        "Fixed up invalid SCM connection without a provider in '$groupId:$artifactId:$version' to $it."
                    }
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

private fun OrtComponent.handleInvalidScmInfo(connection: String, tag: String): VcsInfo =
    @Suppress("UnsafeCallOnNullableType")
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
        val dep = with(componentId) { "$groupId:$artifactId:$version" }

        if (connection.startsWith("git://") || connection.endsWith(".git")) {
            // It is a common mistake to omit the "scm:[provider]:" prefix. Add fall-backs for nevertheless clear
            // cases.
            logger.info {
                "Maven SCM connection '$connection' in '$dep' lacks the required 'scm' prefix."
            }

            VcsInfo(type = VcsType.GIT, url = connection, revision = tag)
        } else {
            if (connection.isNotEmpty()) {
                logger.info {
                    "Ignoring Maven SCM connection '$connection' in '$dep' due to an unexpected format."
                }
            }

            VcsInfo.EMPTY
        }
    }

/**
 * Create a [RemoteArtifact] based on the given [pomUrl], [classifier] and [extension]. The hash value is retrieved
 * remotely.
 */
private fun createRemoteArtifact(
    pomUrl: String?,
    classifier: String? = null,
    extension: String? = null
): RemoteArtifact {
    val algorithm = "sha1"
    val artifactBaseUrl = pomUrl?.removeSuffix(".pom") ?: return RemoteArtifact.EMPTY

    val artifactUrl = buildString {
        append(artifactBaseUrl)
        if (!classifier.isNullOrEmpty()) append("-$classifier")
        if (!extension.isNullOrEmpty()) append(".$extension") else append(".jar")
    }

    // TODO: How to handle authentication for private repositories here, or rely on Gradle for the download?
    val hash = okHttpClient.downloadText("$artifactUrl.$algorithm")
        .mapCatching { checksum ->
            parseChecksum(checksum, algorithm).also {
                require(it.value != HashAlgorithm.SHA1.emptyValue) {
                    "Ignoring invalid artifact of zero size at $artifactUrl."
                }
            }
        }.getOrElse {
            logger.debug {
                "Unable to get a valid '$algorithm' checksum for the artifact at $artifactUrl: ${it.collectMessages()}"
            }

            Hash.NONE
        }

    return RemoteArtifact(artifactUrl, hash)
}

/**
 * Split the provided [checksum] by whitespace and return a [Hash] for the first element that matches the provided
 * algorithm. If no element matches, return [Hash.NONE]. This works around the issue that Maven checksum files sometimes
 * contain arbitrary strings before or after the actual checksum.
 */
private fun parseChecksum(checksum: String, algorithm: String) =
    checksum.splitOnWhitespace().firstNotNullOfOrNull {
        runCatching { Hash(it, algorithm) }.getOrNull()
    }.orNone()
