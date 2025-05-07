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
import java.util.LinkedList

import org.ossreviewtoolkit.analyzer.PackageManager.Companion.processPackageVcs
import org.ossreviewtoolkit.analyzer.parseAuthorString
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.common.realFile
import org.ossreviewtoolkit.utils.common.toUri
import org.ossreviewtoolkit.utils.spdx.SpdxConstants

private const val NON_EXISTING_SEMVER = "0.0.0"

/**
 * Expand an NPM shortcut [url] to a regular URL as used for dependencies, see
 * https://docs.npmjs.com/cli/v7/configuring-npm/package-json#urls-as-dependencies.
 */
internal fun expandShortcutUrl(url: String): String {
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
internal fun String.fixDownloadUrl(): String =
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

internal fun Collection<String>.mapLicenses(): Set<String> =
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
 * Parse information about the VCS from the [packageJson] of a module.
 */
internal fun parseVcsInfo(packageJson: PackageJson): VcsInfo {
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
        url = expandShortcutUrl(url),
        revision = head,
        path = path
    )
}

/**
 * Construct a [Package] by parsing its [packageJsonFile] and - if applicable - querying additional content via
 * [moduleInfoResolver]. The result is a [Pair] with the raw identifier and the new package.
 */
internal fun parsePackage(packageJsonFile: File, moduleInfoResolver: ModuleInfoResolver): Package {
    val packageJson = parsePackageJson(packageJsonFile)
    return parsePackage(packageJson, moduleInfoResolver)
}

/**
 * Construct a [Package] by parsing its [packageJson] and - if applicable - querying additional content via
 * [moduleInfoResolver]. The result is a [Pair] with the raw identifier and the new package.
 */
internal fun parsePackage(packageJson: PackageJson, moduleInfoResolver: ModuleInfoResolver): Package {
    // The "name" and "version" fields are only required if the package is going to be published, otherwise they are
    // optional, see
    // - https://docs.npmjs.com/cli/v10/configuring-npm/package-json#name
    // - https://docs.npmjs.com/cli/v10/configuring-npm/package-json#version
    // So, projects analyzed by ORT might not have these fields set.
    val rawName = packageJson.name.orEmpty() // TODO: Fall back to a generated name if the name is unset.
    val (namespace, name) = splitNamespaceAndName(rawName)
    val version = packageJson.version ?: NON_EXISTING_SEMVER

    val declaredLicenses = packageJson.licenses.mapLicenses()
    val authors = packageJson.authors.flatMap { parseAuthorString(it.name) }.mapNotNullTo(mutableSetOf()) { it.name }

    var description = packageJson.description.orEmpty()
    var homepageUrl = packageJson.homepage.orEmpty()

    // Note that all fields prefixed with "_" are considered private to NPM and should not be relied on.
    var downloadUrl = expandShortcutUrl(packageJson.resolved.orEmpty()).ifEmpty {
        // If the normalized form of the specified dependency contains a URL as the version, expand and use it.
        val fromVersion = packageJson.from.orEmpty().substringAfterLast('@')
        expandShortcutUrl(fromVersion).takeIf { it != fromVersion }.orEmpty()
    }

    var hash = Hash.create(packageJson.integrity.orEmpty())

    var vcsFromPackage = parseVcsInfo(packageJson)

    val id = Identifier("NPM", namespace, name, version)

    val hasIncompleteData = description.isEmpty() || homepageUrl.isEmpty() || downloadUrl.isEmpty()
        || hash == Hash.NONE || vcsFromPackage == VcsInfo.EMPTY

    if (hasIncompleteData) {
        moduleInfoResolver.getModuleInfo("$rawName@$version")?.let { details ->
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
            vcsFromPackage = vcsFromPackage.merge(parseVcsInfo(details))
        }
    }

    downloadUrl = downloadUrl.fixDownloadUrl()

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

/**
 * Split the given [rawName] of a module to a pair with namespace and name.
 */
internal fun splitNamespaceAndName(rawName: String): Pair<String, String> {
    val name = rawName.substringAfterLast("/")
    val namespace = rawName.removeSuffix(name).removeSuffix("/")
    return Pair(namespace, name)
}

internal val PackageJson.moduleId: String get() =
    buildString {
        append(name.orEmpty())
        if (!version.isNullOrBlank()) {
            append("@")
            append(version)
        }
    }

/**
 * Return the directories of all modules which have been installed in the 'node_modules' dir within [moduleDir].
 */
internal fun getInstalledModulesDirs(projectDir: File): Set<File> {
    val modulesDirsToProcess = LinkedList<File>().apply { add(projectDir.realFile) }
    val discoveredModulesDirs = mutableSetOf<File>()

    while (modulesDirsToProcess.isNotEmpty()) {
        val currentModule = modulesDirsToProcess.removeFirst()
        if (!discoveredModulesDirs.add(currentModule)) continue

        modulesDirsToProcess += getChildModuleDirs(currentModule)
    }

    return discoveredModulesDirs
}

/**
* Find all direct dependency module directories within the node_modules directory of the given [moduleDir].
* This handles both regular modules and namespaced (@organization) modules.
*/
private fun getChildModuleDirs(moduleDir: File): Set<File> {
    val nodeModulesDir = moduleDir.resolve("node_modules").takeIf { it.isDirectory } ?: return emptySet()

    fun File.isModuleDir(): Boolean =
        isDirectory && !isHidden && !name.startsWith("@") && resolve(NodePackageManagerType.DEFINITION_FILE).isFile

    val nodeModulesDirFiles = nodeModulesDir.walk().maxDepth(1)
    val childModuleDirsWithoutNamespace = nodeModulesDirFiles.filter { it.isModuleDir() }
    val childModuleDirsWithNamespace = nodeModulesDirFiles.filter {
        it.isDirectory && !it.isHidden && it.name.startsWith("@")
    }.flatMap { namespaceDir ->
        namespaceDir.walk().maxDepth(1).filter { it.isModuleDir() }
    }

    return (childModuleDirsWithoutNamespace + childModuleDirsWithNamespace).mapTo(mutableSetOf()) { it.realFile }
}
