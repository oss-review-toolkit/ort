/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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
import com.fasterxml.jackson.module.kotlin.readValue

import com.here.ort.downloader.DownloadException
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.downloader.WorkingTree
import com.here.ort.model.Package
import com.here.ort.model.xmlMapper
import com.here.ort.utils.CommandLineTool
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.log

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
data class SubversionPathEntry(
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

class Subversion : VersionControlSystem(), CommandLineTool {
    private val versionRegex = Pattern.compile("svn, [Vv]ersion (?<version>[\\d.]+) \\(r\\d+\\)")

    override val aliases = listOf("subversion", "svn")
    override val latestRevisionNames = listOf("HEAD")

    override fun command(workingDir: File?) = "svn"

    override fun getVersion() =
            getVersion { output ->
                versionRegex.matcher(output.lineSequence().first()).let {
                    if (it.matches()) {
                        it.group("version")
                    } else {
                        ""
                    }
                }
            }

    override fun getWorkingTree(vcsDirectory: File) =
            object : WorkingTree(vcsDirectory, type) {
                private val directoryNamespaces = listOf("branches", "tags", "trunk", "wiki")
                private val svnInfoReader = xmlMapper.readerFor(SubversionInfoEntry::class.java)
                        .with(DeserializationFeature.UNWRAP_ROOT_VALUE)

                override fun isValid(): Boolean {
                    if (!workingDir.isDirectory) {
                        return false
                    }

                    return runSvnInfoCommand() != null
                }

                override fun isShallow() = false

                override fun getRemoteUrl() = runSvnInfoCommand()?.url ?: ""

                override fun getRevision() = runSvnInfoCommand()?.commit?.revision ?: ""

                override fun getRootPath() =
                        runSvnInfoCommand()?.workingCopy?.absolutePath?.let { File(it) } ?: workingDir

                private fun listRemoteRefs(namespace: String): List<String> {
                    val remoteUrl = getRemoteUrl()

                    val projectRoot = if (directoryNamespaces.any { "/$it/" in remoteUrl }) {
                        runSvnInfoCommand()?.repository?.root ?: ""
                    } else {
                        remoteUrl
                    }

                    return try {
                        val svnPathInfo = run(workingDir, "info", "--xml", "--depth=immediates",
                                "$projectRoot/$namespace")
                        val pathEntries = xmlMapper.readValue<List<SubversionPathEntry>>(svnPathInfo.stdout)

                        // As the "immediates" depth includes the parent namespace itself, too, drop it.
                        pathEntries.drop(1).map { it.path }.sorted()
                    } catch (e: IOException) {
                        // We assume a single project directory layout above. If we fail, e.g. for a multi-project
                        // layout whose directory names cannot be easily guessed from the project names, return an
                        // empty list to give a later curation the chance to succeed.
                        emptyList()
                    }
                }

                override fun listRemoteBranches() = listRemoteRefs("branches")

                override fun listRemoteTags() = listRemoteRefs("tags")

                private fun runSvnInfoCommand(): SubversionInfoEntry? {
                    val info = ProcessCapture("svn", "info", "--xml", workingDir.absolutePath)
                    return if (info.isSuccess) svnInfoReader.readValue(info.stdout) else null
                }
            }

    override fun isApplicableUrlInternal(vcsUrl: String) =
            (vcsUrl.startsWith("svn+") || ProcessCapture("svn", "list", vcsUrl).isSuccess)

    override fun download(pkg: Package, targetDir: File, allowMovingRevisions: Boolean,
                          recursive: Boolean): WorkingTree {
        log.info { "Using $type version ${getVersion()}." }

        val workingTree = try {
            // Create an empty working tree of the latest revision to allow sparse checkouts.
            run(targetDir, "checkout", pkg.vcsProcessed.url, "--depth", "empty", ".")

            var revision = pkg.vcsProcessed.revision.takeIf { it.isNotBlank() } ?: "HEAD"

            val workingTree = getWorkingTree(targetDir)
            if (allowMovingRevisions || isFixedRevision(workingTree, revision)) {
                if (pkg.vcsProcessed.path.isBlank()) {
                    // Deepen everything as we do not know whether the revision is contained in branches, tags or trunk.
                    run(targetDir, "update", "-r", revision, "--set-depth", "infinity")
                    workingTree
                } else {
                    // Deepen only the given path.
                    run(targetDir, "update", "-r", revision, "--depth", "infinity", "--parents",
                            pkg.vcsProcessed.path)

                    // Only return that part of the working tree that has the right revision.
                    getWorkingTree(File(targetDir, pkg.vcsProcessed.path))
                }
            } else {
                val tagPath: String
                val path: String

                val tagsIndex = pkg.vcsProcessed.path.indexOf("tags/")
                if (tagsIndex == 0 || (tagsIndex > 0 && pkg.vcsProcessed.path[tagsIndex - 1] == '/')) {
                    log.info {
                        "Ignoring the $type revision '${pkg.vcsProcessed.revision}' as the path points to a tag."
                    }

                    val tagsPathIndex = pkg.vcsProcessed.path.indexOf('/', tagsIndex + "tags/".length)

                    tagPath = pkg.vcsProcessed.path.let {
                        if (tagsPathIndex < 0) it else it.substring(0, tagsPathIndex)
                    }
                    path = pkg.vcsProcessed.path
                } else {
                    log.info { "Trying to guess a $type revision for version '${pkg.id.version}'." }

                    revision = try {
                        getWorkingTree(targetDir).guessRevisionName(pkg.id.name, pkg.id.version)
                    } catch (e: IOException) {
                        throw IOException("Unable to determine a revision to checkout.", e)
                    }

                    log.warn {
                        "Using guessed $type revision '$revision' for version '${pkg.id.version}'. This might cause " +
                                "the downloaded source code to not match the package version."
                    }

                    tagPath = "tags/$revision"
                    path = "$tagPath/${pkg.vcsProcessed.path}"
                }

                // In Subversion, tags are not symbolic names for revisions but names of directories containing
                // snapshots, checking out a tag just is a sparse checkout of that path.
                run(targetDir, "update", "--depth", "infinity", "--parents", path)

                // Only return that part of the working tree that has the right revision.
                getWorkingTree(File(targetDir, tagPath))
            }
        } catch (e: IOException) {
            throw DownloadException("$type failed to download from ${pkg.vcsProcessed.url}.", e)
        }

        return workingTree
    }
}
