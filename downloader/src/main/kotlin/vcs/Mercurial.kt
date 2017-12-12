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
        val mercurialVersionRegex = Regex("Mercurial .*\\(version (?<version>[\\d.]+)\\)")

        return getCommandVersion("hg") {
            mercurialVersionRegex.matchEntire(it.lineSequence().first())?.groups?.get("version")?.value ?: ""
        }
    }

    override fun getWorkingDirectory(vcsDirectory: File) =
            object : WorkingDirectory(vcsDirectory) {
                override fun isValid(): Boolean {
                    val repositoryRoot = runMercurialCommand(workingDir, "root").stdout().trim()
                    return workingDir.path.startsWith(repositoryRoot)
                }

                override fun getRemoteUrl() =
                        runMercurialCommand(workingDir, "paths", "default").stdout().trimEnd()

                override fun getRevision() = runMercurialCommand(workingDir, "id", "-i").stdout().trimEnd()

                override fun getRootPath(path: File) = runMercurialCommand(workingDir, "root").stdout().trimEnd()

                override fun getPathToRoot(path: File): String {
                    val absolutePath = if (path.isAbsolute || path == workingDir) {
                        path
                    } else {
                        workingDir.resolve(path)
                    }

                    return absolutePath.relativeTo(File(getRootPath(absolutePath))).path
                }
            }

    override fun isApplicableProvider(vcsProvider: String) = vcsProvider.toLowerCase() in listOf("mercurial", "hg")

    override fun isApplicableUrl(vcsUrl: String) = ProcessCapture("hg", "identify", vcsUrl).exitValue() == 0

    override fun download(vcsUrl: String, vcsRevision: String?, vcsPath: String?, version: String, targetDir: File)
            : String {
        log.info { "Using $this version ${getVersion()}." }

        val revisionCmdArgs = mutableListOf<String>()

        // We cannot detect beforehand if the Large Files extension would be required, so enable it by default.
        val extensionsList = mutableListOf(EXTENSION_LARGE_FILES)

        if (vcsRevision != null && vcsRevision.isNotBlank()) {
            revisionCmdArgs.add("-r")
            revisionCmdArgs.add(vcsRevision)
        }

        if (!vcsPath.isNullOrEmpty() && isAtLeastVersion("4.3")) {
            // Starting with version 4.3 Mercurial has experimental built-in support for sparse checkouts, see
            // https://www.mercurial-scm.org/wiki/WhatsNew#Mercurial_4.3_.2F_4.3.1_.282017-08-10.29
            extensionsList.add(EXTENSION_SPARSE)
        }

        runMercurialCommand(targetDir, "init")
        File(targetDir, ".hg/hgrc").writeText("""
            [paths]
            default = $vcsUrl
            [extensions]

            """.trimIndent() + extensionsList.joinToString(separator = "\n"))

        // If this is a sparse checkout include given path.
        if (extensionsList.contains(EXTENSION_SPARSE)) {
            log.info { "Sparse checkout of '$vcsPath'." }

            try {
                runMercurialCommand(targetDir, "debugsparse", "-I", "$vcsPath/**")
            } catch (e: IOException) {
                if (Main.stacktrace) {
                    e.printStackTrace()
                }

                log.warn {
                    "Could not set sparse checkout of '$vcsPath': ${e.message}\n" +
                            "Falling back to fetching everything."
                }
            }
        }

        runMercurialCommand(targetDir, "pull")

        if (version.isNotBlank() && !revisionCmdArgs.contains("-r")) {
            log.info { "Trying to determine revision for version $version" }

            val tagRevision = runMercurialCommand(targetDir, "log", "--template={node}\\t{tags}\\n")
                    .stdout()
                    .lineSequence()
                    .map { it.split("\t") }
                    .find { it.last().endsWith(version) || it.last().endsWith(version.replace('.', '_')) }?.first()

            if (tagRevision != null) {
                log.info { "Found $tagRevision revision for version $version" }

                revisionCmdArgs.add("-r")
                revisionCmdArgs.add(tagRevision)
            } else {
                log.info { "Failed to find revision for version $version" }
            }
        }

        try {
            runMercurialCommand(targetDir, "update", *revisionCmdArgs.toTypedArray())
        } catch (e: IOException) {
            if (Main.stacktrace) {
                e.printStackTrace()
            }

            if (revisionCmdArgs.contains("-r") && revisionCmdArgs.contains(vcsRevision)) {
                log.warn {
                    "Could not fetch only '$vcsRevision': ${e.message}\n" +
                            "Falling back to fetching everything."
                }

                runMercurialCommand(targetDir, "update")
            } else {
                throw e
            }
        }

        return Mercurial.getWorkingDirectory(targetDir).getRevision()
    }

    fun isAtLeastVersion(version: String): Boolean {
        val mercurialVersion = Semver(getVersion(), Semver.SemverType.LOOSE)
        return !mercurialVersion.isLowerThan(Semver(version, Semver.SemverType.LOOSE))
    }

    private fun runMercurialCommand(workingDir: File, vararg args: String) =
            ProcessCapture(workingDir, "hg", *args).requireSuccess()
}
