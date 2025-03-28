/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.versioncontrolsystems.mercurial

import java.io.File

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.downloader.VersionControlSystemFactory
import org.ossreviewtoolkit.downloader.WorkingTree
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.common.CommandLineTool

const val MERCURIAL_LARGE_FILES_EXTENSION = "largefiles = "
const val MERCURIAL_SPARSE_EXTENSION = "sparse = "

internal object MercurialCommand : CommandLineTool {
    private val versionRegex = Regex("Mercurial .*\\([Vv]ersion (?<version>[\\d.]+)\\)")

    override fun command(workingDir: File?) = "hg"

    override fun transformVersion(output: String): String =
        versionRegex.matchEntire(output.lineSequence().first())?.let { match ->
            @Suppress("UnsafeCallOnNullableType")
            match.groups["version"]!!.value
        }.orEmpty()
}

internal fun MercurialWorkingTree.runHg(vararg args: String) =
    MercurialCommand.run(*args, workingDir = workingDir).requireSuccess()

@OrtPlugin(
    displayName = "Mercurial",
    description = "A VCS implementation to interact with Mercurial repositories.",
    factory = VersionControlSystemFactory::class
)
class Mercurial(override val descriptor: PluginDescriptor = MercurialFactory.descriptor) : VersionControlSystem() {
    override val type = VcsType.MERCURIAL
    override val priority = 20
    override val latestRevisionNames = listOf("tip")

    override fun getVersion() = MercurialCommand.getVersion()

    override fun getDefaultBranchName(url: String) = "default"

    override fun getWorkingTree(vcsDirectory: File): WorkingTree = MercurialWorkingTree(vcsDirectory, type)

    override fun isAvailable() = MercurialCommand.isInPath()

    override fun isApplicableUrlInternal(vcsUrl: String) = MercurialCommand.run("identify", vcsUrl).isSuccess

    override fun initWorkingTree(targetDir: File, vcs: VcsInfo): WorkingTree {
        // We cannot detect beforehand if the Large Files extension would be required, so enable it by default.
        val extensionsList = mutableListOf(MERCURIAL_LARGE_FILES_EXTENSION)

        if (vcs.path.isNotBlank() && isAtLeastVersion("4.3")) {
            // Starting with version 4.3 Mercurial has experimental built-in support for sparse checkouts, see
            // https://www.mercurial-scm.org/wiki/WhatsNew#Mercurial_4.3_.2F_4.3.1_.282017-08-10.29
            extensionsList += MERCURIAL_SPARSE_EXTENSION
        }

        MercurialCommand.run(targetDir, "init").requireSuccess()

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
                .requireSuccess()
        }

        return getWorkingTree(targetDir)
    }

    override fun updateWorkingTree(workingTree: WorkingTree, revision: String, path: String, recursive: Boolean) =
        runCatching {
            check(workingTree is MercurialWorkingTree)

            // To safe network bandwidth, only pull exactly the revision we want. Do not use "-u" to update the
            // working tree just yet, as Mercurial would only update if new changesets were pulled. But that might
            // not be the case if the requested revision is already available locally.
            workingTree.runHg("pull", "-r", revision)

            // TODO: Implement updating of subrepositories.

            // Explicitly update the working tree to the desired revision.
            workingTree.runHg("update", revision).isSuccess
        }.map {
            revision
        }
}
