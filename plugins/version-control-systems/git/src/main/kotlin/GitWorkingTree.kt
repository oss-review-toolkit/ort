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

package org.ossreviewtoolkit.plugins.versioncontrolsystems.git

import java.io.File
import java.io.IOException

import org.apache.logging.log4j.kotlin.logger

import org.eclipse.jgit.api.Git as JGit
import org.eclipse.jgit.api.LsRemoteCommand
import org.eclipse.jgit.lib.BranchConfig
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.submodule.SubmoduleWalk

import org.ossreviewtoolkit.downloader.WorkingTree
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.toUri
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl

private fun findGitOrSubmoduleDir(workingDirOrFile: File): Repository {
    val workingDir = (workingDirOrFile.takeIf { it.isDirectory } ?: workingDirOrFile.parentFile).absoluteFile
    return runCatching {
        // First try to open an existing working tree exactly at the given directory. This also works for submodules
        // which since Git 1.7.8 do not have their own ".git" directory anymore in favor of a ".git" file.
        FileRepositoryBuilder().setWorkTree(workingDir).setMustExist(true).build()
    }.recoverCatching {
        // Fall back to searching for a .git directory upwards in the directory tree.
        FileRepositoryBuilder().findGitDir(workingDir).build()
    }.recoverCatching {
        // Finally, fall back to treating the directory as a working tree that is yet to be created.
        FileRepositoryBuilder().setWorkTree(workingDir).build()
    }.getOrThrow()
}

internal open class GitWorkingTree(workingDir: File, vcsType: VcsType) : WorkingTree(workingDir, vcsType) {
    fun <T> useRepo(block: Repository.() -> T): T = findGitOrSubmoduleDir(workingDir).use(block)

    override fun isValid(): Boolean = useRepo { objectDatabase?.exists() == true }

    override fun isShallow(): Boolean = useRepo { directory?.resolve("shallow")?.isFile == true }

    private fun listSubmodulePaths(repo: Repository): List<String> {
        fun listSubmodules(parent: String, repo: Repository, paths: MutableList<String>) {
            val prefix = if (parent.isEmpty()) parent else "$parent/"

            SubmoduleWalk.forIndex(repo).use { walk ->
                while (walk.next()) {
                    val path = "$prefix${walk.path}"

                    if (walk.repository == null) {
                        logger.warn {
                            "Git submodule at '$path' not initialized. Cannot recursively list its submodules."
                        }
                    } else {
                        paths += path

                        walk.repository.use { submoduleRepo ->
                            listSubmodules(path, submoduleRepo, paths)
                        }
                    }
                }
            }
        }

        return mutableListOf<String>().also { paths ->
            listSubmodules("", repo, paths)
        }
    }

    override fun getNested(): Map<String, VcsInfo> =
        useRepo {
            listSubmodulePaths(this).associateWith { path ->
                GitWorkingTree(workTree / path, vcsType).getInfo()
            }
        }

    override fun getRemoteUrl(): String =
        useRepo {
            runCatching {
                val remotes = JGit(this).use { it.remoteList().call() }
                val remoteForCurrentBranch = BranchConfig(config, branch).remote

                val remote = if (remotes.size <= 1 || remoteForCurrentBranch == null) {
                    remotes.find { it.name == "origin" } ?: remotes.firstOrNull()
                } else {
                    remotes.find { remote ->
                        remote.name == remoteForCurrentBranch
                    }
                }

                val firstRemote = remote?.urIs?.firstOrNull()
                when {
                    firstRemote == null -> normalizeVcsUrl(remoteForCurrentBranch).toUri { it.toString() }.getOrThrow()
                    firstRemote.isRemote -> firstRemote.toString()
                    else -> File(firstRemote.path).invariantSeparatorsPath
                }
            }.getOrElse {
                throw IOException("Unable to get the remote URL.", it)
            }
        }

    override fun getRevision(): String = useRepo { exactRef(Constants.HEAD)?.objectId?.name().orEmpty() }

    override fun getRootPath(): File = useRepo { workTree }

    override fun listRemoteBranches(): List<String> =
        useRepo {
            runCatching {
                LsRemoteCommand(this).setHeads(true).call().map { branch ->
                    branch.name.removePrefix("refs/heads/")
                }
            }.getOrElse { e ->
                throw IOException("Unable to list the remote branches.", e)
            }
        }

    override fun listRemoteTags(): List<String> =
        useRepo {
            runCatching {
                LsRemoteCommand(this).setTags(true).call().map { tag ->
                    tag.name.removePrefix("refs/tags/")
                }
            }.getOrElse { e ->
                throw IOException("Unable to list the remote tags.", e)
            }
        }
}
