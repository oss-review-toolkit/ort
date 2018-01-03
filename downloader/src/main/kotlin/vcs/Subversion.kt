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

package com.here.ort.downloader.vcs

import ch.frankel.slf4k.*

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonRootName
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

import com.here.ort.downloader.Main
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.getCommandVersion
import com.here.ort.utils.log
import com.here.ort.utils.xmlMapper

import java.io.File
import java.io.IOException

data class SubversionInfoRepository(
        val root: String,
        val uuid: String)

data class SubversionInfoWorkingCopy(
        @JsonProperty("wcroot-abspath")
        val absolutePath: String,
        val schedule: String,
        val depth: String)

data class SubversionInfoCommit(
        @JacksonXmlProperty(isAttribute = true)
        val revision: String,
        val author: String?,
        val date: String)

data class SubversionInfoLock(
        val created: String)

@JsonRootName("entry")
data class SubversionInfoEntry(
        @JacksonXmlProperty(isAttribute = true)
        val kind: String,
        @JacksonXmlProperty(isAttribute = true)
        val path: String,
        @JacksonXmlProperty(isAttribute = true)
        val revision: String,
        val url: String,
        @JsonProperty("relative-url")
        val relativeUrl: String,
        val repository: SubversionInfoRepository,
        @JsonProperty("wc-info")
        val workingCopy: SubversionInfoWorkingCopy,
        val commit: SubversionInfoCommit)

@JsonRootName("entry")
data class SubversionTagsEntry(
        @JacksonXmlProperty(isAttribute = true)
        val kind: String,
        @JacksonXmlProperty(isAttribute = true)
        val path: String,
        @JacksonXmlProperty(isAttribute = true)
        val revision: String,
        val url: String,
        @JsonProperty("relative-url")
        val relativeUrl: String,
        val repository: SubversionInfoRepository,
        val commit: SubversionInfoCommit,
        val lock: SubversionInfoLock?)

data class SubversionLogEntry(
        @JacksonXmlProperty(isAttribute = true)
        val revision: String,
        val author: String,
        val date: String,
        val msg: String)

object Subversion : VersionControlSystem() {
    override fun getVersion(): String {
        val subversionVersionRegex = Regex("svn, version (?<version>[\\d.]+) \\(r\\d+\\)")

        return getCommandVersion("svn") {
            subversionVersionRegex.matchEntire(it.lineSequence().first())?.groups?.get("version")?.value ?: ""
        }
    }

    override fun getWorkingTree(vcsDirectory: File) =
            object : WorkingTree(vcsDirectory) {
                private val svnInfo = ProcessCapture("svn", "info", "--xml", workingDir.absolutePath)
                private val svnInfoReader = xmlMapper.readerFor(SubversionInfoEntry::class.java)
                        .with(DeserializationFeature.UNWRAP_ROOT_VALUE)
                private val svnInfoEntry: SubversionInfoEntry = svnInfoReader.readValue(svnInfo.stdout())

                override fun isValid() = svnInfo.exitValue() == 0

                override fun getRemoteUrl() = svnInfoEntry.repository.root

                override fun getRevision() = svnInfoEntry.commit.revision

                override fun getRootPath() = svnInfoEntry.workingCopy.absolutePath

                override fun listRemoteTags(): List<String> {
                    val svnInfoTags = runSvnCommand(workingDir, "info", "--xml", "--depth=immediates", "^/tags")
                    val valueType = xmlMapper.typeFactory
                            .constructCollectionType(List::class.java, SubversionTagsEntry::class.java)
                    val tagsEntries: List<SubversionTagsEntry> = xmlMapper.readValue(svnInfoTags.stdout(), valueType)

                    // As the "immediates" depth includes the "tags" parent, drop it.
                    return tagsEntries.drop(1).map { it.path }.sorted()
                }
            }

    override fun isApplicableProvider(vcsProvider: String) = vcsProvider.toLowerCase() in listOf("subversion", "svn")

    override fun isApplicableUrl(vcsUrl: String) = ProcessCapture("svn", "ls", vcsUrl).exitValue() == 0

    override fun download(vcsUrl: String, vcsRevision: String?, vcsPath: String?, version: String,
                          targetDir: File): String {
        log.info { "Using $this version ${getVersion()}." }

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
                val valueType = xmlMapper.typeFactory
                        .constructCollectionType(List::class.java, SubversionLogEntry::class.java)
                val logEntries: List<SubversionLogEntry> = xmlMapper.readValue(xml, valueType)
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
            // In case of sparse checkout, destination directory needs to exists,
            // `svn update` will fail otherwise (if dest dir is deeper than one level).
            targetDir.resolve(vcsPath).mkdirs()
        }

        if (revision.isNotBlank()) {
            runSvnCommand(targetDir, "up", "-r", revision, "--set-depth", "infinity", vcsPath ?: "")
        } else {
            runSvnCommand(targetDir, "up", "--set-depth", "infinity", vcsPath?.apply { } ?: "")
        }

        return Subversion.getWorkingTree(targetDir).getRevision()
    }

    private fun runSvnCommand(workingDir: File, vararg args: String) =
            ProcessCapture(workingDir, "svn", *args).requireSuccess()
}
