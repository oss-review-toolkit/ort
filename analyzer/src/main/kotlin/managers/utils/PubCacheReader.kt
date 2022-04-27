/*
 * Copyright (C) 2017-2022 HERE Europe B.V.
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.analyzer.managers.utils

import com.fasterxml.jackson.databind.JsonNode

import java.io.File

import org.ossreviewtoolkit.analyzer.managers.flutterHome
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.isSymbolicLink
import org.ossreviewtoolkit.utils.common.textValueOrEmpty
import org.ossreviewtoolkit.utils.core.log

/**
 * A reader for the Pub cache directory. It looks for files in the ".pub-cache" directory in the user's home
 * directory. If Flutter is installed it additionally looks for files in the ".pub-cache" directory of Flutter's
 * installation directory.
 */
internal class PubCacheReader {
    internal val pubCacheRoot by lazy {
        Os.env["PUB_CACHE"]?.let { return@lazy File(it) }

        if (Os.isWindows) {
            File(Os.env["LOCALAPPDATA"], "Pub/Cache")
        } else {
            Os.userHomeDirectory.resolve(".pub-cache")
        }
    }

    private val flutterPubCacheRoot by lazy {
        flutterHome.resolve(".pub-cache").takeIf { it.isDirectory }
    }

    fun findFile(packageInfo: JsonNode, filename: String): File? {
        val artifactRootDir = findProjectRoot(packageInfo) ?: return null

        // Try to locate the file directly.
        val file = artifactRootDir.resolve(filename)
        if (file.isFile) return file

        // Search the directory tree for the file.
        return artifactRootDir.walk()
            .onEnter { !it.isSymbolicLink() }
            .find { !it.isSymbolicLink() && it.isFile && it.name == filename }
    }

    fun findProjectRoot(packageInfo: JsonNode): File? {
        val packageVersion = packageInfo["version"].textValueOrEmpty()
        val type = packageInfo["source"].textValueOrEmpty()
        val description = packageInfo["description"]
        val packageName = description["name"].textValueOrEmpty()
        val url = description["url"].textValueOrEmpty()
        val resolvedRef = description["resolved-ref"].textValueOrEmpty()

        val path = if (type == "hosted" && url.isNotEmpty()) {
            // Packages with source set to "hosted" and "url" key in description set to "https://pub.dartlang.org".
            // The path should be resolved to "hosted/pub.dartlang.org/packageName-packageVersion".
            "hosted/${url.replace("https://", "")}/$packageName-$packageVersion"
        } else if (type == "git" && resolvedRef.isNotEmpty()) {
            // Packages with source set to "git" and a "resolved-ref" key in description set to a gitHash.
            // These packages do not define a packageName in the packageInfo, but by definition the path resolves to
            // the project name as given from the VcsHost and to the resolvedRef.
            val projectName = VcsHost.fromUrl(url)?.getProject(url) ?: return null

            "git/$projectName-$resolvedRef"
        } else {
            log.error { "Could not find projectRoot of '$packageName'." }

            // Unsupported type.
            return null
        }

        return pubCacheRoot.resolve(path).takeIf { it.isDirectory }
            ?: flutterPubCacheRoot?.resolve(path)?.takeIf { it.isDirectory }
    }
}
