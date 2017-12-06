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

package com.here.ort.downloader.vcs

import ch.frankel.slf4k.*

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlFactory
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

import com.here.ort.downloader.Main
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.util.ProcessCapture
import com.here.ort.util.getCommandVersion
import com.here.ort.util.log

import java.io.File
import java.io.IOException

data class SubversionLogEntry(
        @JacksonXmlProperty(isAttribute = true)
        val revision: String,
        @JacksonXmlProperty
        val msg: String,
        @JacksonXmlProperty
        val date: String,
        @JacksonXmlProperty
        val author: String) {
}

object Subversion : VersionControlSystem() {
    override fun getVersion(): String {
        val subversionVersionRegex = Regex("svn, version (?<version>[\\d.]+) \\(.+\\)")

        return getCommandVersion("svn") {
            subversionVersionRegex.matchEntire(it.lineSequence().first())?.groups?.get("version")?.value ?: ""
        }
    }

    override fun getWorkingDirectory(vcsDirectory: File) =
            object : WorkingDirectory(vcsDirectory) {
                val infoCommandResult = ProcessCapture("svn", "info", workingDir.absolutePath)

                override fun isValid() = infoCommandResult.exitValue() == 0;

                override fun getRemoteUrl() = infoCommandResult.stdout().lineSequence()
                        .first { it.startsWith("URL:") }.removePrefix("URL:").trim()

                override fun getRevision() = getLineValue("Revision: ")

                override fun getRootPath(path: File) = getLineValue("Working Copy Root Path:")

                override fun getPathToRoot(path: File): String
                        = getLineValue("Path:").substringAfter(File.separatorChar)

                private fun getLineValue(linePrefix: String) =
                        infoCommandResult.requireSuccess().stdout().lineSequence().first { it.startsWith(linePrefix) }
                                .removePrefix(linePrefix).trim()
            }

    override fun isApplicableProvider(vcsProvider: String) = vcsProvider.toLowerCase() in listOf("subversion", "svn")

    override fun isApplicableUrl(vcsUrl: String) = ProcessCapture("svn", "ls", vcsUrl).exitValue() == 0

    override fun download(vcsUrl: String, vcsRevision: String?, vcsPath: String?, version: String,
                          targetDir: File): String {
        runSvnCommand(targetDir, "co", vcsUrl, "--depth", "empty", ".")

        val revision = if (vcsRevision != null && vcsRevision.isNotBlank()) {
            vcsRevision
        } else if (version.isNotBlank()) {
            try {
                log.info { "Trying to determine revision for version: $version" }
                val tagsList = runSvnCommand(targetDir, "list", "$vcsUrl/tags").stdout().trim().lineSequence()
                val tagName = tagsList.firstOrNull {
                    val trimmedTag = it.trimEnd('/')
                    trimmedTag.endsWith(version)
                            || trimmedTag.endsWith(version.replace('.', '_'))
                }

                val xml = runSvnCommand(targetDir,
                                        "log",
                                        "$vcsUrl/tags/$tagName",
                                        "--xml").stdout().trim()
                val xmlMapper = ObjectMapper(XmlFactory()).registerKotlinModule()
                val logEntries: List<SubversionLogEntry> = xmlMapper.readValue(xml,
                    xmlMapper.typeFactory.constructCollectionType(List::class.java, SubversionLogEntry::class.java))
                logEntries.firstOrNull()?.revision ?: ""

            } catch (e: IOException) {
                if (Main.stacktrace) {
                    e.printStackTrace()
                }

                log.warn { "Could not determine revision for version: $version. Falling back to fetching everything." }
                ""
            }

        } else {
            ""
        }

        if (vcsPath != null && vcsPath.isNotBlank()) {
            targetDir.resolve(vcsPath).mkdirs()
        }

        if (revision.isNotBlank()) {
            runSvnCommand(targetDir, "up", "-r", revision, "--set-depth", "infinity", vcsPath ?: "")
        } else {
            runSvnCommand(targetDir, "up", "--set-depth", "infinity", vcsPath?.apply { } ?: "")
        }

        return Subversion.getWorkingDirectory(targetDir).getRevision()
    }

    private fun runSvnCommand(workingDir: File, vararg args: String)
            = ProcessCapture(workingDir, "svn", *args).requireSuccess()
}
