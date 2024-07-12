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

package org.ossreviewtoolkit.plugins.packagemanagers.pub

import java.io.File

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.plugins.packagemanagers.pub.model.PackageInfo
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.isSymbolicLink

/**
 * A reader for the Pub cache directory. It looks for files in the ".pub-cache" directory in the user's home
 * directory. If Flutter is installed, it additionally looks for files in the ".pub-cache" directory of Flutter's
 * installation directory.
 */
internal class PubCacheReader(flutterHome: File? = null) {
    private val pubCacheRoot by lazy {
        Os.env["PUB_CACHE"]?.let { return@lazy File(it) }

        if (Os.isWindows) {
            File(Os.env["LOCALAPPDATA"], "Pub/Cache")
        } else {
            Os.userHomeDirectory / ".pub-cache"
        }
    }

    private val flutterPubCacheRoot by lazy {
        flutterHome?.resolve(".pub-cache")?.takeIf { it.isDirectory }
    }

    fun findFile(packageInfo: PackageInfo, workingDir: File, filename: String): File? {
        val artifactRootDir = findProjectRoot(packageInfo, workingDir) ?: return null
        // Try to locate the file directly.
        val file = artifactRootDir / filename
        if (file.isFile) return file

        // Search the directory tree for the file.
        return artifactRootDir.walk()
            .onEnter { !it.isSymbolicLink }
            .find { !it.isSymbolicLink && it.isFile && it.name == filename }
    }

    fun findProjectRoot(packageInfo: PackageInfo, workingDir: File): File? {
        val packageVersion = packageInfo.version.orEmpty()
        val type = packageInfo.source.orEmpty()
        val description = packageInfo.description
        val packageName = description.name.orEmpty()
        val url = description.url.orEmpty()
        val resolvedRef = description.resolvedRef.orEmpty()
        val resolvedPath = description.path.orEmpty()
        val isPathRelative = description.relative == true

        if (type == "path" && resolvedPath.isNotEmpty()) {
            // For "path" packages, the path should be the absolute resolved path
            // of the "path" given in the description.
            return if (isPathRelative) {
                workingDir.resolve(resolvedPath).takeIf { it.isDirectory }
            } else {
                File(resolvedPath).takeIf { it.isDirectory }
            }
        }

        val path = if (type == "hosted" && url.isNotEmpty()) {
            // Packages with source set to "hosted" and "url" key in description set to "https://pub.dartlang.org".
            // The path should be resolved to "hosted/pub.dartlang.org/packageName-packageVersion".
            "hosted/${url.hostedUrlToDirectoryName()}/$packageName-$packageVersion"
        } else if (type == "git" && resolvedRef.isNotEmpty()) {
            // Packages with source set to "git" and a "resolved-ref" key in description set to a gitHash.
            // These packages do not define a packageName in the packageInfo, but by definition the path resolves to
            // the project name as given from the VcsHost and to the resolvedRef.
            val projectName = VcsHost.getProject(url) ?: return null
            if (resolvedPath.isNotEmpty() && resolvedPath != ".") {
                "git/$projectName-$resolvedRef/$resolvedPath"
            } else {
                "git/$projectName-$resolvedRef"
            }
        } else {
            logger.error { "Could not find projectRoot of '$packageName'." }

            // Unsupported type.
            return null
        }

        return pubCacheRoot.resolve(path).takeIf { it.isDirectory }
            ?: flutterPubCacheRoot?.resolve(path)?.takeIf { it.isDirectory }
    }
}

// See https://github.com/dart-lang/pub/blob/ea4a1c854690d3abceb92c8cc2c6454470f9d5a7/lib/src/source/hosted.dart#L1899.
private fun String.hostedUrlToDirectoryName(): String {
    val url = replace(schemeLocalhostRegex) { match ->
        // Do not include the scheme for HTTPS URLs. This makes the directory names nice for the default and most
        // recommended scheme. Also do not include it for localhost URLs, since they are always known to be HTTP.
        val localhost = if (match.groupValues.size == 3) "" else "localhost"
        val scheme = if (match.groupValues[1] == "https://" || localhost.isNotEmpty()) "" else match.groupValues[1]
        "$scheme$localhost"
    }

    return url.replace(specialCharRegex) { match ->
        match.groupValues[0].map { char -> "%${char.code}" }.joinToString("")
    }
}

private val schemeLocalhostRegex = """^(https?://)(127\.0\.0\.1|\[::1]|localhost)?""".toRegex()
private val specialCharRegex = """[<>:"\\/|?*%]""".toRegex()
