/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.python.utils

import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.plugins.packagemanagers.python.PylockFile
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.replaceCredentialsInUri
import org.ossreviewtoolkit.utils.ort.HttpDownloadError
import org.ossreviewtoolkit.utils.ort.download
import org.ossreviewtoolkit.utils.ort.downloadText
import org.ossreviewtoolkit.utils.ort.okHttpClient

internal const val PYPI_SIMPLE_INDEX_URL = "https://pypi.org/simple"

private const val PYPI_FILES_HOST = "files.pythonhosted.org"

/** Prefer the JSON simple API (PEP 691) over the HTML one (PEP 503). */
private const val SIMPLE_INDEX_ACCEPT_HEADER = "application/vnd.pypi.simple.v1+json, text/html;q=0.1"

/** The suffix appended to a distribution URL to get the URL of its core metadata (PEP 658). */
private const val CORE_METADATA_SUFFIX = ".metadata"

/** An anchor on an HTML simple index page. The `href` is the distribution URL and the text is the file name. */
private val HTML_ANCHOR_REGEX = Regex("""<a\s+([^>]*)>\s*([^<]+?)\s*</a>""", RegexOption.IGNORE_CASE)

private val HTML_ATTRIBUTE_REGEX = Regex("""([\w-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+))""")

private val json = Json { ignoreUnknownKeys = true }

/** A project page of the JSON simple API, see https://peps.python.org/pep-0691/#project-detail. */
@Serializable
private data class SimpleIndexProject(
    val files: List<SimpleIndexFile> = emptyList()
)

/**
 * A distribution file on a simple index project page. PEP 714 renamed the metadata key, and PyPI used to serve the
 * old one with a `data-` prefix.
 */
@Serializable
private data class SimpleIndexFile(
    val filename: String,
    val url: String,

    @SerialName("core-metadata")
    val coreMetadata: JsonElement? = null,

    @SerialName("dist-info-metadata")
    val distInfoMetadata: JsonElement? = null,

    @SerialName("data-dist-info-metadata")
    val dataDistInfoMetadata: JsonElement? = null
) {
    @Transient
    val hasCoreMetadata: Boolean = listOfNotNull(coreMetadata, distInfoMetadata, dataDistInfoMetadata).any {
        (it as? JsonPrimitive)?.booleanOrNull != false
    }
}

/** A release of the JSON API of PyPI-compatible indexes, see https://docs.pypi.org/api/json/#get-a-release. */
@Serializable
private data class JsonApiRelease(
    val info: Info
) {
    @Serializable
    data class Info(
        val summary: String? = null,

        @SerialName("home_page")
        val homePage: String? = null,

        @SerialName("project_urls")
        val projectUrls: Map<String, String>? = null,

        val author: String? = null,

        @SerialName("author_email")
        val authorEmail: String? = null,

        val license: String? = null,

        @SerialName("license_expression")
        val licenseExpression: String? = null,

        val classifiers: List<String> = emptyList()
    )
}

/**
 * A client to retrieve the core metadata of locked packages from a package index without downloading them, either
 * from the PEP 658 metadata file next to a distribution or from the JSON API. A [configuredIndexUrl] is used instead
 * of PyPI, e.g. a proxy or mirror. Packages that are not from PyPI and have no index recorded are never looked up on
 * PyPI, as they could be private.
 */
internal class PythonIndexClient(private val configuredIndexUrl: String? = null) {
    private val metadataCache = ConcurrentHashMap<String, PythonCoreMetadata>()

    /** Return the metadata for the given [pkg], or null if it cannot be retrieved or [pkg] is not from an index. */
    fun getMetadata(pkg: PylockFile.Package): PythonCoreMetadata? {
        val version = pkg.version?.takeIf { pkg.isIndexPackage } ?: return null
        val isFromPyPI = pkg.isFromPyPI()
        val useConfiguredIndex = configuredIndexUrl != null && isFromPyPI
        val indexUrl = when {
            isFromPyPI -> configuredIndexUrl ?: PYPI_SIMPLE_INDEX_URL
            else -> pkg.index ?: configuredIndexUrl
        }?.trimEnd('/')

        // Without an index, the package is only known by the locations of its distribution files.
        val origin = indexUrl ?: pkg.getDistributionUrls().joinToString()
        val cacheKey = "$origin/${pkg.normalizedName}/$version"

        return metadataCache[cacheKey] ?: fetchMetadata(pkg, version, indexUrl, useConfiguredIndex)?.also {
            metadataCache[cacheKey] = it
        }
    }

    private fun fetchMetadata(
        pkg: PylockFile.Package,
        version: String,
        indexUrl: String?,
        useConfiguredIndex: Boolean
    ): PythonCoreMetadata? {
        // Prefer a universal wheel, as its metadata is not platform-specific. Try at most one wheel and the sdist, as
        // an index that serves no metadata for one file usually serves none at all.
        val distributions = listOfNotNull(
            pkg.wheels.find { it.isUniversalWheel } ?: pkg.wheels.firstOrNull(),
            pkg.sdist
        )

        val distributionUrls = distributions.mapNotNull { it.url }
            .takeUnless { useConfiguredIndex }
            ?.ifEmpty { null }
            ?: indexUrl?.let { findDistributionUrls(it, pkg) }.orEmpty()

        val metadata = distributionUrls.firstNotNullOfOrNull { url ->
            val metadataUrl = getMetadataUrl(url)
            fetchText(metadataUrl)?.let { PythonCoreMetadata.parse(it) }?.also {
                logger.debug {
                    "Retrieved metadata for '${pkg.name}' version $version from " +
                        "'${metadataUrl.replaceCredentialsInUri()}'."
                }
            }
        } ?: indexUrl?.let { fetchFromJsonApi(it, pkg, version) }

        if (metadata == null) {
            logger.warn {
                val source = indexUrl?.let { "'${it.replaceCredentialsInUri()}'" } ?: "its distribution files"
                "Unable to retrieve metadata for '${pkg.name}' version $version from $source."
            }
        }

        return metadata
    }

    /** Return the URLs of the distribution files of [pkg] that have core metadata according to the simple index. */
    private fun findDistributionUrls(indexUrl: String, pkg: PylockFile.Package): List<String> {
        val lockedFilenames = (pkg.wheels + listOfNotNull(pkg.sdist)).mapNotNullTo(mutableSetOf()) { it.filename }
        if (lockedFilenames.isEmpty()) return emptyList()

        val projectUrl = "$indexUrl/${pkg.normalizedName}/"
        val (contentType, body) = fetchTextWithContentType(projectUrl, SIMPLE_INDEX_ACCEPT_HEADER) ?: return emptyList()

        // Both flavors of the API may list the file URLs relative to the project page.
        val files = runCatching {
            if ("json" in contentType) {
                json.decodeFromString<SimpleIndexProject>(body).files.map {
                    it.copy(url = URI(projectUrl).resolve(it.url).toString())
                }
            } else {
                parseHtmlProjectPage(body, projectUrl)
            }
        }.onFailure {
            logger.warn {
                "Unable to parse the simple index page '${projectUrl.replaceCredentialsInUri()}': " +
                    it.collectMessages()
            }
        }.getOrDefault(emptyList())

        return files.filter { it.filename in lockedFilenames && it.hasCoreMetadata }.map { it.url }
    }

    private fun fetchFromJsonApi(indexUrl: String, pkg: PylockFile.Package, version: String): PythonCoreMetadata? {
        // The JSON API lives next to the simple index, e.g. "https://pypi.org/pypi/<name>/<version>/json".
        val url = "${indexUrl.removeSuffix("/simple")}/pypi/${pkg.normalizedName}/$version/json"
        val body = fetchText(url) ?: return null

        val info = runCatching {
            json.decodeFromString<JsonApiRelease>(body).info
        }.onFailure {
            logger.warn {
                "Unable to parse the JSON API response from '${url.replaceCredentialsInUri()}': ${it.collectMessages()}"
            }
        }.getOrNull() ?: return null

        logger.debug {
            "Retrieved metadata for '${pkg.name}' version $version from '${url.replaceCredentialsInUri()}'."
        }

        return PythonCoreMetadata(
            summary = info.summary.toMetadataValue(),
            homepage = info.homePage.toMetadataValue(),
            projectUrls = info.projectUrls.orEmpty().entries.associate { (label, url) ->
                label.trim().lowercase() to url.trim()
            },
            author = info.author.toMetadataValue(),
            authorEmail = info.authorEmail.toMetadataValue(),
            license = info.license.toMetadataValue(),
            licenseExpression = info.licenseExpression.toMetadataValue(),
            classifiers = info.classifiers
        )
    }

    private fun fetchText(url: String): String? =
        okHttpClient.downloadText(url).onFailure { logRequestFailure(url, it) }.getOrNull()

    private fun fetchTextWithContentType(url: String, accept: String): Pair<String, String>? =
        okHttpClient.download(url, accept = accept).mapCatching { (response, body) ->
            response.header("Content-Type").orEmpty() to body.use { it.string() }
        }.onFailure {
            logRequestFailure(url, it)
        }.getOrNull()

    /** Log that requesting [url] failed. A missing resource is expected, so it is only logged at debug level. */
    private fun logRequestFailure(url: String, exception: Throwable) {
        if (exception is HttpDownloadError && exception.code == HttpURLConnection.HTTP_NOT_FOUND) {
            logger.debug { "The resource at '${url.replaceCredentialsInUri()}' does not exist." }
        } else {
            logger.warn { "Requesting '${url.replaceCredentialsInUri()}' failed: ${exception.collectMessages()}" }
        }
    }
}

/** Return the URL of the core metadata file (PEP 658) for the distribution file at [distributionUrl]. */
private fun getMetadataUrl(distributionUrl: String): String {
    val url = distributionUrl.substringBefore('#')
    val path = url.substringBefore('?')

    return path + CORE_METADATA_SUFFIX + url.removePrefix(path)
}

private fun PylockFile.Package.getDistributionUrls(): List<String> =
    (wheels + listOfNotNull(sdist)).mapNotNull { it.url }

/**
 * Return whether this package was locked from PyPI. If the lockfile does not record the index, which is optional,
 * this is decided by the host of the distribution files.
 */
private fun PylockFile.Package.isFromPyPI(): Boolean {
    if (index != null) return index.trimEnd('/') == PYPI_SIMPLE_INDEX_URL

    val urls = getDistributionUrls()
    return urls.isNotEmpty() && urls.all { runCatching { URI(it).host }.getOrNull() == PYPI_FILES_HOST }
}

/** Parse the anchors of the HTML project page at [pageUrl] into files. */
private fun parseHtmlProjectPage(html: String, pageUrl: String): List<SimpleIndexFile> =
    HTML_ANCHOR_REGEX.findAll(html).mapNotNull { anchor ->
        val attributes = HTML_ATTRIBUTE_REGEX.findAll(anchor.groupValues[1]).associate { match ->
            val value = match.groups.drop(2).firstNotNullOf { it }.value
            match.groupValues[1].lowercase() to value
        }

        val href = attributes["href"] ?: return@mapNotNull null
        val url = URI(pageUrl).resolve(href.substringBefore('#')).toString()
        val coreMetadata = (attributes["data-core-metadata"] ?: attributes["data-dist-info-metadata"])?.let {
            JsonPrimitive(it != "false")
        }

        SimpleIndexFile(filename = anchor.groupValues[2], url = url, coreMetadata = coreMetadata)
    }.toList()
