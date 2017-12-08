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

import com.here.ort.downloader.Main
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.downloader.WorkingDirectoryWithFileRevisions
import com.here.ort.util.ProcessCapture
import com.here.ort.util.getCommandVersion
import com.here.ort.util.log

import java.io.File
import java.io.IOException

object Cvs : VersionControlSystem<WorkingDirectoryWithFileRevisions>() {
    override fun getVersion(): String {
        val mercurialVersionRegex = Regex("Concurrent Versions System \\(CVS\\) (?<version>[\\d.]+).+")

        return getCommandVersion("cvs") {
            mercurialVersionRegex.matchEntire(it.lineSequence().first())?.groups?.get("version")?.value ?: ""
        }
    }

    override fun getWorkingDirectory(vcsDirectory: File) =
            object : WorkingDirectoryWithFileRevisions(vcsDirectory, this@Cvs.toString()) {
                override fun getRevision(file: File): String {
                    if (file.isDirectory) {
                        throw IOException("$file is a directory, " +
                                                  "${this@Cvs.toString()} provides only revisions for files")
                    }

                    val commandOutput = runCvsCommand(workingDir, "status", file.relativeTo(workingDir).path)
                            .stdout()
                            .lineSequence()
                    val revisionPrefix = "Working revision:"
                    return commandOutput.first { it.trim().startsWith(revisionPrefix) }
                            .trim().removePrefix(revisionPrefix).trim()
                }

                private val cvsDir = File(workingDir, "CVS")

                override fun isValid(): Boolean
                        = ProcessCapture(workingDir, "cvs", "status").exitValue() == 0

                override fun getRemoteUrl(): String = File(cvsDir, "Root").readLines().first().substringAfter("/")

                override fun getRootPath(path: File): String {
                    val cvsRepository = File(cvsDir, "Repository").readLines().first()
                            .replace("/", File.separator).substringAfter(File.separator)
                    return workingDir.absolutePath.substringBeforeLast(cvsRepository)
                }

                override fun getPathToRoot(path: File): String {
                    val parent = if (path.isDirectory) {
                        path.path
                    } else {
                        path.parent
                    }
                    return File(parent, "CVS/Repository").readLines().first()
                }
            }

    override fun isApplicableProvider(vcsProvider: String) = vcsProvider.equals("cvs", true)

    override fun isApplicableUrl(vcsUrl: String) = vcsUrl.matches(":(ext|pserver):\\w+@[\\w.]+:[\\/\\w]+".toRegex())

    override fun download(vcsUrl: String, vcsRevision: String?, vcsPath: String?, version: String, targetDir: File)
            : String? {
        try {
            log.info { "Init ${this@Cvs.toString()} workdir (${targetDir.absolutePath}) for '$vcsUrl'" }

            runCvsCommand(targetDir, "-d", vcsUrl, "co", "-l", ".")
            if (vcsRevision != null && vcsRevision.isNotBlank()) {

                // Only files, for which specified revision exists, will be checked out.
                log.info { "Trying to checkout  files with revision: '$vcsRevision'" }
                runCvsCommand(targetDir, "co", "-r", vcsRevision, vcsPath ?: ".")
            } else {
                if (version.isNotBlank()) {

                    // As for now, version will be treated as snapshot name
                    log.info { "Trying to checkout snapshot: '$version'" }

                    // Add -f to force HEAD if snapshot not found.
                    runCvsCommand(targetDir, "co", "-r", version, "-f", vcsPath ?: ".")
                } else {
                    log.info { "Trying to checkout HEAD for all files in ${vcsPath ?: "."}'" }
                    runCvsCommand(targetDir, "co", vcsPath ?: ".")
                }
            }
        } catch (e: Exception) {
            if (Main.stacktrace) {
                e.printStackTrace()
            }

            log.warn {
                "Could not checkout from '$vcsUrl': ${e.message}"
            }
        }
        return null;
    }

    private fun runCvsCommand(workingDir: File, vararg args: String): ProcessCapture =
            ProcessCapture(workingDir, "cvs", *args).requireSuccess()
}
