/*
 * Copyright (C) 2017 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.bundler

import java.io.File
import java.io.IOException
import java.lang.invoke.MethodHandles
import java.net.HttpURLConnection

import kotlinx.serialization.decodeFromString

import org.apache.logging.log4j.kotlin.loggerOf

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManager.Companion.getFallbackProjectName
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.utils.ort.HttpDownloadError
import org.ossreviewtoolkit.utils.ort.OkHttpClientHelper
import org.ossreviewtoolkit.utils.ort.downloadText
import org.ossreviewtoolkit.utils.ort.okHttpClient

private val logger = loggerOf(MethodHandles.lookup().lookupClass())

/** Return the gemspec file in the given [workingDir], or null if no such file exists. */
internal fun getGemspecFile(workingDir: File) =
    workingDir.walk().maxDepth(1).firstOrNull { it.isFile && it.extension == "gemspec" }

/** Parse the Bundler version from the `BUNDLED WITH` section of the given [lockfile]. */
internal fun parseBundlerVersionFromLockfile(lockfile: File): String? {
    val bundledWithLines = lockfile.readLines().dropWhile { it != "BUNDLED WITH" }
    if (bundledWithLines.size < 2) return null
    return bundledWithLines[1].trim()
}

/** Parse the project information from the given [definitionFile] and [gemsInfo]. */
internal fun parseProject(analysisRoot: File, definitionFile: File, gemsInfo: MutableMap<String, GemInfo>) =
    getGemspecFile(definitionFile.parentFile)?.let { gemspecFile ->
        // Project is a Gem, i.e. a library.
        gemsInfo[gemspecFile.nameWithoutExtension]
    } ?: GemInfo(
        name = getFallbackProjectName(analysisRoot, definitionFile),
        version = "",
        platform = "ruby",
        homepageUrl = "",
        authors = emptySet(),
        declaredLicenses = emptySet(),
        description = "",
        runtimeDependencies = emptySet(),
        vcs = VcsInfo.EMPTY,
        artifact = RemoteArtifact.EMPTY
    )

/** Convert this Ruby [GemInfo] to an ORT [Package]. */
internal fun GemInfo.toPackage() =
    Package(
        id = Identifier(PACKAGE_TYPE, "", name, version),
        authors = authors,
        declaredLicenses = declaredLicenses,
        description = description,
        homepageUrl = homepageUrl,
        binaryArtifact = RemoteArtifact.EMPTY,
        sourceArtifact = artifact,
        vcs = vcs,
        vcsProcessed = PackageManager.processPackageVcs(vcs, homepageUrl)
    )

/**
 * Query RubyGems for metadata of a Gem with the given [name], [version], and [platform]. Return its [GemInfo], or null
 * if the metadata cannot be retrieved or the Gem cannot be found.
 */
internal fun queryRubyGems(name: String, version: String, platform: String, retryCount: Int = 3): GemInfo? {
    // Query the metadata for the platform of the resolved Gem, as e.g. the dependencies and the artifact of Gems
    // with native extensions differ per platform.
    // See <http://guides.rubygems.org/rubygems-org-api-v2/>.
    val url = "https://rubygems.org/api/v2/rubygems/$name/versions/$version.yaml?platform=$platform"

    return okHttpClient.downloadText(url).mapCatching {
        val details = YAML.decodeFromString<VersionDetails>(it)
        GemInfo.createFromGem(details)
    }.onFailure {
        val error = (it as? HttpDownloadError) ?: run {
            logger.warn { "Unable to retrieve metadata for gem '$name' from RubyGems: ${it.message}" }
            return null
        }

        when (error.code) {
            HttpURLConnection.HTTP_NOT_FOUND -> logger.info { "Gem '$name' was not found on RubyGems." }

            OkHttpClientHelper.HTTP_TOO_MANY_REQUESTS -> {
                throw IOException(
                    "RubyGems reported too many requests when requesting metadata for gem '$name', see " +
                        "https://guides.rubygems.org/rubygems-org-api/#rate-limits."
                )
            }

            HttpURLConnection.HTTP_BAD_GATEWAY -> {
                if (retryCount > 0) {
                    // We see a lot of sporadic "bad gateway" responses that disappear when trying again.
                    Thread.sleep(100)
                    return queryRubyGems(name, version, platform, retryCount - 1)
                }

                throw IOException(
                    "RubyGems reported too many bad gateway errors when requesting metadata for gem '$name'."
                )
            }

            else -> {
                throw IOException(
                    "RubyGems reported unhandled HTTP code ${error.code} when requesting metadata for gem '$name'."
                )
            }
        }
    }.getOrNull()
}
