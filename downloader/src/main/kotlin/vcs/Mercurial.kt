/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
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

package org.ossreviewtoolkit.downloader.vcs

import java.io.File
import java.util.regex.Pattern

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.downloader.WorkingTree
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.ort.showStackTrace

const val MERCURIAL_LARGE_FILES_EXTENSION = "largefiles = "
const val MERCURIAL_SPARSE_EXTENSION = "sparse = "

object MercurialCommand : CommandLineTool {
    private val versionRegex = Pattern.compile("Mercurial .*\\([Vv]ersion (?<version>[\\d.]+)\\)")

    override fun command(workingDir: File?) = "hg"

    override fun transformVersion(output: String): String =
        versionRegex.matcher(output.lineSequence().first()).let {
            if (it.matches()) {
                it.group("version")
            } else {
                ""
            }
        }
}

class Mercurial : VersionControlSystem() {
    companion object : Logging

    override val type = VcsType.MERCURIAL
    override val priority = 20
    override val latestRevisionNames = listOf("tip")

    override fun getVersion() = MercurialCommand.getVersion(null)

    override fun getDefaultBranchName(url: String) = "default"

    override fun getWorkingTree(vcsDirectory: File) = MercurialWorkingTree(vcsDirectory, type)

    override fun isApplicableUrlInternal(vcsUrl: String) =
        ProcessCapture("hg", "identify", vcsUrl).isSuccess

    override fun initWorkingTree(targetDir: File, vcs: VcsInfo): WorkingTree {
        // We cannot detect beforehand if the Large Files extension would be required, so enable it by default.
        val extensionsList = mutableListOf(MERCURIAL_LARGE_FILES_EXTENSION)

        if (vcs.path.isNotBlank() && isAtLeastVersion("4.3")) {
            // Starting with version 4.3 Mercurial has experimental built-in support for sparse checkouts, see
            // https://www.mercurial-scm.org/wiki/WhatsNew#Mercurial_4.3_.2F_4.3.1_.282017-08-10.29
            extensionsList += MERCURIAL_SPARSE_EXTENSION
        }

        MercurialCommand.run(targetDir, "init")
        targetDir.resolve(".hg/hgrc").writeText(
            """
                [paths]
                default = ${vcs.url}
                [extensions]

                """.trimIndent() + extensionsList.joinToString("\n")
        )

        if (MERCURIAL_SPARSE_EXTENSION in extensionsList) {
            logger.info { "Configuring Mercurial to do sparse checkout of path '${vcs.path}'." }

            // Mercurial does not accept absolute paths.
            val globPatterns = getSparseCheckoutGlobPatterns() + "${vcs.path}/**"

            MercurialCommand.run(targetDir, "debugsparse", *globPatterns.flatMap { listOf("-I", it) }.toTypedArray())
        }

        return getWorkingTree(targetDir)
    }

    override fun updateWorkingTree(workingTree: WorkingTree, revision: String, path: String, recursive: Boolean) =
        runCatching {
            // To safe network bandwidth, only pull exactly the revision we want. Do not use "-u" to update the
            // working tree just yet, as Mercurial would only update if new changesets were pulled. But that might
            // not be the case if the requested revision is already available locally.
            MercurialCommand.run(workingTree.workingDir, "pull", "-r", revision)

            // TODO: Implement updating of subrepositories.

            // Explicitly update the working tree to the desired revision.
            MercurialCommand.run(workingTree.workingDir, "update", revision).isSuccess
        }.onFailure {
            it.showStackTrace()

            logger.warn { "Failed to update $type working tree to revision '$revision': ${it.collectMessages()}" }
        }.map {
            revision
        }
}
