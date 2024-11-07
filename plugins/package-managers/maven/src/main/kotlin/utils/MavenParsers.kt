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

package org.ossreviewtoolkit.plugins.packagemanagers.maven.utils

import java.lang.invoke.MethodHandles

import org.apache.logging.log4j.kotlin.loggerOf
import org.apache.maven.model.Scm
import org.apache.maven.project.MavenProject

import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.utils.parseRepoManifestPath
import org.ossreviewtoolkit.utils.common.splitOnWhitespace
import org.ossreviewtoolkit.utils.common.withoutPrefix
import org.ossreviewtoolkit.utils.ort.DeclaredLicenseProcessor
import org.ossreviewtoolkit.utils.ort.ProcessedDeclaredLicense
import org.ossreviewtoolkit.utils.spdx.SpdxOperator

// See http://maven.apache.org/pom.html#SCM.
private val SCM_REGEX = Regex("scm:(?<type>[^:@]+):(?<url>.+)")
private val USER_HOST_REGEX = Regex("scm:(?<user>[^:@]+)@(?<host>[^:]+)[:/](?<path>.+)")

private val logger = loggerOf(MethodHandles.lookup().lookupClass())

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
internal fun getOriginalScm(mavenProject: MavenProject): Scm? {
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
                    vcsInfo.takeIf { "/" in it.path } ?: run {
                        // Fixup a single directory that is specified as part of the URL and contains the
                        // project name as a prefix.
                        val projectPrefix = "${host.getProject(trimmedUrl)}-"
                        vcsInfo.path.withoutPrefix(projectPrefix)?.let { path ->
                            vcsInfo.copy(path = path)
                        }
                    }
                }
            } ?: VcsInfo(type = VcsType.forName(type), url = trimmedUrl, revision = tag)
        }
    }

internal fun parseAuthors(mavenProject: MavenProject): Set<String> =
    buildSet {
        mavenProject.organization?.let {
            if (!it.name.isNullOrEmpty()) add(it.name)
        }

        val developers = mavenProject.developers.mapNotNull { it.organization.orEmpty().ifEmpty { it.name } }
        addAll(developers)
    }

/**
 * Split the provided [checksum] by whitespace and return a [Hash] for the first element that matches the
 * provided algorithm. If no element matches, return [Hash.NONE]. This works around the issue that Maven
 * checksum files sometimes contain arbitrary strings before or after the actual checksum.
 */
internal fun parseChecksum(checksum: String, algorithm: String) =
    checksum.splitOnWhitespace().firstNotNullOfOrNull {
        runCatching { Hash(it, algorithm) }.getOrNull()
    } ?: Hash.NONE

internal fun parseLicenses(mavenProject: MavenProject): Set<String> =
    mavenProject.licenses.mapNotNullTo(mutableSetOf()) { license ->
        listOfNotNull(license.name, license.url, license.comments).firstOrNull { it.isNotBlank() }
    }

internal fun parseVcsInfo(project: MavenProject): VcsInfo {
    val scm = getOriginalScm(project)
    val connection = scm?.connection
    if (connection.isNullOrEmpty()) return VcsInfo.EMPTY

    val tag = scm.tag?.takeIf { it != "HEAD" }.orEmpty()

    return SCM_REGEX.matchEntire(connection)?.let { match ->
        val (type, url) = match.destructured
        getVcsInfo(type, url, tag)
    } ?: run {
        USER_HOST_REGEX.matchEntire(connection)?.let { match ->
            // Some projects omit the provider and use the SCP-like Git URL syntax, for example
            // "scm:git@github.com:facebook/facebook-android-sdk.git".
            val (user, host, path) = match.destructured

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

internal fun processDeclaredLicenses(licenses: Set<String>): ProcessedDeclaredLicense =
    // See http://maven.apache.org/ref/3.6.3/maven-model/maven.html#project which says: "If multiple licenses
    // are listed, it is assumed that the user can select any of them, not that they must accept all."
    DeclaredLicenseProcessor.process(licenses, operator = SpdxOperator.OR)
