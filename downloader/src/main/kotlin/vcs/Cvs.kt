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
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.Package
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.getCommandVersion
import com.here.ort.utils.log
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.searchUpwardsForSubdirectory

import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.DigestUtils

import java.io.File
import java.io.IOException
import java.util.regex.Pattern

typealias CvsFileRevisions = List<Pair<String, String>>

object Cvs : VersionControlSystem() {
    override val aliases = listOf("cvs")
    override val commandName = "cvs"
    override val movingRevisionNames = emptyList<String>()

    override fun getVersion(): String {
        val versionRegex = Pattern.compile("Concurrent Versions System \\(CVS\\) (?<version>[\\d.]+).+")

        return getCommandVersion("cvs") {
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
                private val cvsDirectory = File(workingDir, "CVS")

                override fun isValid(): Boolean {
                    if (!workingDir.isDirectory) {
                        return false
                    }

                    return ProcessCapture(workingDir, "cvs", "status", "-l").exitValue() == 0
                }

                override fun isShallow() = false

                override fun getRemoteUrl() = File(cvsDirectory, "Root").useLines { it.first() }

                override fun getRevision() =
                        // CVS does not have the concept of a global revision, but each file has its own revision. As
                        // we just use the revision to uniquely identify the state of a working tree, simply create an
                        // artificial single revision based on the revisions of all files.
                        getFileRevisionsHash(getFileRevisions())

                private fun getFileRevisions(): CvsFileRevisions {
                    val cvsLog = run(workingDir, "log", "-h")

                    var currentWorkingFile = ""
                    return cvsLog.stdout().lines().mapNotNull { line ->
                        var value = line.removePrefix("Working file: ")
                        if (value.length < line.length) {
                            currentWorkingFile = value
                        } else {
                            value = line.removePrefix("head: ")
                            if (value.length < line.length) {
                                if (currentWorkingFile.isNotBlank() && value.isNotBlank()) {
                                    return@mapNotNull Pair(currentWorkingFile, value)
                                }
                            }
                        }

                        null
                    }.sortedBy { it.first }
                }

                private fun getFileRevisionsHash(fileRevisions: CvsFileRevisions): String {
                    val digest = fileRevisions.fold(DigestUtils.getSha1Digest()) { digest, (file, revision) ->
                        DigestUtils.updateDigest(digest, file)
                        DigestUtils.updateDigest(digest, revision)
                    }.digest()

                    return Hex.encodeHexString(digest)
                }

                override fun getRootPath(): String {
                    val rootDir = workingDir.searchUpwardsForSubdirectory("CVS")?.toString() ?: ""
                    return rootDir.replace(File.separatorChar, '/')
                }

                override fun listRemoteTags(): List<String> {
                    val cvsLog = run(workingDir, "log", "-h")
                    var tagsSectionStarted = false

                    val tagsList = cvsLog.stdout().lines().mapNotNull {
                        if (tagsSectionStarted) {
                            if (it.startsWith('\t')) {
                                it.split(':').map { it.trim() }.firstOrNull()
                            } else {
                                tagsSectionStarted = false
                                null
                            }
                        } else {
                            if (it == "symbolic names:") {
                                tagsSectionStarted = true
                            }
                            null
                        }
                    }

                    return tagsList.toSortedSet().toList()
                }
            }

    override fun isApplicableUrl(vcsUrl: String) = vcsUrl.matches("^:(ext|pserver):[^@]+@.+$".toRegex())

    override fun download(pkg: Package, targetDir: File, allowMovingRevisions: Boolean,
                          recursive: Boolean): WorkingTree {
        log.info { "Using $this version ${getVersion()}." }

        try {
            val path = if (pkg.vcsProcessed.path.isBlank()) "." else pkg.vcsProcessed.path

            // Create a "fake" checkout as described at https://stackoverflow.com/a/3448891/1127485.
            run(targetDir, "-z3", "-d", pkg.vcsProcessed.url, "checkout", "-l", ".")
            val workingTree = getWorkingTree(targetDir)

            val revision = if (allowMovingRevisions || isFixedRevision(pkg.vcsProcessed.revision)) {
                pkg.vcsProcessed.revision
            } else {
                // Create all working tree directories in order to be able to query the log.
                run(targetDir, "update", "-d")

                log.info { "Trying to guess a $this revision for version '${pkg.id.version}'." }

                workingTree.guessRevisionName(pkg.id.name, pkg.id.version).also {
                    if (it.isBlank()) {
                        throw IOException("Unable to determine a revision to checkout.")
                    }

                    log.warn {
                        "Using guessed $this revision '$it' for version '${pkg.id.version}'. This might cause the " +
                                "downloaded source code to not match the package version."
                    }

                    // Clean the temporarily updated working tree again.
                    targetDir.listFiles().forEach {
                        if (it.isDirectory) {
                            if (it.name != "CVS") it.safeDeleteRecursively()
                        } else {
                            it.delete()
                        }
                    }
                }
            }

            // Checkout the working tree of the desired revision.
            run(targetDir, "checkout", "-r", revision, path)

            return workingTree
        } catch (e: IOException) {
            if (com.here.ort.utils.printStackTrace) {
                e.printStackTrace()
            }

            throw DownloadException("$this failed to download from URL '${pkg.vcsProcessed.url}'.", e)
        }
    }
}
