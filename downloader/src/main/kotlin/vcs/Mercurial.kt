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

import com.here.ort.downloader.DownloadException
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.downloader.WorkingTree
import com.here.ort.model.Package
import com.here.ort.spdx.LICENSE_FILE_NAMES
import com.here.ort.utils.CommandLineTool
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.log

import java.io.File
import java.io.IOException
import java.util.regex.Pattern

const val MERCURIAL_LARGE_FILES_EXTENSION = "largefiles = "
const val MERCURIAL_SPARSE_EXTENSION = "sparse = "

class Mercurial : VersionControlSystem(), CommandLineTool {
    private val versionRegex = Pattern.compile("Mercurial .*\\([Vv]ersion (?<version>[\\d.]+)\\)")

    override val aliases = listOf("mercurial", "hg")
    override val latestRevisionNames = listOf("tip")

    override fun command(workingDir: File?) = "hg"

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
                override fun isValid(): Boolean {
                    if (!workingDir.isDirectory) {
                        return false
                    }

                    // Do not use runMercurialCommand() here as we do not require the command to succeed.
                    val hgRootPath = ProcessCapture(workingDir, "hg", "root")
                    return hgRootPath.isSuccess && workingDir.path.startsWith(hgRootPath.stdout.trimEnd())
                }

                override fun isShallow() = false

                override fun getRemoteUrl() = run(workingDir, "paths", "default").stdout.trimEnd()

                override fun getRevision() = run(workingDir, "--debug", "id", "-i").stdout.trimEnd()

                override fun getRootPath() = File(run(workingDir, "root").stdout.trimEnd())

                override fun listRemoteBranches(): List<String> {
                    val branches = run(workingDir, "branches").stdout.trimEnd()
                    return branches.lines().map {
                        it.split(' ').first()
                    }.sorted()
                }

                override fun listRemoteTags(): List<String> {
                    // Mercurial does not have the concept of global remote tags. Its "regular tags" are defined per
                    // branch as part of the committed ".hgtags" file. See https://stackoverflow.com/a/2059189/1127485.
                    run(workingDir, "pull", "-r", "default")
                    val tags = run(workingDir, "cat", "-r", "default", ".hgtags").stdout.trimEnd()
                    return tags.lines().map {
                        it.split(' ').last()
                    }.sorted()
                }
            }

    override fun isApplicableUrlInternal(vcsUrl: String) =
            ProcessCapture("hg", "identify", vcsUrl).isSuccess

    override fun download(pkg: Package, targetDir: File, allowMovingRevisions: Boolean,
                          recursive: Boolean): WorkingTree {
        log.info { "Using $type version ${getVersion()}." }

        try {
            // We cannot detect beforehand if the Large Files extension would be required, so enable it by default.
            val extensionsList = mutableListOf(MERCURIAL_LARGE_FILES_EXTENSION)

            if (pkg.vcsProcessed.path.isNotBlank() && isAtLeastVersion("4.3")) {
                // Starting with version 4.3 Mercurial has experimental built-in support for sparse checkouts, see
                // https://www.mercurial-scm.org/wiki/WhatsNew#Mercurial_4.3_.2F_4.3.1_.282017-08-10.29
                extensionsList += MERCURIAL_SPARSE_EXTENSION
            }

            run(targetDir, "init")
            File(targetDir, ".hg/hgrc").writeText("""
                [paths]
                default = ${pkg.vcsProcessed.url}
                [extensions]

                """.trimIndent() + extensionsList.joinToString("\n"))

            if (MERCURIAL_SPARSE_EXTENSION in extensionsList) {
                log.info { "Configuring Mercurial to do sparse checkout of path '${pkg.vcsProcessed.path}'." }
                run(targetDir, "debugsparse", "-I", "${pkg.vcsProcessed.path}/**",
                        *LICENSE_FILE_NAMES.flatMap { listOf("-I", it) }.toTypedArray())
            }

            val workingTree = getWorkingTree(targetDir)

            val revision = if (allowMovingRevisions || isFixedRevision(workingTree, pkg.vcsProcessed.revision)) {
                pkg.vcsProcessed.revision
            } else {
                log.info { "Trying to guess a $type revision for version '${pkg.id.version}'." }
                try {
                    workingTree.guessRevisionName(pkg.id.name, pkg.id.version).also { revision ->
                        log.warn {
                            "Using guessed $type revision '$revision' for version '${pkg.id.version}'. This might " +
                                    "cause the downloaded source code to not match the package version."
                        }
                    }
                } catch (e: IOException) {
                    throw IOException("Unable to determine a revision to checkout.", e)
                }
            }

            // To safe network bandwidth, only pull exactly the revision we want. Do not use "-u" to update the
            // working tree just yet, as Mercurial would only update if new changesets were pulled. But that might
            // not be the case if the requested revision is already available locally.
            run(targetDir, "pull", "-r", revision)

            // Explicitly update the working tree to the desired revision.
            run(targetDir, "update", revision)

            return workingTree
        } catch (e: IOException) {
            throw DownloadException("$type failed to download from URL '${pkg.vcsProcessed.url}'.", e)
        }
    }
}
