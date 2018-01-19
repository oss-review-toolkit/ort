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
import com.here.ort.model.VcsInfo
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.log

import java.io.File
import java.io.IOException

object GitRepo : GitBase() {
    override fun getWorkingTree(vcsDirectory: File) =
            super.getWorkingTree(File(vcsDirectory, ".repo/manifests"))

    override fun isApplicableProvider(vcsProvider: String) =
            vcsProvider.toLowerCase() in listOf("gitrepo", "git-repo", "repo")

    override fun isApplicableUrl(vcsUrl: String) = false

    /**
     * Clones the Git repositories defined in the manifest file using the Git Repo tool. The manifest file is checkout
     * from the repository defined in [vcs], its location is defined by [VcsInfo.path].
     *
     * @throws DownloadException In case the download failed.
     */
    override fun download(vcs: VcsInfo, version: String, targetDir: File, allowMovingRevisions: Boolean): WorkingTree {
        val revision = if (vcs.revision.isNotBlank()) vcs.revision else "master"
        val manifestPath = if (vcs.path.isNotBlank()) vcs.path else "manifest.xml"

        try {
            log.debug { "Initialize git-repo from ${vcs.url} with branch $revision and manifest $manifestPath." }
            runRepoCommand(targetDir, "init", "--depth", "1", "-b", revision, "-u", vcs.url, "-m", manifestPath)

            log.debug { "Start git-repo sync." }
            runRepoCommand(targetDir, "sync", "-c")

            return getWorkingTree(targetDir)
        } catch (e: IOException) {
            if (Main.stacktrace) {
                e.printStackTrace()
            }

            throw DownloadException("Could not clone ${vcs.url}/$manifestPath", e)
        }
    }

    private fun runRepoCommand(targetDir: File, vararg args: String) {
        ProcessCapture(targetDir, "repo", *args).requireSuccess()
    }
}
