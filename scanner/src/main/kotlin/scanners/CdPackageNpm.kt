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

package com.here.ort.scanner.scanners

import ch.frankel.slf4k.*
import ch.qos.logback.classic.Level

import com.fasterxml.jackson.databind.JsonNode

import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.Package
import com.here.ort.model.Project
import com.here.ort.model.Scope
import com.here.ort.model.VcsInfo
import com.here.ort.scanner.ScanException
import com.here.ort.scanner.Scanner
import com.here.ort.utils.asTextOrEmpty
import com.here.ort.utils.jsonMapper
import com.here.ort.utils.log

import java.io.File
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder

object CdPackageNpm : Scanner() {

    override val resultFileExtension = "json"

    override fun canScan(pkg: Package): Boolean {
        return pkg.packageManager.toLowerCase() == "npm"
    }

    override fun scanPath(path: File, resultsFile: File): Result {
        val packageJson = File(path, "package.json")
        log.debug { "Parsing project info from ${packageJson.absolutePath}." }

        val json = jsonMapper.readTree(packageJson)

        val rawName = json["name"].asTextOrEmpty()
        val (namespace, name) = splitNamespaceAndName(rawName)
        if (name.isBlank()) {
            log.warn { "'$packageJson' does not define a name." }
        }

        val version = json["version"].asTextOrEmpty()
        if (version.isBlank()) {
            log.warn { "'$packageJson' does not define a version." }
        }

        val declaredLicenses = sortedSetOf<String>()
        setOf(json["license"]).mapNotNullTo(declaredLicenses) {
            it?.asText()
        }

        val homepageUrl = json["homepage"].asTextOrEmpty()

        val projectDir = packageJson.parentFile

        // Try to get VCS information from the package.json's repository field, or otherwise from the working directory.
        val vcs = parseVcsInfo(json).takeUnless {
            it == VcsInfo.EMPTY
        } ?: VersionControlSystem.forDirectory(projectDir)?.let {
            it.getInfo(projectDir)
        } ?: VcsInfo.EMPTY

        val project = Project(
                packageManager = "npm",
                namespace = namespace,
                name = name,
                version = version,
                declaredLicenses = declaredLicenses,
                aliases = emptyList(),
                vcs = vcs,
                homepageUrl = homepageUrl,
                scopes = sortedSetOf<Scope>()
        )
        jsonMapper.writerWithDefaultPrettyPrinter().writeValue(resultsFile, project)
        return Result(declaredLicenses, sortedSetOf<String>())
    }

    override fun getResult(resultsFile: File): Result {
        return Result(sortedSetOf<String>(), sortedSetOf<String>())
    }

    private fun splitNamespaceAndName(rawName: String): Pair<String, String> {
        val name = rawName.substringAfterLast("/")
        val namespace = rawName.removeSuffix(name).removeSuffix("/")
        return Pair(namespace, name)
    }

    private fun parseVcsInfo(node: JsonNode): VcsInfo {
        // See https://github.com/npm/read-package-json/issues/7 for some background info.
        val head = node["gitHead"].asTextOrEmpty()

        return node["repository"]?.let { repo ->
            val type = repo["type"].asTextOrEmpty()
            val url = repo.textValue() ?: repo["url"].asTextOrEmpty()
            VcsInfo(type, expandShortcutURL(url), head, "")
        } ?: VcsInfo("", "", head, "")
    }


    fun expandShortcutURL(url: String): String {
        // A hierarchical URI looks like
        //     [scheme:][//authority][path][?query][#fragment]
        // where a server-based "authority" has the syntax
        //     [user-info@]host[:port]
        val uri = try {
            URI(url)
        } catch (e: URISyntaxException) {
            // Fall back to returning the original URL.
            return url
        }

        val path = uri.schemeSpecificPart
        return if (!path.isNullOrEmpty() && listOf(uri.authority, uri.query, uri.fragment).all { it == null }) {
            // See https://docs.npmjs.com/files/package.json#repository.
            when (uri.scheme) {
                null -> "https://github.com/$path.git"
                "gist" -> "https://gist.github.com/$path"
                "bitbucket" -> "https://bitbucket.org/$path.git"
                "gitlab" -> "https://gitlab.com/$path.git"
                else -> url
            }
        } else {
            url
        }
    }
}
