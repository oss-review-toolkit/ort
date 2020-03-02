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

import com.here.ort.downloader.VersionControlSystem
import com.here.ort.downloader.WorkingTree
import com.here.ort.model.VcsInfo
import com.here.ort.model.VcsType
import com.here.ort.utils.CommandLineTool
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.searchUpwardsForSubdirectory
import com.here.ort.utils.toHexString

import java.io.File
import java.security.MessageDigest
import java.util.regex.Pattern

typealias CvsFileRevisions = List<Pair<String, String>>

class Cvs : VersionControlSystem(), CommandLineTool {
    private val versionRegex = Pattern.compile("Concurrent Versions System \\(CVS\\) (?<version>[\\d.]+).+")

    override val type = VcsType.CVS
    override val defaultBranchName = ""
    override val latestRevisionNames = emptyList<String>()

    override fun command(workingDir: File?) = "cvs"

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
            private val cvsDirectory = File(workingDir, "CVS")

            override fun isValid(): Boolean {
                if (!workingDir.isDirectory) {
                    return false
                }

                return ProcessCapture(workingDir, "cvs", "status", "-l").isSuccess
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
                return cvsLog.stdout.lines().mapNotNull { line ->
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

            private fun getFileRevisionsHash(fileRevisions: CvsFileRevisions) =
                fileRevisions.fold(MessageDigest.getInstance("SHA-1")) { digest, (file, revision) ->
                    digest.apply {
                        update(file.toByteArray())
                        update(revision.toByteArray())
                    }
                }.digest().toHexString()

            override fun getRootPath(): File {
                val rootDir = workingDir.searchUpwardsForSubdirectory("CVS")
                return rootDir ?: workingDir
            }

            private fun listSymbolicNames(): Map<String, String> =
                try {
                    // Create all working tree directories in order to be able to query the log.
                    run(workingDir, "update", "-d")

                    val cvsLog = run(workingDir, "log", "-h")
                    var tagsSectionStarted = false

                    cvsLog.stdout.lines().mapNotNull { line ->
                        if (tagsSectionStarted) {
                            if (line.startsWith('\t')) {
                                line.split(':', limit = 2).let {
                                    Pair(it.first().trim(), it.last().trim())
                                }
                            } else {
                                tagsSectionStarted = false
                                null
                            }
                        } else {
                            if (line == "symbolic names:") {
                                tagsSectionStarted = true
                            }
                            null
                        }
                    }.toMap().toSortedMap()
                } finally {
                    // Clean the temporarily updated working tree again.
                    workingDir.listFiles().forEach {
                        if (it.isDirectory) {
                            if (it.name != "CVS") it.safeDeleteRecursively()
                        } else {
                            it.delete()
                        }
                    }
                }

            private fun isBranchVersion(version: String): Boolean {
                // See http://cvsgui.sourceforge.net/howto/cvsdoc/cvs_5.html#SEC59.
                val decimals = version.split('.')

                // "Externally, branch numbers consist of an odd number of dot-separated decimal
                // integers."
                return decimals.size % 2 != 0 ||
                        // "That is not the whole truth, however. For efficiency reasons CVS sometimes inserts
                        // an extra 0 in the second rightmost position."
                        decimals.dropLast(1).last() == "0"
            }

            override fun listRemoteBranches() =
                listSymbolicNames().mapNotNull { (name, version) ->
                    name.takeIf { isBranchVersion(version) }
                }

            override fun listRemoteTags() =
                listSymbolicNames().mapNotNull { (name, version) ->
                    name.takeUnless { isBranchVersion(version) }
                }
        }

    override fun isApplicableUrlInternal(vcsUrl: String) = vcsUrl.matches(":(ext|pserver):[^@]+@.+".toRegex())

    override fun initWorkingTree(targetDir: File, vcs: VcsInfo): WorkingTree {
        // Create a "fake" checkout as described at https://stackoverflow.com/a/3448891/1127485.
        run(targetDir, "-z3", "-d", vcs.url, "checkout", "-l", ".")

        return getWorkingTree(targetDir)
    }

    override fun updateWorkingTree(workingTree: WorkingTree, revision: String, path: String, recursive: Boolean) =
        runCatching {
            // Checkout the working tree of the desired revision.
            run(workingTree.workingDir, "checkout", "-r", revision, path.takeUnless { it.isEmpty() } ?: ".")
        }.isSuccess
}
