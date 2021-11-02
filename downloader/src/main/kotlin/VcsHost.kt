/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

package org.ossreviewtoolkit.downloader

import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths

import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.common.toUri
import org.ossreviewtoolkit.utils.core.normalizeVcsUrl

/**
 * An enum to handle VCS-host-specific information.
 */
@Suppress("TooManyFunctions")
enum class VcsHost(
    /**
     * The hostname of VCS host.
     */
    protected val hostname: String,

    /**
     * The VCS types the host supports.
     */
    vararg supportedTypes: VcsType
) {
    /**
     * The enum constant to handle [Bitbucket][https://bitbucket.org/]-specific information.
     */
    BITBUCKET("bitbucket.org", VcsType.GIT) {
        override fun getUserOrOrgInternal(projectUrl: URI) = projectUrlToUserOrOrgAndProject(projectUrl)?.first

        override fun getProjectInternal(projectUrl: URI) = projectUrlToUserOrOrgAndProject(projectUrl)?.second

        override fun toVcsInfoInternal(projectUrl: URI) =
            gitProjectUrlToVcsInfo(projectUrl) { baseUrl, pathIterator ->
                var revision = ""
                var path = ""

                if (pathIterator.hasNext() && pathIterator.next().toString() == "src") {
                    if (pathIterator.hasNext()) {
                        revision = pathIterator.next().toString()
                        path = projectUrl.path.substringAfter(revision).trimStart('/').removeSuffix(".git")
                    }
                }

                VcsInfo(VcsType.GIT, baseUrl, revision, path = path)
            }

        override fun toPermalinkInternal(vcsInfo: VcsInfo, startLine: Int, endLine: Int) =
            buildString {
                val vcsUrl = URI(vcsInfo.url)
                append("https://${vcsUrl.host}${vcsUrl.path.removeSuffix(".git")}")

                if (vcsInfo.revision.isNotEmpty()) {
                    append("/src/${vcsInfo.revision}")

                    if (vcsInfo.path.isNotEmpty()) {
                        append("/${vcsInfo.path}")

                        if (startLine > 0) {
                            append("#lines-$startLine")

                            if (endLine > startLine) append(":$endLine")
                        }
                    }
                }
            }

        override fun toArchiveDownloadUrlInternal(userOrOrg: String, project: String, vcsInfo: VcsInfo) =
            "https://$hostname/$userOrOrg/$project/get/${vcsInfo.revision}.tar.gz"
    },

    /**
     * The enum constant to handle [GitHub][https://github.com/]-specific information.
     */
    GITHUB("github.com", VcsType.GIT) {
        override fun getUserOrOrgInternal(projectUrl: URI) = projectUrlToUserOrOrgAndProject(projectUrl)?.first

        override fun getProjectInternal(projectUrl: URI) =
            projectUrlToUserOrOrgAndProject(projectUrl)?.second?.removeSuffix(".git")

        override fun toVcsInfoInternal(projectUrl: URI) =
            gitProjectUrlToVcsInfo(projectUrl) { baseUrl, pathIterator ->
                var revision = ""
                var path = ""

                if (pathIterator.hasNext()) {
                    val extra = pathIterator.next().toString()
                    if (extra in listOf("blob", "tree") && pathIterator.hasNext()) {
                        revision = pathIterator.next().toString()
                        path = projectUrl.path.substringAfter(revision).trimStart('/').removeSuffix(".git")
                    } else {
                        // Just treat all the extra components as a path.
                        path = (sequenceOf(extra) + pathIterator.asSequence()).joinToString("/")
                    }
                }

                VcsInfo(VcsType.GIT, baseUrl, revision, path = path)
            }

        override fun toPermalinkInternal(vcsInfo: VcsInfo, startLine: Int, endLine: Int) =
            toGitPermalink(URI(vcsInfo.url), vcsInfo.revision, vcsInfo.path, startLine, endLine, "#L", "-L")

        override fun toArchiveDownloadUrlInternal(userOrOrg: String, project: String, vcsInfo: VcsInfo) =
            "https://$hostname/$userOrOrg/$project/archive/${vcsInfo.revision}.tar.gz"
    },

    /**
     * The enum constant to handle [GitLab][https://gitlab.com/]-specific information.
     */
    GITLAB("gitlab.com", VcsType.GIT) {
        override fun getUserOrOrgInternal(projectUrl: URI) = projectUrlToUserOrOrgAndProject(projectUrl)?.first

        override fun getProjectInternal(projectUrl: URI) =
            projectUrlToUserOrOrgAndProject(projectUrl)?.second?.removeSuffix(".git")

        override fun toVcsInfoInternal(projectUrl: URI) =
            gitProjectUrlToVcsInfo(projectUrl) { baseUrl, pathIterator ->
                var revision = ""
                var path = ""

                if (pathIterator.hasNext()) {
                    var extra = pathIterator.next().toString()

                    // Skip the dash that is part of newer URLs.
                    if (extra == "-" && pathIterator.hasNext()) {
                        extra = pathIterator.next().toString()
                    }

                    if (extra in listOf("blob", "tree") && pathIterator.hasNext()) {
                        revision = pathIterator.next().toString()
                        path = projectUrl.path.substringAfter(revision).trimStart('/').removeSuffix(".git")
                    } else {
                        // Just treat all the extra components as a path.
                        path = (sequenceOf(extra) + pathIterator.asSequence()).joinToString("/")
                    }
                }

                VcsInfo(VcsType.GIT, baseUrl, revision, path = path)
            }

        override fun toPermalinkInternal(vcsInfo: VcsInfo, startLine: Int, endLine: Int) =
            toGitPermalink(URI(vcsInfo.url), vcsInfo.revision, vcsInfo.path, startLine, endLine, "#L", "-")

        override fun toArchiveDownloadUrlInternal(userOrOrg: String, project: String, vcsInfo: VcsInfo) =
            "https://$hostname/$userOrOrg/$project/-/archive/${vcsInfo.revision}/$project-${vcsInfo.revision}.tar.gz"
    },

    SOURCEHUT("sr.ht", VcsType.GIT, VcsType.MERCURIAL) {
        override fun getUserOrOrgInternal(projectUrl: URI) =
            projectUrlToUserOrOrgAndProject(projectUrl)?.first?.removePrefix("~")

        override fun getProjectInternal(projectUrl: URI) = projectUrlToUserOrOrgAndProject(projectUrl)?.second

        override fun toVcsInfoInternal(projectUrl: URI): VcsInfo {
            val type = when (projectUrl.host.substringBefore('.')) {
                "git" -> VcsType.GIT
                "hg" -> VcsType.MERCURIAL
                else -> VcsType.UNKNOWN
            }

            var url = projectUrl.scheme + "://" + projectUrl.authority

            // Append the first two path components that denote the user and project to the base URL.
            val pathIterator = Paths.get(projectUrl.path).iterator()

            if (pathIterator.hasNext()) url += "/${pathIterator.next()}"
            if (pathIterator.hasNext()) url += "/${pathIterator.next()}"

            var revision = ""
            var path = ""

            if (pathIterator.hasNext()) {
                val component = pathIterator.next().toString()
                val isGitUrl = type == VcsType.GIT && component == "tree"
                val isHgUrl = type == VcsType.MERCURIAL && component == "browse"

                if ((isGitUrl || isHgUrl) && pathIterator.hasNext()) {
                    revision = pathIterator.next().toString()
                    path = projectUrl.path.substringAfter(revision).trimStart('/')
                }
            }

            return VcsInfo(type, url, revision, path = path)
        }

        override fun toPermalinkInternal(vcsInfo: VcsInfo, startLine: Int, endLine: Int) =
            when (vcsInfo.type) {
                VcsType.GIT -> {
                    toGitPermalink(URI(vcsInfo.url), vcsInfo.revision, vcsInfo.path, startLine, endLine, "#L", "-")
                }

                VcsType.MERCURIAL -> {
                    val vcsUrl = URI(vcsInfo.url)
                    var permalink = "https://${vcsUrl.host}${vcsUrl.path}"

                    if (vcsInfo.revision.isNotEmpty()) {
                        permalink += "/browse/${vcsInfo.revision}"

                        if (vcsInfo.path.isNotEmpty()) {
                            permalink += "/${vcsInfo.path}"

                            if (startLine > 0) {
                                permalink += "#L$startLine"

                                // SourceHut does not support an end line in permalinks to Mercurial repos.
                            }
                        }
                    }

                    permalink
                }

                else -> ""
            }

        override fun toArchiveDownloadUrlInternal(userOrOrg: String, project: String, vcsInfo: VcsInfo) =
            "https://${vcsInfo.type.toString().lowercase()}.$hostname/~$userOrOrg/$project/archive/" +
                    "${vcsInfo.revision}.tar.gz"
    };

    companion object {
        private val SVN_BRANCH_OR_TAG_PATTERN = Regex("(.*svn.*)/(branches|tags)/([^/]+)/?(.*)")
        private val SVN_TRUNK_PATTERN = Regex("(.*svn.*)/(trunk)/?(.*)")
        private val GIT_REVISION_FRAGMENT = Regex("git.+#[a-fA-F0-9]{7,}")

        /**
         * Return the [VcsHost] for a [vcsUrl].
         */
        fun toVcsHost(vcsUrl: URI): VcsHost? = values().find { host -> host.isApplicable(vcsUrl) }

        /**
         * Return all [VcsInfo] that can be parsed from [projectUrl] without actually making a network request.
         */
        fun toVcsInfo(projectUrl: String): VcsInfo {
            val vcsInfoFromHost = projectUrl.toUri { toVcsHost(it)?.toVcsInfoInternal(it) }.getOrNull()

            // Fall back to generic URL detection for unknown VCS hosts.
            val svnBranchOrTagMatch = SVN_BRANCH_OR_TAG_PATTERN.matchEntire(projectUrl)
            val svnTrunkMatch = SVN_TRUNK_PATTERN.matchEntire(projectUrl)

            val vcsInfoFromUrl = when {
                svnBranchOrTagMatch != null -> VcsInfo(
                    type = VcsType.SUBVERSION,
                    url = svnBranchOrTagMatch.groupValues[1],
                    revision = "${svnBranchOrTagMatch.groupValues[2]}/${svnBranchOrTagMatch.groupValues[3]}",
                    path = svnBranchOrTagMatch.groupValues[4]
                )

                svnTrunkMatch != null -> VcsInfo(
                    type = VcsType.SUBVERSION,
                    url = svnTrunkMatch.groupValues[1],
                    revision = svnTrunkMatch.groupValues[2],
                    path = svnTrunkMatch.groupValues[3]
                )

                projectUrl.endsWith(".git") -> VcsInfo(
                    type = VcsType.GIT,
                    url = normalizeVcsUrl(projectUrl),
                    revision = ""
                )

                projectUrl.contains(".git/") -> {
                    val url = normalizeVcsUrl(projectUrl.substringBefore(".git/"))
                    val path = projectUrl.substringAfter(".git/")

                    VcsInfo(
                        type = VcsType.GIT,
                        url = "$url.git",
                        revision = "",
                        path = path
                    )
                }

                projectUrl.contains(".git#") || GIT_REVISION_FRAGMENT.matches(projectUrl) -> {
                    val url = normalizeVcsUrl(projectUrl.substringBeforeLast('#'))
                    val revision = projectUrl.substringAfterLast('#')

                    VcsInfo(
                        type = VcsType.GIT,
                        url = url,
                        revision = revision,
                    )
                }

                else -> VcsInfo(
                    type = VcsType.UNKNOWN,
                    url = projectUrl,
                    revision = ""
                )
            }

            return vcsInfoFromHost?.merge(vcsInfoFromUrl) ?: vcsInfoFromUrl
        }

        /**
         * Return the download URL to an archive generated for the referenced [vcsInfo], or null if no download URL can
         * be determined.
         */
        fun toArchiveDownloadUrl(vcsInfo: VcsInfo): String? {
            val normalizedVcsInfo = vcsInfo.normalize()
            val host = values().find { it.isApplicable(normalizedVcsInfo) } ?: return null

            return normalizedVcsInfo.url.toUri {
                val userOrOrg = host.getUserOrOrgInternal(it) ?: return@toUri null
                val project = host.getProjectInternal(it) ?: return@toUri null
                host.toArchiveDownloadUrlInternal(userOrOrg, project, normalizedVcsInfo)
            }.getOrNull()
        }

        /**
         * Return the host-specific permanent link to browse the code location described by [vcsInfo] with optional
         * highlighting of [startLine] to [endLine].
         */
        fun toPermalink(vcsInfo: VcsInfo, startLine: Int = -1, endLine: Int = -1): String? {
            if (!isValidLineRange(startLine, endLine)) return null
            return values().find { host -> host.isApplicable(vcsInfo) }
                ?.toPermalinkInternal(vcsInfo.normalize(), startLine, endLine)
        }
    }

    private val supportedTypes = supportedTypes.asList()

    /**
     * Return whether this host is applicable for the [url] URI.
     */
    fun isApplicable(url: URI) = url.host?.endsWith(hostname) == true

    /**
     * Return whether this host is applicable for the [url] string.
     */
    fun isApplicable(url: String) = url.toUri { isApplicable(it) }.getOrDefault(false)

    /**
     * Return whether this host is applicable for [vcsInfo].
     */
    fun isApplicable(vcsInfo: VcsInfo) = vcsInfo.type in supportedTypes && isApplicable(vcsInfo.url)

    /**
     * Return the user or organization name the project belongs to.
     */
    fun getUserOrOrganization(projectUrl: String): String? =
        projectUrl.toUri { if (isApplicable(it)) getUserOrOrgInternal(it) else null }.getOrNull()

    protected abstract fun getUserOrOrgInternal(projectUrl: URI): String?

    /**
     * Return the project's name.
     */
    fun getProject(projectUrl: String): String? =
        projectUrl.toUri { if (isApplicable(it)) getProjectInternal(it) else null }.getOrNull()

    protected abstract fun getProjectInternal(projectUrl: URI): String?

    /**
     * Return all [VcsInfo] that can be extracted from the host-specific [projectUrl].
     */
    fun toVcsInfo(projectUrl: String): VcsInfo? =
        projectUrl.toUri { if (isApplicable(it)) toVcsInfoInternal(it) else null }.getOrNull()

    protected abstract fun toVcsInfoInternal(projectUrl: URI): VcsInfo

    /**
     * Return the download URL to an archive generated for the referenced [vcsInfo], or null if no download URL can be
     * determined.
     */
    fun toArchiveDownloadUrl(vcsInfo: VcsInfo): String? {
        val normalizedVcsInfo = vcsInfo.normalize()
        if (!isApplicable(normalizedVcsInfo)) return null

        return normalizedVcsInfo.url.toUri {
            val userOrOrg = getUserOrOrgInternal(it) ?: return@toUri null
            val project = getProjectInternal(it) ?: return@toUri null
            toArchiveDownloadUrlInternal(userOrOrg, project, normalizedVcsInfo)
        }.getOrNull()
    }

    abstract fun toArchiveDownloadUrlInternal(userOrOrg: String, project: String, vcsInfo: VcsInfo): String

    /**
     * Return the host-specific permanent link to browse the code location described by [vcsInfo] with optional
     * highlighting of [startLine] to [endLine].
     */
    fun toPermalink(vcsInfo: VcsInfo, startLine: Int = -1, endLine: Int = -1): String? {
        val normalizedVcsInfo = vcsInfo.normalize()
        if (!isApplicable(normalizedVcsInfo) || !isValidLineRange(startLine, endLine)) return null
        return toPermalinkInternal(normalizedVcsInfo, startLine, endLine)
    }

    protected abstract fun toPermalinkInternal(vcsInfo: VcsInfo, startLine: Int, endLine: Int): String
}

private fun String.isPathToMarkdownFile() =
    endsWith(".md", ignoreCase = true) || endsWith(".markdown", ignoreCase = true)

private fun isValidLineRange(startLine: Int, endLine: Int): Boolean =
    (startLine == -1 && endLine == -1) || (startLine >= 1 && endLine == -1) || (startLine in 1..endLine)

private fun projectUrlToUserOrOrgAndProject(projectUrl: URI): Pair<String, String>? {
    val pathIterator = Paths.get(projectUrl.path).iterator()

    if (pathIterator.hasNext()) {
        val userOrOrg = pathIterator.next()

        if (pathIterator.hasNext()) {
            val project = pathIterator.next()
            return Pair(userOrOrg.toString(), project.toString().removeSuffix(".git"))
        }
    }

    return null
}

private fun gitProjectUrlToVcsInfo(projectUrl: URI, pathParser: (String, Iterator<Path>) -> VcsInfo): VcsInfo {
    var baseUrl = projectUrl.scheme + "://" + projectUrl.authority

    // Append the first two path components that denote the user and project to the base URL.
    val pathIterator = Paths.get(projectUrl.path).iterator()

    if (pathIterator.hasNext()) {
        baseUrl += "/${pathIterator.next()}"
    }

    if (pathIterator.hasNext()) {
        baseUrl += "/${pathIterator.next()}"

        if (!baseUrl.endsWith(".git")) baseUrl += ".git"
    }

    return pathParser(baseUrl, pathIterator)
}

private fun toGitPermalink(
    vcsUrl: URI, revision: String, path: String, startLine: Int, endLine: Int,
    startLineMarker: String, endLineMarker: String
): String {
    var permalink = "https://${vcsUrl.host}${vcsUrl.path.removeSuffix(".git")}"

    if (revision.isNotEmpty()) {
        // GitHub and GitLab are tolerant about "blob" vs. "tree" here, but SourceHut requires "tree" also for files.
        val gitObject = if (path.isNotEmpty()) {
            // Markdown files are usually rendered and can only link to lines in blame view.
            if (path.isPathToMarkdownFile() && startLine != -1) "blame" else "tree"
        } else {
            "commit"
        }

        permalink += "/$gitObject/$revision"

        if (path.isNotEmpty()) {
            permalink += "/$path"

            if (startLine > 0) {
                permalink += "$startLineMarker$startLine"

                if (endLine > startLine) permalink += "$endLineMarker$endLine"
            }
        }
    }

    return permalink
}
