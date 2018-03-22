/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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
import com.fasterxml.jackson.dataformat.xml.XmlFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.net.URLConnection

import okhttp3.Cache
import okhttp3.ConnectionSpec
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

@Suppress("UnsafeCast")
val log = org.slf4j.LoggerFactory.getLogger({}.javaClass) as ch.qos.logback.classic.Logger

/**
 * Global variable that gets toggled by a command line parameter parsed in the main entry points of the modules.
 */
var printStackTrace = false

val jsonMapper = ObjectMapper().registerKotlinModule()
val xmlMapper = ObjectMapper(XmlFactory()).registerKotlinModule()
val yamlMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

/**
 * Ordinal for mandatory program parameters.
 */
const val PARAMETER_ORDER_MANDATORY = 0

/**
 * Ordinal for optional program parameters.
 */
const val PARAMETER_ORDER_OPTIONAL = 1

/**
 * Ordinal for logging related program parameters.
 */
const val PARAMETER_ORDER_LOGGING = 2

/**
 * Ordinal for the help program parameter.
 */
const val PARAMETER_ORDER_HELP = 100

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
    fun createRequestBody(source: File): RequestBody = RequestBody.create(guessMediaType(source.name), source)

    /**
     * Execute a request using the client for the specified cache directory.
     */
    fun execute(cachePath: String, request: Request): Response {
        val client = clients.getOrPut(cachePath) {
            val cacheDirectory = File(getUserConfigDirectory(), cachePath)
            val cache = Cache(cacheDirectory, 10 * 1024 * 1024)
            val specs = listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT)
            OkHttpClient.Builder()
                    .cache(cache)
                    .connectionSpecs(specs)
                    .build()
        }

        return client.newCall(request).execute()
    }
}

/**
 * Filter a list of [names] to include only those that likely belong to the given [version] of an optional [project].
 */
fun filterVersionNames(version: String, names: List<String>, project: String? = null): List<String> {
    if (version.isBlank()) return emptyList()

    val normalizedSeparator = '_'
    val normalizedVersion = version.replace(Regex("(\\.|-)"), normalizedSeparator.toString()).toLowerCase()

    val filteredByVersion = names.filter {
        val normalizedName = it.replace(Regex("(\\.|-)"), normalizedSeparator.toString()).toLowerCase()

        when {
            // Allow to ignore suffixes in names that are separated by something else than the current
            // separator, e.g. for version "3.3.1" accept "3.3.1-npm-packages" but not "3.3.1.0".
            normalizedName.startsWith(normalizedVersion) -> {
                val tail = normalizedName.removePrefix(normalizedVersion)
                tail.firstOrNull() != normalizedSeparator
            }

            // Allow to ignore prefixes in names that are separated by something else than the current
            // separator, e.g. for version "0.10" accept "docutils-0.10" but not "1.0.10".
            normalizedName.endsWith(normalizedVersion) -> {
                val head = normalizedName.removeSuffix(normalizedVersion)
                val last = head.lastOrNull()
                val forelast = head.dropLast(1).lastOrNull()
                last == null
                        || (last != normalizedSeparator && !last.isDigit())
                        || (last == normalizedSeparator && (forelast == null || !forelast.isDigit()))
                        || (last.toLowerCase() == 'v' && (forelast == null || forelast == normalizedSeparator))
            }

            else -> false
        }
    }

    return filteredByVersion.filter {
        // startsWith("") returns "true" for any string, so we get an unfiltered list if "project" is "null".
        it.startsWith(project ?: "")
    }.let {
        if (it.isEmpty()) filteredByVersion else it
    }
}

/**
 * Return the directory to store user-specific configuration in.
 */
fun getUserConfigDirectory() = File(System.getProperty("user.home"), ".ort")

/**
 * Return the full path to the given executable file if it is in the system's PATH environment, or null otherwise.
 */
fun getPathFromEnvironment(executable: String): File? {
    val paths = System.getenv("PATH")?.splitToSequence(File.pathSeparatorChar) ?: emptySequence()

    val executables = if (OS.isWindows) {
        val pathExt = System.getenv("PATHEXT")?.let {
            it.split(File.pathSeparatorChar).map { it.toLowerCase().drop(1) }
        } ?: emptyList()

        if (executable.substringAfterLast(".").toLowerCase() !in pathExt) {
            pathExt.map { "$executable.$it" }
        } else {
            listOf(executable)
        }
    } else {
        listOf(executable)
    }

    paths.forEach { path ->
        executables.forEach {
            val pathToExecutable = File(path, it)
            if (pathToExecutable.isFile) {
                return pathToExecutable
            }
        }
    }

    return null
}

/**
 * Normalize a VCS URL by converting it to a common pattern. For example NPM defines some shortcuts for GitHub or GitLab
 * URLs which are converted to full URLs so that they can be used in a common way.
 *
 * @param vcsUrl The URL to normalize.
 */
fun normalizeVcsUrl(vcsUrl: String): String {
    var url = vcsUrl.trimEnd('/')

    if (url.startsWith(":pserver:") || url.startsWith(":ext:")) {
        // Do not touch CVS URLs for now.
        return url
    }

    // URLs to Git repos may omit the scheme and use an scp-like URL that uses ":" to separate the host from the path,
    // see https://git-scm.com/docs/git-clone#_git_urls_a_id_urls_a. Make this an explicit ssh URL so it can be parsed
    // by Java's URI class.
    url = url.replace(Regex("^(.*)([a-zA-Z]+):([a-zA-Z]+)(.*)$")) {
        val tail = "${it.groupValues[1]}${it.groupValues[2]}/${it.groupValues[3]}${it.groupValues[4]}"
        if (url.contains("://")) tail else "ssh://$tail"
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

    // Handle host-specific normalizations.
    if (uri.host != null) {
        when {
            uri.host.endsWith("github.com") -> {
                // Ensure the path ends in ".git".
                val path = uri.path.takeIf { it.contains(Regex("\\.git(/|$)")) } ?: "${uri.path}.git"

                return if (uri.scheme == "ssh") {
                    // Ensure the generic "git" user name is specified.
                    val host = uri.authority.let { if (it.startsWith("git@")) it else "git@$it" }
                    "ssh://$host$path"
                } else {
                    // Remove any user name and "www" prefix.
                    val host = uri.authority.substringAfter("@").removePrefix("www.")
                    "https://$host$path"
                }
            }
        }
    }

    return url
}

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

/**
 * Delete files recursively without following symbolic links (Unix) or junctions (Windows).
 *
 * @throws IOException if the directory could not be deleted.
 */
fun File.safeDeleteRecursively() {
    if (!this.exists()) {
        return
    }

    // This call to walkFileTree() implicitly uses EnumSet.noneOf(FileVisitOption.class), i.e.
    // FileVisitOption.FOLLOW_LINKS is not used, so symbolic links are not followed.
    Files.walkFileTree(this.toPath(), object : SimpleFileVisitor<Path>() {
        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
            if (OS.isWindows && attrs.isOther) {
                // Unlink junctions to turn them into empty directories.
                val fsutil = ProcessCapture("fsutil", "reparsepoint", "delete", dir.toString())
                if (fsutil.isSuccess()) {
                    return FileVisitResult.SKIP_SUBTREE
                }
            }

            return FileVisitResult.CONTINUE
        }

        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            try {
                Files.delete(file)
            } catch (e: java.nio.file.AccessDeniedException) {
                if (file.toFile().setWritable(true)) {
                    // Try again.
                    Files.delete(file)
                }
            }

            return FileVisitResult.CONTINUE
        }

        override fun postVisitDirectory(dir: Path, exc: IOException?) =
                FileVisitResult.CONTINUE.also { Files.delete(dir) }
    })

    if (this.exists()) {
        throw IOException("Could not delete directory '${this.absolutePath}'.")
    }
}

/**
 * Create all missing intermediate directories without failing if any already exists.
 *
 * @throws IOException if any missing directory could not be created.
 */
fun File.safeMkdirs() {
    // Do not blindly trust mkdirs() returning "false" as it can fail for edge-cases like
    // File(File("/tmp/parent1/parent2"), "/").mkdirs() if parent1 does not exist, although the directory is
    // successfully created.
    if (this.isDirectory || this.mkdirs() || this.isDirectory) {
        return
    }

    throw IOException("Could not create directory '${this.absolutePath}'.")
}

/**
 * Search [this] directory upwards towards the root until a contained sub-directory called [searchDirName] is found and
 * return the parent of [searchDirName], or return null if no such directory is found.
 */
fun File.searchUpwardsForSubdirectory(searchDirName: String): File? {
    if (!this.isDirectory) return null

    var currentDir: File? = this.absoluteFile

    while (currentDir != null && !File(currentDir, searchDirName).isDirectory) {
        currentDir = currentDir.parentFile
    }

    return currentDir
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
 * Return the string encoded for safe use as a file name.
 */
fun String.fileSystemEncode() =
        // URLEncoder does not encode "." and "*", so do that manually.
        java.net.URLEncoder.encode(this, "UTF-8").replace("*", "%2A")
                .replace(Regex("(^\\.|\\.$)"), "%2E")

/**
 * Return the decoded string for a safe file name.
 */
fun String.fileSystemDecode(): String =
        // URLDecoder does decode "." and "*".
        java.net.URLDecoder.decode(this, "UTF-8")
