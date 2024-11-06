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

@file:Suppress("TooManyFunctions")

package org.ossreviewtoolkit.plugins.packagemanagers.node

import java.io.File

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager.Companion.getFallbackProjectName
import org.ossreviewtoolkit.analyzer.PackageManager.Companion.processPackageVcs
import org.ossreviewtoolkit.analyzer.PackageManager.Companion.processProjectVcs
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.NON_EXISTING_SEMVER
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.NodePackageManager
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.NpmDetection
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.expandNpmShortcutUrl
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.fixNpmDownloadUrl
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.mapNpmLicenses
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.parseNpmAuthor
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.parseNpmVcsInfo
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.splitNpmNamespaceAndName
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.common.realFile
import org.ossreviewtoolkit.utils.common.withoutPrefix

import org.semver4j.RangesList
import org.semver4j.RangesListFactory

/**
 * The [Node package manager](https://www.npmjs.com/) for JavaScript.
 *
 * This package manager supports the following [options][PackageManagerConfiguration.options]:
 * - *legacyPeerDeps*: If true, the "--legacy-peer-deps" flag is passed to NPM to ignore conflicts in peer dependencies
 *   which are reported since NPM 7. This allows to analyze NPM 6 projects with peer dependency conflicts. For more
 *   information see the [documentation](https://docs.npmjs.com/cli/v8/commands/npm-install#strict-peer-deps) and the
 *   [NPM Blog](https://blog.npmjs.org/post/626173315965468672/npm-v7-series-beta-release-and-semver-major).
 */
class Npm(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : Yarn(name, analysisRoot, analyzerConfig, repoConfig) {
    companion object {
        /** Name of the configuration option to toggle legacy peer dependency support. */
        const val OPTION_LEGACY_PEER_DEPS = "legacyPeerDeps"
    }

    class Factory : AbstractPackageManagerFactory<Npm>("NPM") {
        override val globsForDefinitionFiles = listOf("package.json")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Npm(type, analysisRoot, analyzerConfig, repoConfig)
    }

    private val legacyPeerDeps = options[OPTION_LEGACY_PEER_DEPS].toBoolean()

    private val npmViewCache = mutableMapOf<String, PackageJson>()

    override fun hasLockfile(projectDir: File) = NodePackageManager.NPM.hasLockfile(projectDir)

    override fun command(workingDir: File?) = if (Os.isWindows) "npm.cmd" else "npm"

    override fun getVersionRequirement(): RangesList = RangesListFactory.create("6.* - 10.*")

    override fun mapDefinitionFiles(definitionFiles: List<File>) =
        NpmDetection(definitionFiles).filterApplicable(NodePackageManager.NPM)

    override fun beforeResolution(definitionFiles: List<File>) {
        // We do not actually depend on any features specific to an NPM version, but we still want to stick to a
        // fixed minor version to be sure to get consistent results.
        checkVersion()
    }

    override fun getRemotePackageDetails(workingDir: File, packageName: String): PackageJson? {
        npmViewCache[packageName]?.let { return it }

        return runCatching {
            val process = run(workingDir, "info", "--json", packageName)

            parsePackageJson(process.stdout)
        }.onFailure { e ->
            logger.warn { "Error getting details for $packageName in directory $workingDir: ${e.message.orEmpty()}" }
        }.onSuccess {
            npmViewCache[packageName] = it
        }.getOrNull()
    }

    override fun runInstall(workingDir: File): ProcessCapture {
        val options = listOfNotNull(
            "--ignore-scripts",
            "--no-audit",
            "--legacy-peer-deps".takeIf { legacyPeerDeps }
        )

        val subcommand = if (hasLockfile(workingDir)) "ci" else "install"
        return ProcessCapture(workingDir, command(workingDir), subcommand, *options.toTypedArray())
    }
}

internal fun List<String>.groupLines(vararg markers: String): List<String> {
    val ignorableLinePrefixes = setOf("code ", "errno ", "path ", "syscall ")
    val singleLinePrefixes = setOf("deprecated ", "skipping integrity check for git dependency ")
    val minCommonPrefixLength = 5

    val issueLines = mapNotNull { line ->
        markers.firstNotNullOfOrNull { marker ->
            line.withoutPrefix(marker)?.takeUnless { ignorableLinePrefixes.any { prefix -> it.startsWith(prefix) } }
        }
    }

    var commonPrefix: String
    var previousPrefix = ""

    val collapsedLines = issueLines.distinct().fold(mutableListOf<String>()) { messages, line ->
        if (messages.isEmpty()) {
            // The first line is always added including the prefix. The prefix will be removed later.
            messages += line
        } else {
            // Find the longest common prefix that ends with space.
            commonPrefix = line.commonPrefixWith(messages.last())
            if (!commonPrefix.endsWith(' ')) {
                // Deal with prefixes being used on their own as separators.
                commonPrefix = if ("$commonPrefix " == previousPrefix || line.startsWith("$commonPrefix ")) {
                    "$commonPrefix "
                } else {
                    commonPrefix.dropLastWhile { it != ' ' }
                }
            }

            if (commonPrefix !in singleLinePrefixes && commonPrefix.length >= minCommonPrefixLength) {
                // Do not drop the whole prefix but keep the space when concatenating lines.
                messages[messages.size - 1] += line.drop(commonPrefix.length - 1).trimEnd()
                previousPrefix = commonPrefix
            } else {
                // Remove the prefix from previously added message start.
                messages[messages.size - 1] = messages.last().removePrefix(previousPrefix).trimStart()
                messages += line
            }
        }

        messages
    }

    if (collapsedLines.isNotEmpty()) {
        // Remove the prefix from the last added message start.
        collapsedLines[collapsedLines.size - 1] = collapsedLines.last().removePrefix(previousPrefix).trimStart()
    }

    val nonFooterLines = collapsedLines.takeWhile {
        // Skip any footer as a whole.
        it != "A complete log of this run can be found in:"
    }

    // If no lines but the last end with a dot, assume the message to be a single sentence.
    return if (
        nonFooterLines.size > 1 &&
        nonFooterLines.last().endsWith('.') &&
        nonFooterLines.subList(0, nonFooterLines.size - 1).none { it.endsWith('.') }
    ) {
        listOf(nonFooterLines.joinToString(" "))
    } else {
        nonFooterLines.map { it.trim() }
    }
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
    val authors = parseNpmAuthor(packageJson.authors.firstOrNull()) // TODO: parse all authors.

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
    Npm.logger.debug { "Parsing project info from '$packageJsonFile'." }

    val packageJson = parsePackageJson(packageJsonFile)

    val rawName = packageJson.name.orEmpty()
    val (namespace, name) = splitNpmNamespaceAndName(rawName)

    val projectName = name.ifBlank {
        getFallbackProjectName(analysisRoot, packageJsonFile).also {
            Npm.logger.warn { "'$packageJsonFile' does not define a name, falling back to '$it'." }
        }
    }

    val version = packageJson.version.orEmpty()
    if (version.isBlank()) {
        Npm.logger.warn { "'$packageJsonFile' does not define a version." }
    }

    val declaredLicenses = packageJson.licenses.mapNpmLicenses()
    val authors = parseNpmAuthor(packageJson.authors.firstOrNull()) // TODO: parse all authors.
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
