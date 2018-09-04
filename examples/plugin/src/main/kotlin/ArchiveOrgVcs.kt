/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

package com.here.ort.examples.plugin

import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.Package
import com.here.ort.model.jsonMapper
import com.here.ort.utils.fileSystemEncode
import com.here.ort.utils.safeMkdirs
import com.here.ort.utils.textValueOrEmpty

import java.io.File
import java.io.IOException
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ArchiveOrgVcs : VersionControlSystem() {
    override val aliases = listOf("archive.org")
    override val commandName = "" // Not using a command line tool.
    override val latestRevisionNames = listOf<String>()

    override fun getVersion() = "1.0"

    override fun getWorkingTree(vcsDirectory: File) = object : WorkingTree(vcsDirectory) {
        override fun isValid() = false

        override fun isShallow() = false

        override fun getRemoteUrl() = ""

        override fun getRevision() = ""

        override fun getRootPath() = vcsDirectory

        override fun listRemoteBranches() = emptyList<String>()

        override fun listRemoteTags() = emptyList<String>()
    }

    override fun isApplicableUrlInternal(vcsUrl: String) = false

    override fun download(
        pkg: Package,
        targetDir: File,
        allowMovingRevisions: Boolean,
        recursive: Boolean
    ): WorkingTree {
        val instant = Instant.ofEpochSecond(pkg.vcsProcessed.revision.toLong())
        val timestamp = DateTimeFormatter.BASIC_ISO_DATE.withZone(ZoneId.of("UTC")).format(instant).substringBefore("Z")

        val pkgUrl = URL(pkg.vcsProcessed.url)

        println("Checking availability of archive.org entry for '$pkgUrl' at timestamp '$timestamp'.")

        val requestUrl = URL("https://archive.org/wayback/available?url=$pkgUrl&timestamp=$timestamp")
        val response = requestUrl.readText()
        val json = jsonMapper.readTree(response)

        val closest = json["archived_snapshots"]?.get("closest")
        val closestUrl = closest?.get("url")?.let { URL(it.textValueOrEmpty()) } ?: throw IOException(
            "Could not find an entry on archive.org for '$pkgUrl' for time '$instant'."
        )

        println("Writing archive.org entry for '$pkgUrl' at timestamp '$timestamp' to disk.")

        val outputFile = File(targetDir, pkgUrl.file.fileSystemEncode())
        outputFile.writeText(closestUrl.readText())

        return object : WorkingTree(targetDir) {
            override fun isValid() = true

            override fun isShallow() = false

            override fun getRemoteUrl() = pkg.vcsProcessed.url

            override fun getRevision() = pkg.vcsProcessed.revision

            override fun getRootPath() = targetDir

            override fun listRemoteBranches() = emptyList<String>()

            override fun listRemoteTags() = emptyList<String>()
        }
    }
}
