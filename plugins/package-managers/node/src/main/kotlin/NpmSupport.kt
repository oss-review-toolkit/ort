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

package org.ossreviewtoolkit.plugins.packagemanagers.node

import java.io.File
import java.lang.invoke.MethodHandles

import org.apache.logging.log4j.kotlin.loggerOf

import org.ossreviewtoolkit.analyzer.PackageManager.Companion.getFallbackProjectName
import org.ossreviewtoolkit.analyzer.PackageManager.Companion.processPackageVcs
import org.ossreviewtoolkit.analyzer.PackageManager.Companion.processProjectVcs
import org.ossreviewtoolkit.analyzer.parseAuthorString
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.common.realFile
import org.ossreviewtoolkit.utils.common.toUri
import org.ossreviewtoolkit.utils.spdx.SpdxConstants

internal const val NON_EXISTING_SEMVER = "0.0.0"

private val logger = loggerOf(MethodHandles.lookup().lookupClass())

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
 * Construct a [Package] by parsing its _package.json_ file and - if applicable - querying additional
 * content via the `npm view` command. The result is a [Pair] with the raw identifier and the new package.
 */
internal fun parsePackage(
    workingDir: File,
    packageJsonFile: File,
    getRemotePackageDetails: (workingDir: File, packageName: String) -> PackageJson?
): Package {
    val packageJson = parsePackageJson(packageJsonFile)

    // The "name" and "version" fields are only required if the package is going to be published, otherwise they are
    // optional, see
    // - https://docs.npmjs.com/cli/v10/configuring-npm/package-json#name
    // - https://docs.npmjs.com/cli/v10/configuring-npm/package-json#version
    // So, projects analyzed by ORT might not have these fields set.
    val rawName = packageJson.name.orEmpty() // TODO: Fall back to a generated name if the name is unset.
    val (namespace, name) = splitNpmNamespaceAndName(rawName)
    val version = packageJson.version ?: NON_EXISTING_SEMVER

    val declaredLicenses = packageJson.licenses.mapNpmLicenses()
    val authors = packageJson.authors.flatMap { parseAuthorString(it.name) }.mapNotNullTo(mutableSetOf()) { it.name }

    var description = packageJson.description.orEmpty()
    var homepageUrl = packageJson.homepage.orEmpty()

    // Note that all fields prefixed with "_" are considered private to NPM and should not be relied on.
    var downloadUrl = expandNpmShortcutUrl(packageJson.resolved.orEmpty()).ifEmpty {
        // If the normalized form of the specified dependency contains a URL as the version, expand and use it.
        val fromVersion = packageJson.from.orEmpty().substringAfterLast('@')
        expandNpmShortcutUrl(fromVersion).takeIf { it != fromVersion }.orEmpty()
    }

    var hash = Hash.create(packageJson.integrity.orEmpty())

    var vcsFromPackage = parseNpmVcsInfo(packageJson)

    val id = Identifier("NPM", namespace, name, version)

    val hasIncompleteData = description.isEmpty() || homepageUrl.isEmpty() || downloadUrl.isEmpty()
        || hash == Hash.NONE || vcsFromPackage == VcsInfo.EMPTY

    if (hasIncompleteData) {
        getRemotePackageDetails(workingDir, "$rawName@$version")?.let { details ->
            if (description.isEmpty()) description = details.description.orEmpty()
            if (homepageUrl.isEmpty()) homepageUrl = details.homepage.orEmpty()

            details.dist?.let { dist ->
                if (downloadUrl.isEmpty() || hash == Hash.NONE) {
                    downloadUrl = dist.tarball.orEmpty()
                    hash = Hash.create(dist.shasum.orEmpty())
                }
            }

            // Do not replace but merge, because it happens that `package.json` has VCS info while
            // `npm view` doesn't, for example for dependencies hosted on GitLab package registry.
            vcsFromPackage = vcsFromPackage.merge(parseNpmVcsInfo(details))
        }
    }

    downloadUrl = downloadUrl.fixNpmDownloadUrl()

    val vcsFromDownloadUrl = VcsHost.parseUrl(downloadUrl)
    if (vcsFromDownloadUrl.url != downloadUrl) {
        vcsFromPackage = vcsFromPackage.merge(vcsFromDownloadUrl)
    }

    val module = Package(
        id = id,
        authors = authors,
        declaredLicenses = declaredLicenses,
        description = description,
        homepageUrl = homepageUrl,
        binaryArtifact = RemoteArtifact.EMPTY,
        sourceArtifact = RemoteArtifact(
            url = VcsHost.toArchiveDownloadUrl(vcsFromDownloadUrl) ?: downloadUrl,
            hash = hash
        ),
        vcs = vcsFromPackage,
        vcsProcessed = processPackageVcs(vcsFromPackage, homepageUrl)
    )

    require(module.id.name.isNotEmpty()) {
        "Generated package info for '${id.toCoordinates()}' has no name."
    }

    require(module.id.version.isNotEmpty()) {
        "Generated package info for '${id.toCoordinates()}' has no version."
    }

    return module
}

internal fun parseProject(packageJsonFile: File, analysisRoot: File, managerName: String): Project {
    logger.debug { "Parsing project info from '$packageJsonFile'." }

    val packageJson = parsePackageJson(packageJsonFile)

    val rawName = packageJson.name.orEmpty()
    val (namespace, name) = splitNpmNamespaceAndName(rawName)

    val projectName = name.ifBlank {
        getFallbackProjectName(analysisRoot, packageJsonFile).also {
            logger.warn { "'$packageJsonFile' does not define a name, falling back to '$it'." }
        }
    }

    val version = packageJson.version.orEmpty()
    if (version.isBlank()) {
        logger.warn { "'$packageJsonFile' does not define a version." }
    }

    val declaredLicenses = packageJson.licenses.mapNpmLicenses()
    val authors = packageJson.authors.flatMap { parseAuthorString(it.name) }.mapNotNullTo(mutableSetOf()) { it.name }
    val homepageUrl = packageJson.homepage.orEmpty()
    val projectDir = packageJsonFile.parentFile.realFile()
    val vcsFromPackage = parseNpmVcsInfo(packageJson)

    return Project(
        id = Identifier(
            type = managerName,
            namespace = namespace,
            name = projectName,
            version = version
        ),
        definitionFilePath = VersionControlSystem.getPathInfo(packageJsonFile.realFile()).path,
        authors = authors,
        declaredLicenses = declaredLicenses,
        vcs = vcsFromPackage,
        vcsProcessed = processProjectVcs(projectDir, vcsFromPackage, homepageUrl),
        homepageUrl = homepageUrl
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
