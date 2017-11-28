/*
 * Copyright (c) 2017 HERE Europe B.V.
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

package com.here.ort.util

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

import com.vdurmont.semver4j.Semver

import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.net.URLConnection

import okhttp3.Cache
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response

@Suppress("UnsafeCast")
val log = org.slf4j.LoggerFactory.getLogger({}.javaClass) as ch.qos.logback.classic.Logger

val jsonMapper = ObjectMapper().registerKotlinModule()
val yamlMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

/**
 * A helper class to manage OkHttp instances backed by distinct cache directories.
 */
object OkHttpClientHelper {
    private val clients = mutableMapOf<String, OkHttpClient>()

    /**
     * Guess the media type based on the file component of a string.
     */
    fun guessMediaType(name: String): MediaType? {
        val contentType = URLConnection.guessContentTypeFromName(name) ?: "application/octet-stream"
        return MediaType.parse(contentType)
    }

    /**
     * Create a request body for the specified file.
     */
    fun createRequestBody(source: File) = RequestBody.create(guessMediaType(source.name), source)

    /**
     * Execute a request using the client for the specified cache directory.
     */
    fun execute(cacheSubDirectory: String, request: Request): Response {
        val client = clients.getOrPut(cacheSubDirectory) {
            val cacheDirectory = File(getUserConfigDirectory(), "cache/$cacheSubDirectory")
            val cache = Cache(cacheDirectory, 10 * 1024 * 1024)
            OkHttpClient.Builder().cache(cache).build()
        }

        return client.newCall(request).execute()
    }
}

/**
 * Return the directory to store user-specific configuration in.
 */
fun getUserConfigDirectory() = File(System.getProperty("user.home"), ".ort")

/**
 * Normalize a VCS URL by converting it to a common pattern. For example NPM defines some shortcuts for GitHub or GitLab
 * URLs which are converted to full URLs so that they can be used in a common way.
 *
 * @param vcsUrl The URL to normalize.
 * @param semverType Required to convert package manager specific shortcuts.
 */
fun normalizeVcsUrl(vcsUrl: String, semverType: Semver.SemverType = Semver.SemverType.STRICT): String {
    var url = vcsUrl.trimEnd('/')

    url = url.replace(Regex("(.+://)?git@github\\.com[:/](.+)")) {
        "ssh://git@github.com/${it.groupValues[2]}"
    }

    // A hierarchical URI looks like
    //     [scheme:][//authority][path][?query][#fragment]
    // where a server-based "authority" has the syntax
    //     [user-info@]host[:port]
    val uri = try {
        URI(url)
    } catch (e: URISyntaxException) {
        // Fall back to a file if the URL is a Windows path.
        return File(url).toSafeURI().toString()
    }

    if (semverType == Semver.SemverType.NPM) {
        // https://docs.npmjs.com/files/package.json#repository
        val path = uri.schemeSpecificPart
        if (path != null) {
            if (uri.authority == null && uri.query == null && uri.fragment == null) {
                // Handle shortcut URLs.
                when (uri.scheme) {
                    null -> return "https://github.com/$path.git"
                    "gist" -> return "https://gist.github.com/$path"
                    "bitbucket" -> return "https://bitbucket.org/$path.git"
                    "gitlab" -> return "https://gitlab.com/$path.git"
                }
            }
        }
    }

    if (uri.scheme == null && uri.path.isNotEmpty()) {
        // Fall back to a file if the URL is a Linux path.
        return File(url).toSafeURI().toString()
    }

    if (uri.scheme != "ssh" && uri.host != null && uri.host.endsWith("github.com")) {
        // Ensure the path ends in ".git".
        val path = if (uri.path.endsWith(".git")) uri.path else uri.path + ".git"

        // Remove any user name and "www" prefix.
        val host = uri.authority.substringAfter("@").removePrefix("www.")

        return "https://" + host + path
    }

    return url
}

/**
 * Split a [vcsUrl] into a pair of strings denoting the base repository URL and the path within the repository.
 */
fun splitVcsPathFromUrl(vcsUrl: String): Pair<String, String> {
    val uri = URI(vcsUrl)

    if (uri.host.endsWith("github.com")) {
        val split = vcsUrl.split("/blob/", "/tree/")
        if (split.size == 2) {
            val repo = split.first() + ".git"

            // Remove the blob / tree committish (e.g. a branch name) and any ".git" suffix.
            val path = split.last().substringAfter("/").substringBeforeLast(".git")

            return Pair(repo, path)
        }
    }

    // Fall back to returning just the original URL.
    return Pair(vcsUrl, "")
}

/**
 * Create all missing intermediate directories without failing if any already exists.
 *
 * @throws IOException if any missing directory could not be created.
 */
fun File.safeMkdirs() {
    if (this.isDirectory || this.mkdirs()) {
        return
    }

    throw IOException("Could not create directory ${this.absolutePath}.")
}

/**
 * Construct a "file:" URI in a safe way by never using a null authority for wider compatibility.
 */
fun File.toSafeURI(): URI {
    val fileUri = this.toURI()
    return URI("file", "", fileUri.path, fileUri.query, fileUri.fragment)
}

/**
 * Convenience function for [JsonNode] that returns an empty string if [JsonNode.asText] is called on a null object.
 */
fun JsonNode?.asTextOrEmpty(): String = if (this != null) this.asText() else ""

/**
 * Recursively collect the exception messages of this [Exception] and all its causes.
 */
fun Exception.collectMessages(): List<String> {
    val messages = mutableListOf<String>()
    var cause: Throwable? = this
    while (cause != null) {
        messages.add("${cause.javaClass.simpleName}: ${cause.message}")
        cause = cause.cause
    }
    return messages
}
