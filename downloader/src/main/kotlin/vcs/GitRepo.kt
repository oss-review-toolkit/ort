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
import com.here.ort.util.ProcessCapture
import com.here.ort.util.log

import java.io.File
import java.io.IOException

object GitRepo : GitBase() {
    override fun getWorkingDirectory(vcsDirectory: File) =
            super.getWorkingDirectory(File(vcsDirectory, ".repo/manifests"))

    override fun isApplicableProvider(vcsProvider: String) =
            vcsProvider.toLowerCase() in listOf("gitrepo", "git-repo", "repo")

    override fun isApplicableUrl(vcsUrl: String) = false

    /**
     * Clones the Git repositories defined in the manifest file using the Git Repo tool.
     *
     * @param vcsPath The path to the repo manifest file in the repository. Defaults to "manifest.xml" if not provided.
     *
     * @throws DownloadException In case the download failed.
     */
    override fun download(vcsUrl: String, vcsRevision: String?, vcsPath: String?, version: String, targetDir: File)
            : String? {
        val revision = if (vcsRevision != null && vcsRevision.isNotEmpty()) vcsRevision else "master"
        val manifestPath = if (vcsPath != null && vcsPath.isNotEmpty()) vcsPath else "manifest.xml"

        try {
            log.debug { "Initialize git-repo from $vcsUrl with branch $revision and manifest $manifestPath." }
            runRepoCommand(targetDir, "init", "--depth", "1", "-b", revision, "-u", vcsUrl, "-m", manifestPath)

            log.debug { "Start git-repo sync." }
            runRepoCommand(targetDir, "sync", "-c")

            return getWorkingDirectory(targetDir).getRevision()
        } catch (e: IOException) {
            if (Main.stacktrace) {
                e.printStackTrace()
            }

            throw DownloadException("Could not clone $vcsUrl/$manifestPath", e)
        }
    }

    private fun runRepoCommand(targetDir: File, vararg args: String) {
        ProcessCapture(targetDir, "repo", *args).requireSuccess()
    }
}
