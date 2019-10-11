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

package com.here.ort.downloader

import com.here.ort.model.VcsInfo
import com.here.ort.model.VcsType
import com.here.ort.utils.hasRevisionFragment

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
            var vcsUrl = projectUrl.scheme + "://" + projectUrl.authority

            // Append the first two path components that denote the user and project to the base URL.
            val pathIterator = Paths.get(projectUrl.path).iterator()

            if (pathIterator.hasNext()) {
                vcsUrl += "/${pathIterator.next()}"
            }

            if (pathIterator.hasNext()) {
                vcsUrl += "/${pathIterator.next()}"
            }

            var revision = ""
            var path = ""

            if (pathIterator.hasNext() && pathIterator.next().toString() == "src") {
                if (pathIterator.hasNext()) {
                    revision = pathIterator.next().toString()
                    path = projectUrl.path.substringAfter(revision).trimStart('/').removeSuffix(".git")
                }
            }

            val type = VersionControlSystem.forUrl(vcsUrl)?.type ?: VcsType.NONE
            if (type == VcsType.GIT) {
                vcsUrl += ".git"
            }

            return VcsInfo(type, vcsUrl, revision, path = path)
        }
    },

    /**
     * The enum constant to handle [GitHub][https://github.com/]-specific information.
     */
    GITHUB("github.com", VcsType.GIT) {
        override fun toVcsInfo(projectUrl: URI) = gitProjectUrlToVcsInfo(projectUrl)
    },

    /**
     * The enum constant to handle [GitLab][https://gitlab.com/]-specific information.
     */
    GITLAB("gitlab.com", VcsType.GIT) {
        override fun toVcsInfo(projectUrl: URI) = gitProjectUrlToVcsInfo(projectUrl)
    };

    companion object {
        /**
         * Return all [VcsInfo] that can be extracted from [projectUrl] by the applicable host.
         */
        fun toVcsInfo(projectUrl: String): VcsInfo? =
            try {
                URI(projectUrl).let {
                    values().find { host -> host.isApplicable(it) }?.toVcsInfo(it)
                }
            } catch (e: URISyntaxException) {
                null
            }
    }

    private val supportedTypes = supportedTypes.toSet()

    /**
     * Return whether this host is applicable for [url].
     */
    fun isApplicable(url: URI) = url.host == hostname

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
}

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
