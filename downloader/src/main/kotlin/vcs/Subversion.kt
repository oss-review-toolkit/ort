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

import com.here.ort.downloader.DownloadException
import com.here.ort.downloader.Main
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.VcsInfo
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
        val subversionVersionRegex = Regex("svn, [Vv]ersion (?<version>[\\d.]+) \\(r\\d+\\)")

        return getCommandVersion("svn") {
            subversionVersionRegex.matchEntire(it.lineSequence().first())?.groups?.get("version")?.value ?: ""
        }
    }

    override fun getWorkingTree(vcsDirectory: File) =
            object : WorkingTree(vcsDirectory) {
                private val svnInfoReader = xmlMapper.readerFor(SubversionInfoEntry::class.java)
                        .with(DeserializationFeature.UNWRAP_ROOT_VALUE)

                override fun isValid(): Boolean {
                    if (!workingDir.isDirectory) {
                        return false
                    }

                    return runSvnInfoCommand() != null
                }

                override fun getRemoteUrl() = runSvnInfoCommand()?.repository?.root ?: ""

                override fun getRevision() = runSvnInfoCommand()?.commit?.revision ?: ""

                override fun getRootPath() = runSvnInfoCommand()?.workingCopy?.absolutePath ?: ""

                override fun listRemoteTags(): List<String> {
                    val svnInfoTags = runSvnCommand(workingDir, "info", "--xml", "--depth=immediates", "^/tags")
                    val valueType = xmlMapper.typeFactory
                            .constructCollectionType(List::class.java, SubversionTagsEntry::class.java)
                    val tagsEntries: List<SubversionTagsEntry> = xmlMapper.readValue(svnInfoTags.stdout(), valueType)

                    // As the "immediates" depth includes the "tags" parent, drop it.
                    return tagsEntries.drop(1).map { it.path }.sorted()
                }

                private fun runSvnInfoCommand(): SubversionInfoEntry? {
                    val info = ProcessCapture("svn", "info", "--xml", workingDir.absolutePath)
                    if (info.exitValue() != 0) {
                        return null
                    }
                    return svnInfoReader.readValue(info.stdout())
                }
            }

    override fun isApplicableProvider(vcsProvider: String) = vcsProvider.toLowerCase() in listOf("subversion", "svn")

    override fun isApplicableUrl(vcsUrl: String) = ProcessCapture("svn", "list", vcsUrl).exitValue() == 0

    override fun download(vcs: VcsInfo, version: String, targetDir: File): String {
        log.info { "Using $this version ${getVersion()}." }

        try {
            runSvnCommand(targetDir, "checkout", vcs.url, "--depth", "empty", ".")

            val revision = if (vcs.revision.isNotBlank()) {
                vcs.revision
            } else if (version.isNotBlank()) {
                try {
                    log.info { "Trying to determine revision for version: $version" }
                    val tagsList = runSvnCommand(targetDir, "list", "${vcs.url}/tags")
                            .stdout().trim().lineSequence()
                    val tagName = tagsList.firstOrNull {
                        val trimmedTag = it.trimEnd('/')
                        trimmedTag.endsWith(version)
                                || trimmedTag.endsWith(version.replace('.', '_'))
                    }

                    val xml = runSvnCommand(targetDir,
                            "log",
                            "${vcs.url}/tags/$tagName",
                            "--xml").stdout().trim()
                    val valueType = xmlMapper.typeFactory
                            .constructCollectionType(List::class.java, SubversionLogEntry::class.java)
                    val logEntries: List<SubversionLogEntry> = xmlMapper.readValue(xml, valueType)
                    logEntries.firstOrNull()?.revision ?: ""
                } catch (e: IOException) {
                    if (Main.stacktrace) {
                        e.printStackTrace()
                    }

                    log.warn {
                        "Could not determine revision for version: $version. Falling back to fetching everything."
                    }
                    ""
                }
            } else {
                ""
            }

            if (vcs.path.isNotBlank()) {
                // In case of sparse checkout, destination directory needs to exists,
                // `svn update` will fail otherwise (if dest dir is deeper than one level).
                targetDir.resolve(vcs.path).mkdirs()
            }

            if (revision.isNotBlank()) {
                runSvnCommand(targetDir, "update", "-r", revision, "--set-depth", "infinity", vcs.path)
            } else {
                runSvnCommand(targetDir, "update", "--set-depth", "infinity", vcs.path)
            }

            return Subversion.getWorkingTree(targetDir).getRevision()
        } catch (e: IOException) {
            if (Main.stacktrace) {
                e.printStackTrace()
            }

            log.error { "Could not checkout ${vcs.url}: ${e.message}" }
            throw DownloadException("Could not checkout ${vcs.url}.", e)
        }
    }

    private fun runSvnCommand(workingDir: File, vararg args: String) =
            ProcessCapture(workingDir, "svn", *args).requireSuccess()
}
