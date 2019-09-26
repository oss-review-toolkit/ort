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

import kotlin.reflect.full.primaryConstructor

/**
 * A class to handle VCS-host-specific information.
 */
sealed class VcsHost(val url: String) {
    companion object {
        private val ALL = VcsHost::class.sealedSubclasses

        /**
         * Get the VCS host that is applicable for [url].
         */
        fun fromUrl(url: String): VcsHost? =
            ALL.asSequence().map {
                it.primaryConstructor!!.call(url)
            }.find {
                it.isApplicable()
            }
    }

    /**
     * The [URI] for [url] if it can be parsed, or null otherwise.
     */
    protected val uri = try {
        URI(url)
    } catch (e: URISyntaxException) {
        null
    }

    /**
     * The base hostname of VCS host.
     */
    abstract val hostname: String

    /**
     * The set of VCS types the host supports.
     */
    abstract val supportedTypes: Set<VcsType>

    /**
     * Return whether this instance is applicable for the [uri] it was constructed with.
     */
    fun isApplicable() = uri?.host == hostname

    /**
     * Return all [VcsInfo] that can be extracted from [uri].
     */
    abstract fun toVcsInfo(): VcsInfo?
}

private fun gitUrlToVcsInfo(uri: URI): VcsInfo {
    var url = uri.scheme + "://" + uri.authority

    // Append the first two path components that denote the user and project to the base URL.
    val pathIterator = Paths.get(uri.path).iterator()

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
            path = uri.path.substringAfter(revision).trimStart('/').removeSuffix(".git")
        } else {
            // Just treat all the extra components as a path.
            path = (sequenceOf(extra) + pathIterator.asSequence()).joinToString("/")
        }
    } else {
        if (uri.hasRevisionFragment()) revision = uri.fragment
    }

    return VcsInfo(VcsType.GIT, url, revision, path = path)
}

/**
 * A class to handle [Bitbucket][https://bitbucket.org/]-specific information.
 */
class Bitbucket(url: String) : VcsHost(url) {
    override val hostname = "bitbucket.org"
    override val supportedTypes = setOf(VcsType.GIT, VcsType.MERCURIAL)

    override fun toVcsInfo(): VcsInfo? {
        if (uri == null) return null
        var url = uri.scheme + "://" + uri.authority

        // Append the first two path components that denote the user and project to the base URL.
        val pathIterator = Paths.get(uri.path).iterator()

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
                path = uri.path.substringAfter(revision).trimStart('/').removeSuffix(".git")
            }
        }

        val type = VersionControlSystem.forUrl(url)?.type ?: VcsType.NONE
        if (type == VcsType.GIT) {
            url += ".git"
        }

        return VcsInfo(type, url, revision, path = path)
    }
}

/**
 * A class to handle [GitHub][https://github.com/]-specific information.
 */
class GitHub(url: String) : VcsHost(url) {
    override val hostname = "github.com"
    override val supportedTypes = setOf(VcsType.GIT)

    override fun toVcsInfo(): VcsInfo? {
        return if (uri != null) gitUrlToVcsInfo(uri) else null
    }
}

/**
 * A class to handle [GitLab][https://gitlab.com/]-specific information.
 */
class GitLab(url: String) : VcsHost(url) {
    override val hostname = "gitlab.com"
    override val supportedTypes = setOf(VcsType.GIT)

    override fun toVcsInfo(): VcsInfo? {
        return if (uri != null) gitUrlToVcsInfo(uri) else null
    }
}
