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
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

import kotlin.jvm.optionals.getOrNull

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

import okhttp3.Request

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.plugins.packagemanagers.python.PylockFile
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.ort.execute
import org.ossreviewtoolkit.utils.ort.okHttpClient

/** The URL of the simple index of PyPI. */
internal const val PYPI_SIMPLE_INDEX_URL = "https://pypi.org/simple"

/**
 * The media type of the JSON simple API (PEP 691), with HTML as a fallback for indexes that only implement the
 * HTML simple API (PEP 503).
 */
private const val SIMPLE_INDEX_ACCEPT_HEADER = "application/vnd.pypi.simple.v1+json, text/html;q=0.1"

/** The suffix appended to a distribution URL to get the URL of its core metadata (PEP 658). */
private const val CORE_METADATA_SUFFIX = ".metadata"

/** An anchor on an HTML simple index page. The `href` is the distribution URL and the text is the file name. */
private val HTML_ANCHOR_REGEX = Regex("""<a\s+([^>]*)>\s*([^<]+?)\s*</a>""", RegexOption.IGNORE_CASE)

/**
 * An attribute of an HTML tag, whose value may be double-quoted, single-quoted or unquoted.
 */
private val HTML_ATTRIBUTE_REGEX = Regex("""([\w-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+))""")

private val json = Json { ignoreUnknownKeys = true }

/** A project page of the JSON simple API, see https://peps.python.org/pep-0691/#project-detail. */
@Serializable
private data class SimpleIndexProject(
    val files: List<SimpleIndexFile> = emptyList()
)

/**
 * A distribution file on a simple index project page. Core metadata is available if the `core-metadata` key (PEP
 * 714) or the older `data-dist-info-metadata` key (PEP 658) holds `true` or a table of hashes.
 */
@Serializable
private data class SimpleIndexFile(
    val filename: String,
    val url: String,

    @SerialName("core-metadata")
    val coreMetadata: JsonElement? = null,

    @SerialName("data-dist-info-metadata")
    val dataDistInfoMetadata: JsonElement? = null
) {
    val hasCoreMetadata: Boolean
        get() = listOfNotNull(coreMetadata, dataDistInfoMetadata).any { (it as? JsonPrimitive)?.booleanOrNull != false }
}

/**
 * The relevant part of a release as returned by the JSON API of PyPI-compatible indexes, see
 * https://docs.pypi.org/api/json/#get-a-release.
 */
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
 * A client to retrieve the core metadata of locked packages from a package index without downloading them.
 * The metadata is taken from the PEP 658 metadata file next to a distribution, or from the JSON API of the index as a
 * fallback. If a [configuredIndexUrl] is set, it is used for all packages instead of the index recorded in the
 * lockfile, e.g. to use a proxy of PyPI. Results are cached per package, and the client is thread-safe.
 */
internal class PythonIndexClient(private val configuredIndexUrl: String? = null) {
    private val metadataCache = ConcurrentHashMap<String, Optional<PythonCoreMetadata>>()

    /**
     * Return the metadata for the given [pkg], or null if it cannot be retrieved. VCS, directory and archive entries
     * have no metadata on an index.
     */
    fun getMetadata(pkg: PylockFile.Package): PythonCoreMetadata? {
        val version = pkg.version?.takeIf { pkg.isIndexPackage } ?: return null
        val indexUrl = (configuredIndexUrl ?: pkg.index ?: PYPI_SIMPLE_INDEX_URL).trimEnd('/')

        return metadataCache.getOrPut("$indexUrl/${pkg.normalizedName}/$version") {
            Optional.ofNullable(fetchMetadata(pkg, version, indexUrl))
        }.getOrNull()
    }

    private fun fetchMetadata(pkg: PylockFile.Package, version: String, indexUrl: String): PythonCoreMetadata? {
        // Prefer a universal wheel, as its metadata is not platform-specific. Try at most one wheel and the sdist, as
        // an index that serves no metadata for one file usually serves none at all.
        val distributions = listOfNotNull(
            pkg.wheels.find { it.isUniversalWheel } ?: pkg.wheels.firstOrNull(),
            pkg.sdist
        )

        val distributionUrls = distributions.mapNotNull { it.url }
            .takeIf { configuredIndexUrl == null }
            ?.ifEmpty { null }
            ?: findDistributionUrls(indexUrl, pkg)

        val metadata = distributionUrls.firstNotNullOfOrNull { url ->
            val metadataUrl = "$url$CORE_METADATA_SUFFIX"
            fetchText(metadataUrl)?.let { PythonCoreMetadata.parse(it, metadataUrl) }
        } ?: fetchFromJsonApi(indexUrl, pkg, version)

        if (metadata == null) {
            logger.warn { "Unable to retrieve metadata for '${pkg.name}' version $version from '$indexUrl'." }
        } else {
            logger.debug { "Retrieved metadata for '${pkg.name}' version $version from '${metadata.source}'." }
        }

        return metadata
    }

    /**
     * Return the URLs of the distribution files of [pkg] that have core metadata, looked up on the project page of the
     * simple index at [indexUrl]. This is needed if the lockfile records local paths only, or if a configured index is
     * used instead of the one in the lockfile.
     */
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
            logger.warn { "Unable to parse the simple index page '$projectUrl': ${it.collectMessages()}" }
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
            logger.warn { "Unable to parse the JSON API response from '$url': ${it.collectMessages()}" }
        }.getOrNull() ?: return null

        return PythonCoreMetadata(
            summary = info.summary,
            homepage = info.homePage,
            projectUrls = info.projectUrls.orEmpty(),
            author = info.author,
            authorEmail = info.authorEmail,
            license = info.license,
            licenseExpression = info.licenseExpression,
            classifiers = info.classifiers,
            source = url
        )
    }

    private fun fetchText(url: String, accept: String? = null): String? = fetchTextWithContentType(url, accept)?.second

    /**
     * Return the content type and the body of the resource at [url], or null if it is not available. A missing
     * resource is expected, e.g. if an index does not serve PEP 658 metadata, so it is only logged at debug level.
     */
    private fun fetchTextWithContentType(url: String, accept: String?): Pair<String, String>? =
        runCatching {
            val request = Request.Builder()
                .apply { accept?.let { header("Accept", it) } }
                .get()
                .url(url)
                .build()

            okHttpClient.execute(request).use { response ->
                when {
                    response.isSuccessful -> response.header("Content-Type").orEmpty() to response.body.string()

                    response.code == HttpURLConnection.HTTP_NOT_FOUND -> {
                        logger.debug { "The resource at '$url' does not exist." }
                        null
                    }

                    else -> {
                        logger.warn { "Requesting '$url' failed with HTTP code ${response.code}." }
                        null
                    }
                }
            }
        }.onFailure {
            logger.warn { "Requesting '$url' failed: ${it.collectMessages()}" }
        }.getOrNull()
}

/**
 * Parse the anchors of the HTML project page at [pageUrl] into files. The `href` may be relative to the page and may
 * carry the hash as a fragment.
 */
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
