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

package org.ossreviewtoolkit.plugins.packagemanagers.node.utils

import org.ossreviewtoolkit.analyzer.parseAuthorString
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.plugins.packagemanagers.node.PackageJson
import org.ossreviewtoolkit.utils.common.toUri
import org.ossreviewtoolkit.utils.spdx.SpdxConstants

internal const val NON_EXISTING_SEMVER = "0.0.0"

/**
 * Expand an NPM shortcut [url] to a regular URL as used for dependencies, see
 * https://docs.npmjs.com/cli/v7/configuring-npm/package-json#urls-as-dependencies.
 */
internal fun expandNpmShortcutUrl(url: String): String {
    // A hierarchical URI looks like
    //     [scheme:][//authority][path][?query][#fragment]
    // where a server-based "authority" has the syntax
    //     [user-info@]host[:port]
    val uri = url.toUri().getOrElse {
        // Fall back to returning the original URL.
        return url
    }

    val path = uri.schemeSpecificPart

    // Do not mess with crazy URLs.
    if (path.startsWith("git@") || path.startsWith("github.com") || path.startsWith("gitlab.com")) return url

    return if (!path.isNullOrEmpty() && listOf(uri.authority, uri.query).all { it == null }) {
        // See https://docs.npmjs.com/cli/v7/configuring-npm/package-json#github-urls.
        val revision = uri.fragment?.let { "#$it" }.orEmpty()

        // See https://docs.npmjs.com/cli/v7/configuring-npm/package-json#repository.
        when (uri.scheme) {
            null, "github" -> "https://github.com/$path.git$revision"
            "gist" -> "https://gist.github.com/$path$revision"
            "bitbucket" -> "https://bitbucket.org/$path.git$revision"
            "gitlab" -> "https://gitlab.com/$path.git$revision"
            else -> url
        }
    } else {
        url
    }
}

/**
 * Return the result of doing various replacements in this URL.
 */
internal fun String.fixNpmDownloadUrl(): String =
    @Suppress("HttpUrlsUsage")
    // Work around the issue described at
    // https://npm.community/t/some-packages-have-dist-tarball-as-http-and-not-https/285/19.
    replace("http://registry.npmjs.org/", "https://registry.npmjs.org/")
        // Work around Artifactory returning API URLs instead of download URLs.
        // See these somewhat related issues:
        // - https://www.jfrog.com/jira/browse/RTFACT-8727
        // - https://www.jfrog.com/jira/browse/RTFACT-18463
        .replace(ARTIFACTORY_API_PATH_PATTERN, "$1/$2")

private val ARTIFACTORY_API_PATH_PATTERN = Regex("(.*artifactory.*)/api/npm/(.*)")

/**
 * Parse information about the author from the [package.json][json] file of a module. According to
 * https://docs.npmjs.com/files/package.json#people-fields-author-contributors, there are two formats to
 * specify the author of a package: An object with multiple properties or a single string.
 */
internal fun parseNpmAuthor(author: PackageJson.Author?): Set<String> =
    author?.let {
        if (it.url == null && it.email == null) {
            // The author might either originate from a textual node or from an object node. The former to
            // further process the author string.
            parseAuthorString(it.name, '<', '(')
        } else {
            // The author must have come from an object node, so applying parseAuthorString() is not necessary.
            it.name
        }
    }.let { setOfNotNull(it) }

internal fun Collection<String>.mapNpmLicenses(): Set<String> =
    mapNotNullTo(mutableSetOf()) { declaredLicense ->
        when {
            // NPM does not mean https://unlicense.org/ here, but the wish to not "grant others the right to use
            // a private or unpublished package under any terms", which corresponds to SPDX's "NONE".
            declaredLicense == "UNLICENSED" -> SpdxConstants.NONE

            // NPM allows declaring non-SPDX licenses only by referencing a license file. Avoid reporting an
            // [Issue] by mapping this to a valid license identifier.
            declaredLicense.startsWith("SEE LICENSE IN ") -> SpdxConstants.NOASSERTION

            else -> declaredLicense.takeUnless { it.isBlank() }
        }
    }

/**
 * Parse information about the VCS from the [package.json][node] file of a module.
 */
internal fun parseNpmVcsInfo(packageJson: PackageJson): VcsInfo {
    // See https://github.com/npm/read-package-json/issues/7 for some background info.
    val head = packageJson.gitHead.orEmpty()

    val repository = packageJson.repository ?: return VcsInfo(
        type = VcsType.UNKNOWN,
        url = "",
        revision = head
    )

    val type = repository.type.orEmpty()
    val url = repository.url
    val path = repository.directory.orEmpty()

    return VcsInfo(
        type = VcsType.forName(type),
        url = expandNpmShortcutUrl(url),
        revision = head,
        path = path
    )
}

/**
 * Split the given [rawName] of a module to a pair with namespace and name.
 */
internal fun splitNpmNamespaceAndName(rawName: String): Pair<String, String> {
    val name = rawName.substringAfterLast("/")
    val namespace = rawName.removeSuffix(name).removeSuffix("/")
    return Pair(namespace, name)
}
