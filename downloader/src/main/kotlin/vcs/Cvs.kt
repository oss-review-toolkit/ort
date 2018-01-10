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
import com.here.ort.downloader.DownloadException

import com.here.ort.downloader.Main
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.downloader.WorkingTree
import com.here.ort.downloader.WorkingTreeWithFileRevisions
import com.here.ort.model.VcsInfo
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.getCommandVersion
import com.here.ort.utils.log

import java.io.File
import java.io.IOException

object Cvs : VersionControlSystem<WorkingTreeWithFileRevisions>() {
    override fun getVersion(): String {
        val cvsVersionRegex = Regex("Concurrent Versions System \\(CVS\\) (?<version>[\\d.]+).+")

        return getCommandVersion("cvs") {
            cvsVersionRegex.matchEntire(it.lineSequence().first())?.groups?.get("version")?.value ?: ""
        }
    }

    override fun getWorkingTree(vcsDirectory: File) =
            object : WorkingTreeWithFileRevisions(vcsDirectory, this@Cvs.toString()) {
                override fun listRemoteTags(): List<String> {
                    val logOut = runCvsCommand(workingDir, "log", "-h").stdout()
                    var tagsSectionStarted = false
                    val tags = logOut.lineSequence().mapNotNull {
                        if (tagsSectionStarted) {
                            if (!it.startsWith("\t")) {
                                tagsSectionStarted = false
                                null
                            } else {
                                it.trim().split(": ")[0]
                            }
                        } else {
                            if (it.trim() == "symbolic names:") {
                                tagsSectionStarted = true
                            }
                            null
                        }
                    }
                    return tags.toSet().toList().sorted()
                }

                override fun getRevision(file: File): String {
                    if (file.isDirectory) {
                        throw IOException("$file is a directory, ${this@Cvs} provides only revisions for files")
                    }

                    val commandOutput = runCvsCommand(workingDir, "status", file.relativeTo(workingDir).path)
                            .stdout()
                            .lineSequence()
                    val matched = commandOutput.mapNotNull {
                        Regex("Working revision:\\s+(?<revision>[\\d.]+)")
                                .matchEntire(it.trim())?.groups?.get("revision")?.value
                    }

                    return if (matched.count() > 0) matched.first() else ""
                }

                private val cvsDir = File(workingDir, "CVS")

                override fun isValid() = ProcessCapture(workingDir, "cvs", "status").exitValue() == 0

                override fun getRemoteUrl() = File(cvsDir, "Root").useLines { it.first().substringAfter("/") }

                override fun getRootPath(): String {
                    val cvsRepository = File(cvsDir, "Repository").useLines { it.first() }
                            .replace("/", File.separator).substringAfter(File.separator)
                    return workingDir.absolutePath.substringBeforeLast(cvsRepository)
                }
            }

    override fun isApplicableProvider(vcsProvider: String) = vcsProvider.equals("cvs", true)

    override fun isApplicableUrl(vcsUrl: String) = vcsUrl.matches(":(ext|pserver):\\w+@[\\w.]+:[\\/\\w]+".toRegex())

    override fun download(vcs: VcsInfo, version: String, targetDir: File): WorkingTree {
        try {
            log.info { "Init ${this@Cvs} workdir (${targetDir.absolutePath}) for '${vcs.url}'" }

            runCvsCommand(targetDir, "-d", vcs.url, "co", "-l", ".")
            if (vcs.revision.isNotBlank()) {

                // Only files, for which specified revision exists, will be checked out.
                log.info { "Trying to checkout  files with revision: '${vcs.revision}'" }
                runCvsCommand(targetDir, "co", "-r", vcs.revision, vcs.path)
            } else {
                if (version.isNotBlank()) {

                    // As for now, version will be treated as snapshot name
                    log.info { "Trying to checkout snapshot: '$version'" }

                    // Add -f to force HEAD if snapshot not found.
                    runCvsCommand(targetDir, "co", "-r", version, "-f", vcs.path)
                } else {
                    log.info { "Trying to checkout HEAD for all files in ${vcs.path}'" }
                    runCvsCommand(targetDir, "co", if (vcs.path.isNotBlank()) vcs.path else ".")
                }
            }
            return getWorkingTree(targetDir)
        } catch (e: Exception) {
            if (Main.stacktrace) {
                e.printStackTrace()
            }

            log.error { "Could not checkout from '${vcs.url}': ${e.message}" }
            throw DownloadException("Could not checkout from '${vcs.url}': ${e.message}")
        }
    }

    private fun runCvsCommand(workingDir: File, vararg args: String): ProcessCapture =
            ProcessCapture(workingDir, "cvs", *args).requireSuccess()
}
