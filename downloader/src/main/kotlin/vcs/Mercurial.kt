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

import com.here.ort.downloader.DownloadException
import com.here.ort.downloader.Main
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.VcsInfo
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.getCommandVersion
import com.here.ort.utils.log
import com.vdurmont.semver4j.Semver

import java.io.File
import java.io.IOException

object Mercurial : VersionControlSystem() {
    private const val EXTENSION_LARGE_FILES = "largefiles = "
    private const val EXTENSION_SPARSE = "sparse = "

    override fun getVersion(): String {
        val mercurialVersionRegex = Regex("Mercurial .*\\([Vv]ersion (?<version>[\\d.]+)\\)")

        return getCommandVersion("hg") {
            mercurialVersionRegex.matchEntire(it.lineSequence().first())?.groups?.get("version")?.value ?: ""
        }
    }

    override fun getWorkingTree(vcsDirectory: File) =
            object : WorkingTree(vcsDirectory) {
                override fun isValid(): Boolean {
                    if (!workingDir.isDirectory) {
                        return false
                    }

                    val repositoryRoot = runMercurialCommand(workingDir, "root").stdout().trimEnd()
                    return workingDir.path.startsWith(repositoryRoot)
                }

                override fun getRemoteUrl() =
                        runMercurialCommand(workingDir, "paths", "default").stdout().trimEnd()

                override fun getRevision() =
                        runMercurialCommand(workingDir, "--debug", "id", "-i").stdout().trimEnd()

                override fun getRootPath() = runMercurialCommand(workingDir, "root").stdout().trimEnd()
                        .replace(File.separatorChar, '/')

                override fun listRemoteTags(): List<String> {
                    // Mercurial does not have the concept of global remote tags. Its "regular tags" are defined per
                    // branch as part of the committed ".hgtags" file. See https://stackoverflow.com/a/2059189/1127485.
                    runMercurialCommand(workingDir, "pull", "-r", "default")
                    val tags = runMercurialCommand(workingDir, "cat", "-r", "default", ".hgtags")
                            .stdout().trimEnd()
                    return tags.lines().map {
                        it.split(' ').last()
                    }.sorted()
                }
            }

    override fun isApplicableProvider(vcsProvider: String) = vcsProvider.toLowerCase() in listOf("mercurial", "hg")

    override fun isApplicableUrl(vcsUrl: String) = ProcessCapture("hg", "identify", vcsUrl).exitValue() == 0

    override fun download(vcs: VcsInfo, version: String, targetDir: File): WorkingTree {
        log.info { "Using $this version ${getVersion()}." }

        try {
            // We cannot detect beforehand if the Large Files extension would be required, so enable it by default.
            val extensionsList = mutableListOf(EXTENSION_LARGE_FILES)

            if (vcs.path.isNotBlank() && isAtLeastVersion("4.3")) {
                // Starting with version 4.3 Mercurial has experimental built-in support for sparse checkouts, see
                // https://www.mercurial-scm.org/wiki/WhatsNew#Mercurial_4.3_.2F_4.3.1_.282017-08-10.29
                extensionsList.add(EXTENSION_SPARSE)
            }

            runMercurialCommand(targetDir, "init")
            File(targetDir, ".hg/hgrc").writeText("""
                [paths]
                default = ${vcs.url}
                [extensions]

                """.trimIndent() + extensionsList.joinToString(separator = "\n"))

            if (extensionsList.contains(EXTENSION_SPARSE)) {
                log.info { "Configuring Mercurial to do sparse checkout of path '${vcs.path}'." }
                runMercurialCommand(targetDir, "debugsparse", "-I", "${vcs.path}/**")
            }

            val workingTree = getWorkingTree(targetDir)

            val revision = if (vcs.revision.isNotBlank()) {
                vcs.revision
            } else {
                log.info { "Trying to guess $this revision for version '$version'." }
                workingTree.guessRevisionNameForVersion(version).also { revision ->
                    if (revision.isBlank()) {
                        throw IOException("Unable to determine a revision to checkout.")
                    }

                    log.info { "Found $this revision '$revision' for version '$version'." }
                }
            }

            // To safe network bandwidth, only pull exactly the revision we want. Do not use "-u" to update the
            // working tree just yet, as Mercurial would only update if new changesets were pulled. But that might
            // not be the case if the requested revision is already available locally.
            runMercurialCommand(targetDir, "pull", "-r", revision)

            // Explicitly update the working tree to the desired revision.
            runMercurialCommand(targetDir, "update", revision)

            return workingTree
        } catch (e: IOException) {
            if (Main.stacktrace) {
                e.printStackTrace()
            }

            throw DownloadException("$this failed to download from URL '${vcs.url}'.", e)
        }
    }

    fun isAtLeastVersion(version: String): Boolean {
        val mercurialVersion = Semver(getVersion(), Semver.SemverType.LOOSE)
        return mercurialVersion.isGreaterThanOrEqualTo(Semver(version, Semver.SemverType.LOOSE))
    }

    private fun runMercurialCommand(workingDir: File, vararg args: String) =
            ProcessCapture(workingDir, "hg", *args).requireSuccess()
}
