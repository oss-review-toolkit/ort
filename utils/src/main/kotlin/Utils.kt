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

package com.here.ort.utils

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

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
    fun execute(cachePath: String, request: Request): Response {
        val client = clients.getOrPut(cachePath) {
            val cacheDirectory = File(getUserConfigDirectory(), cachePath)
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
 */
fun normalizeVcsUrl(vcsUrl: String): String {
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
 * Return the string encoded for safe use as a file name.
 */
fun String.fileSystemEncode() =
        // URLEncoder does not encode "." and "*", so do that manually.
        java.net.URLEncoder.encode(this, "UTF-8").replace("*", "%2A").replace(".", "%2E")

/**
 * Return the decoded string for a safe file name.
 */
fun String.fileSystemDecode(): String =
        // URLDecoder does decode "." and "*".
        java.net.URLDecoder.decode(this, "UTF-8")

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
