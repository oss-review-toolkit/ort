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

import com.here.ort.downloader.WorkingTree
import com.here.ort.model.VcsInfo

import java.io.File

import org.eclipse.jgit.api.LsRemoteCommand
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.lib.BranchConfig
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.submodule.SubmoduleWalk
import java.io.IOException

private fun findGitOrSubmoduleDir(workingDir: File): Repository {
    // First try to open an existing working tree exactly at the given directory. This also works for submodules which
    // since Git 1.7.8 do not have their own ".git" directory anymore in favor of a ".git" file.
    FileRepositoryBuilder().setWorkTree(workingDir).setMustExist(true).runCatching {
        build()
    }.onSuccess {
        return it
    }

    // Fall back to searching for a .git directory upwards in the directory tree.
    FileRepositoryBuilder().findGitDir(workingDir).runCatching {
        build()
    }.onSuccess {
        return it
    }

    // Finally, fall back to treating the directory as a working tree that is yet to be created.
    return FileRepositoryBuilder().setWorkTree(workingDir).build()
}

open class GitWorkingTree(workingDir: File, private val gitBase: GitBase) : WorkingTree(workingDir, gitBase.type) {
    private val repo = findGitOrSubmoduleDir(workingDir.absoluteFile)

    override fun isValid(): Boolean = repo.objectDatabase?.exists() == true

    override fun isShallow(): Boolean = repo.directory?.resolve("shallow")?.isFile == true

    private fun listSubmodulePaths(repo: Repository): List<String> {
        fun listSubmodules(parent: String, repo: Repository, paths: MutableList<String>) {
            val prefix = if (parent.isEmpty()) parent else "$parent/"

            SubmoduleWalk.forIndex(repo).use { walk ->
                while (walk.next()) {
                    paths += "$prefix${walk.path}"
                    walk.repository.use { submoduleRepo ->
                        listSubmodules(paths.last(), submoduleRepo, paths)
                    }
                }
            }
        }

        return mutableListOf<String>().also { paths ->
            listSubmodules("", repo, paths)
        }
    }

    override fun getNested(): Map<String, VcsInfo> =
        listSubmodulePaths(repo).associateWith { GitWorkingTree(repo.workTree.resolve(it), gitBase).getInfo() }

    override fun getRemoteUrl(): String =
        try {
            val remotes = org.eclipse.jgit.api.Git(repo).remoteList().call()
            val remoteForCurrentBranch = BranchConfig(repo.config, repo.branch).remote

            val remote = if (remotes.size <= 1 || remoteForCurrentBranch == null) {
                remotes.firstOrNull()
            } else {
                remotes.find { remote ->
                    remote.name == remoteForCurrentBranch
                }
            }

            remote?.urIs?.firstOrNull()?.toString().orEmpty()
        } catch (e: GitAPIException) {
            throw IOException("Unable to get the remote URL.", e)
        }

    override fun getRevision(): String = repo.exactRef(Constants.HEAD)?.objectId?.name().orEmpty()

    override fun getRootPath(): File = repo.workTree ?: workingDir

    override fun listRemoteBranches(): List<String> =
        try {
            LsRemoteCommand(repo).setHeads(true).call().map { it.name.removePrefix("refs/heads/") }
        } catch (e: GitAPIException) {
            throw IOException("Unable to list the remote branches.", e)
        }

    override fun listRemoteTags(): List<String> =
        try {
            LsRemoteCommand(repo).setTags(true).call().map { it.name.removePrefix("refs/tags/") }
        } catch (e: GitAPIException) {
            throw IOException("Unable to list the remote tags.", e)
        }
}
