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
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.VcsInfo
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.getCommandVersion
import com.here.ort.utils.log
import com.here.ort.utils.xmlMapper

import java.io.File
import java.io.IOException
import java.util.regex.Pattern

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

object Subversion : VersionControlSystem() {
    override val movingRevisionNames = emptyList<String>()

    override fun getVersion(): String {
        val versionRegex = Pattern.compile("svn, [Vv]ersion (?<version>[\\d.]+) \\(r\\d+\\)")

        return getCommandVersion("svn") {
            versionRegex.matcher(it.lineSequence().first()).let {
                if (it.matches()) {
                    it.group("version")
                } else {
                    ""
                }
            }
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

                override fun isShallow() = false

                override fun getRemoteUrl() = runSvnInfoCommand()?.repository?.root ?: ""

                override fun getRevision() = runSvnInfoCommand()?.commit?.revision ?: ""

                override fun getRootPath() = runSvnInfoCommand()?.workingCopy?.absolutePath ?: ""

                override fun listRemoteTags(): List<String> {
                    // Assume the recommended layout that has "branches", "tags", and "trunk" at the root level, see
                    // http://svnbook.red-bean.com/en/1.7/svn-book.html#svn.tour.importing.layout
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

    override fun download(vcs: VcsInfo, version: String, targetDir: File, allowMovingRevisions: Boolean): WorkingTree {
        log.info { "Using $this version ${getVersion()}." }

        try {
            // Create an empty working tree of the latest revision to allow sparse checkouts.
            runSvnCommand(targetDir, "checkout", vcs.url, "--depth", "empty", ".")

            return if (allowMovingRevisions || isFixedRevision(vcs.revision)) {
                if (vcs.path.isBlank()) {
                    // Deepen everything as we do not know whether the revision is contained in branches, tags or trunk.
                    runSvnCommand(targetDir, "update", "-r", vcs.revision, "--set-depth", "infinity")
                    getWorkingTree(targetDir)
                } else {
                    // Deepen only the given path.
                    runSvnCommand(targetDir, "update", "-r", vcs.revision, "--depth", "infinity", "--parents", vcs.path)

                    // Only return that part of the working tree that has the right revision.
                    getWorkingTree(File(targetDir, vcs.path))
                }
            } else {
                log.info { "Trying to guess $this revision for version '$version'." }

                val revision = getWorkingTree(targetDir).guessRevisionNameForVersion(version)
                if (revision.isBlank()) {
                    throw IOException("Unable to determine a revision to checkout.")
                }

                log.info { "Found $this revision '$revision' for version '$version'." }

                // In Subversion, tags are not symbolic names for revisions but names of directories containing
                // snapshots, checking out a tag just is a sparse checkout of that path.
                val tagPath = "tags/" + revision
                runSvnCommand(targetDir, "update", "--depth", "infinity", "--parents", tagPath + "/" + vcs.path)

                // Only return that part of the working tree that has the right revision.
                getWorkingTree(File(targetDir, tagPath))
            }
        } catch (e: IOException) {
            if (com.here.ort.utils.printStackTrace) {
                e.printStackTrace()
            }

            throw DownloadException("$this failed to download from URL '${vcs.url}'.", e)
        }
    }

    private fun runSvnCommand(workingDir: File, vararg args: String) =
            ProcessCapture(workingDir, "svn", *args).requireSuccess()
}
