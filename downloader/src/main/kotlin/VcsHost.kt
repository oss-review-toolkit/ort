/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

package org.ossreviewtoolkit.downloader

import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.hasRevisionFragment
import org.ossreviewtoolkit.utils.normalizeVcsUrl

import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Paths

/**
 * An enum to handle VCS-host-specific information.
 */
enum class VcsHost(
    /**
     * The hostname of VCS host.
     */
    private val hostname: String,

    /**
     * The VCS types the host supports.
     */
    vararg supportedTypes: VcsType
) {
    /**
     * The enum constant to handle [Bitbucket][https://bitbucket.org/]-specific information.
     */
    BITBUCKET("bitbucket.org", VcsType.GIT, VcsType.MERCURIAL) {
        override fun toVcsInfo(projectUrl: URI): VcsInfo {
            var url = projectUrl.scheme + "://" + projectUrl.authority

            // Append the first two path components that denote the user and project to the base URL.
            val pathIterator = Paths.get(projectUrl.path).iterator()

            if (pathIterator.hasNext()) {
                url += "/${pathIterator.next()}"
            }

            if (pathIterator.hasNext()) {
                url += "/${pathIterator.next()}"
            }

            var revision = ""
            var path = ""

            if (pathIterator.hasNext() && pathIterator.next().toString() == "src") {
                if (pathIterator.hasNext()) {
                    revision = pathIterator.next().toString()
                    path = projectUrl.path.substringAfter(revision).trimStart('/').removeSuffix(".git")
                }
            }

            val type = VersionControlSystem.forUrl(url)?.type ?: VcsType.NONE
            if (type == VcsType.GIT) {
                url += ".git"
            }

            return VcsInfo(type, url, revision, path = path)
        }

        override fun toPermalinkInternal(vcsInfo: VcsInfo, startLine: Int, endLine: Int) =
            buildString {
                val vcsUrl = URI(vcsInfo.url)
                append("https://${vcsUrl.host}${vcsUrl.path}")

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
    },

    /**
     * The enum constant to handle [GitHub][https://github.com/]-specific information.
     */
    GITHUB("github.com", VcsType.GIT) {
        override fun toVcsInfo(projectUrl: URI) = gitProjectUrlToVcsInfo(projectUrl)

        override fun toPermalinkInternal(vcsInfo: VcsInfo, startLine: Int, endLine: Int) =
            toGitPermalink(URI(vcsInfo.url), vcsInfo.revision, vcsInfo.path, startLine, endLine, "#L", "-L")
    },

    /**
     * The enum constant to handle [GitLab][https://gitlab.com/]-specific information.
     */
    GITLAB("gitlab.com", VcsType.GIT) {
        override fun toVcsInfo(projectUrl: URI) = gitProjectUrlToVcsInfo(projectUrl)

        override fun toPermalinkInternal(vcsInfo: VcsInfo, startLine: Int, endLine: Int) =
            toGitPermalink(URI(vcsInfo.url), vcsInfo.revision, vcsInfo.path, startLine, endLine, "#L", "-")
    },

    SOURCEHUT("sr.ht", VcsType.GIT, VcsType.MERCURIAL) {
        override fun toVcsInfo(projectUrl: URI): VcsInfo {
            val type = when (projectUrl.host.substringBefore('.')) {
                "git" -> VcsType.GIT
                "hg" -> VcsType.MERCURIAL
                else -> VcsType.NONE
            }

            var url = projectUrl.scheme + "://" + projectUrl.authority

            // Append the first two path components that denote the user and project to the base URL.
            val pathIterator = Paths.get(projectUrl.path).iterator()

            if (pathIterator.hasNext()) {
                url += "/${pathIterator.next()}"
            }

            if (pathIterator.hasNext()) {
                url += "/${pathIterator.next()}"
            }

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

                                // SourceHut does not support an end line in permalinks to Mercural repos.
                            }
                        }
                    }

                    permalink
                }

                else -> ""
            }
    };

    companion object {
        /**
         * Return all [VcsInfo] that can be extracted from [projectUrl] by the applicable host.
         */
        fun toVcsInfo(projectUrl: String): VcsInfo {
            val vcs = try {
                URI(projectUrl).let {
                    values().find { host -> host.isApplicable(it) }?.toVcsInfo(it)
                }
            } catch (e: URISyntaxException) {
                null
            }

            if (vcs != null) return vcs

            // Fall back to generic URL detection for unknown VCS hosts.
            val svnBranchOrTagPattern = Regex("(.*svn.*)/(branches|tags)/([^/]+)/?(.*)")
            val svnBranchOrTagMatch = svnBranchOrTagPattern.matchEntire(projectUrl)

            val svnTrunkPattern = Regex("(.*svn.*)/(trunk)/?(.*)")
            val svnTrunkMatch = svnTrunkPattern.matchEntire(projectUrl)

            return when {
                svnBranchOrTagMatch != null -> {
                    VcsInfo(
                        type = VcsType.SUBVERSION,
                        url = svnBranchOrTagMatch.groupValues[1],
                        revision = "${svnBranchOrTagMatch.groupValues[2]}/${svnBranchOrTagMatch.groupValues[3]}",
                        path = svnBranchOrTagMatch.groupValues[4]
                    )
                }

                svnTrunkMatch != null -> {
                    VcsInfo(
                        type = VcsType.SUBVERSION,
                        url = svnTrunkMatch.groupValues[1],
                        revision = svnTrunkMatch.groupValues[2],
                        path = svnTrunkMatch.groupValues[3]
                    )
                }

                projectUrl.endsWith(".git") -> {
                    VcsInfo(VcsType.GIT, normalizeVcsUrl(projectUrl), "", null, "")
                }

                projectUrl.contains(".git/") -> {
                    val url = normalizeVcsUrl(projectUrl.substringBefore(".git/"))
                    val path = projectUrl.substringAfter(".git/")
                    VcsInfo(VcsType.GIT, "$url.git", "", null, path)
                }

                projectUrl.contains(".git#") || Regex("git.+#[a-fA-F0-9]{7,}").matches(projectUrl) -> {
                    val url = normalizeVcsUrl(projectUrl.substringBeforeLast('#'))
                    val revision = projectUrl.substringAfterLast('#')
                    VcsInfo(VcsType.GIT, url, revision, null, "")
                }

                else -> VcsInfo(VcsType.NONE, projectUrl, "")
            }
        }

        /**
         * Return the host-specific permanent link to browse the code location described by [vcsInfo] with optional
         * highlighting of [startLine] to [endLine].
         */
        fun toPermalink(vcsInfo: VcsInfo, startLine: Int = -1, endLine: Int = -1): String? {
            if (!isValidLineRange(startLine, endLine)) return null
            return values().find { host -> host.isApplicable(vcsInfo) }
                ?.toPermalinkInternal(vcsInfo, startLine, endLine)
        }
    }

    private val supportedTypes = supportedTypes.toSet()

    /**
     * Return whether this host is applicable for the [url] URI.
     */
    fun isApplicable(url: URI) = url.host?.endsWith(hostname) == true

    /**
     * Return whether this host is applicable for the [url] string.
     */
    fun isApplicable(url: String) =
        try {
            isApplicable(URI(url))
        } catch (e: URISyntaxException) {
            false
        }

    /**
     * Return whether this host is applicable for [vcsInfo].
     */
    fun isApplicable(vcsInfo: VcsInfo) = vcsInfo.type in supportedTypes && isApplicable(vcsInfo.url)

    /**
     * Return all [VcsInfo] that can be extracted from the host-specific [projectUrl].
     */
    fun toVcsInfo(projectUrl: String): VcsInfo? =
        try {
            val url = URI(projectUrl)
            if (isApplicable(url)) toVcsInfo(url) else null
        } catch (e: URISyntaxException) {
            null
        }

    protected abstract fun toVcsInfo(projectUrl: URI): VcsInfo

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

private fun gitProjectUrlToVcsInfo(projectUrl: URI): VcsInfo {
    var url = projectUrl.scheme + "://" + projectUrl.authority

    // Append the first two path components that denote the user and project to the base URL.
    val pathIterator = Paths.get(projectUrl.path).iterator()

    if (pathIterator.hasNext()) {
        url += "/${pathIterator.next()}"
    }

    if (pathIterator.hasNext()) {
        url += "/${pathIterator.next()}"

        if (!url.endsWith(".git")) {
            url += ".git"
        }
    }

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
    } else {
        if (projectUrl.hasRevisionFragment()) revision = projectUrl.fragment
    }

    return VcsInfo(VcsType.GIT, url, revision, path = path)
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
