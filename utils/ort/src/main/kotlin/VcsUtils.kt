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

package org.ossreviewtoolkit.utils.ort

import java.io.File

import org.ossreviewtoolkit.utils.common.toSafeUri
import org.ossreviewtoolkit.utils.common.toUri

/**
 * Normalize a string representing a [VCS URL][vcsUrl] to a common string form.
 */
fun normalizeVcsUrl(vcsUrl: String): String {
    var url = vcsUrl.trim().trimEnd('/')

    // Avoid using the unauthenticated Git protocol, which is blocked by many VCS hosts.
    if (url.startsWith("git://")) {
        url = "https://${url.removePrefix("git://")}"
    }

    // URLs to Git repos may omit the scheme and use an SCP-like URL that uses ":" to separate the host from the path,
    // see https://git-scm.com/docs/git-clone#_git_urls_a_id_urls_a. Make this an explicit ssh URL, so it can be parsed
    // by Java's URI class.
    url = url.replace(Regex("^(.*)([a-zA-Z]+):([a-zA-Z]+)(.*)$")) {
        val tail = "${it.groupValues[1]}${it.groupValues[2]}/${it.groupValues[3]}${it.groupValues[4]}"
        if ("://" in url) tail else "ssh://$tail"
    }

    // Fixup scp-like Git URLs that do not use a ':' after the server part.
    if (url.startsWith("git@")) {
        url = "ssh://$url"
    }

    // Drop any non-SVN VCS name with "+" from the scheme.
    if (!url.startsWith("svn+")) {
        url = url.replace(Regex("^(.+)\\+(.+)(://.+)$")) {
            // Use the string to the right of "+" which should be the protocol.
            "${it.groupValues[2]}${it.groupValues[3]}"
        }
    }

    // If there is no protocol by now and the host is Git-specific, assume https.
    if (url.startsWith("github.com") || url.startsWith("gitlab.com")) {
        url = "https://$url"
    }

    // A hierarchical URI looks like
    //     [scheme:][//authority][path][?query][#fragment]
    // where a server-based "authority" has the syntax
    //     [user-info@]host[:port]
    val uri = url.toUri().getOrNull()

    if (uri == null || (uri.scheme == null && uri.path.isNotEmpty())) {
        // Fall back to a file if the URL is a Windows or Linux path.
        return File(url).toSafeUri().toString()
    }

    // Handle host-specific normalizations.
    if (uri.host != null) {
        when {
            uri.host.endsWith("github.com") || uri.host.endsWith("gitlab.com") -> {
                // Ensure the path to a repository ends with ".git".
                val path = uri.path.takeIf { path ->
                    path.endsWith(".git") || path.count { it == '/' } != 2
                } ?: "${uri.path}.git"

                val query = uri.query?.takeIf { it.isNotBlank() }?.let { "?$it" }.orEmpty()

                return if (uri.scheme == "ssh") {
                    // Ensure the generic "git" username is specified.
                    val host = uri.authority.let { if (it.startsWith("git@")) it else "git@$it" }
                    "ssh://$host$path$query"
                } else {
                    // Remove any username and "www" prefix.
                    val host = uri.authority.substringAfter('@').removePrefix("www.")
                    "https://$host$path$query"
                }
            }
        }
    }

    return url
}
