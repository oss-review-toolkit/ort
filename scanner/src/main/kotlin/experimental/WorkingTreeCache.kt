/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.scanner.experimental

import java.io.IOException

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.downloader.WorkingTree
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.ort.createOrtTempDir

/**
 * A cache for [VCS][VersionControlSystem] [WorkingTree]s that manages one working tree for each triple of
 * [VcsInfo.type], [VcsInfo.url], and [VcsInfo.path].
 */
interface WorkingTreeCache {
    /**
     * Apply the [block] to the working tree defined by [vcsInfo]. The [VcsInfo.revision] is ignored during
     * initialization of the working tree, it is the caller's responsibility to update the working tree to the correct
     * revision. It is ensured that only one [block] at a time can access a single working tree, separate working trees
     * can be accessed in parallel.
     *
     * Throws an [IllegalStateException] if the cache was already [shut down][shutdown].
     */
    suspend fun <T> use(vcsInfo: VcsInfo, block: (VersionControlSystem, WorkingTree) -> T): T

    /**
     * Shut down the cache and clear all cached working trees from the file system. The function waits for all currently
     * running operations on the working trees to finish before deleting them.
     */
    suspend fun shutdown()
}

class DefaultWorkingTreeCache : WorkingTreeCache {
    private val mutex = Mutex()
    private val workingTreeMutexes = mutableMapOf<String, Mutex>()
    private val workingTrees = mutableMapOf<String, WorkingTree>()

    private var terminated = false

    override suspend fun <T> use(vcsInfo: VcsInfo, block: (VersionControlSystem, WorkingTree) -> T): T {
        val vcs = getVcs(vcsInfo)
        return getWorkingTreeMutex(vcsInfo).withLock { block(vcs, getWorkingTree(vcsInfo, vcs)) }
    }

    private fun getKey(vcsInfo: VcsInfo) = "${vcsInfo.type}|${vcsInfo.url}|${vcsInfo.path}"

    private suspend fun getWorkingTreeMutex(vcsInfo: VcsInfo) =
        mutex.withLock {
            check(!terminated) { "The cache was already shut down." }

            workingTreeMutexes.getOrPut(getKey(vcsInfo)) { Mutex() }
        }

    private fun getVcs(vcsInfo: VcsInfo) =
        VersionControlSystem.forType(vcsInfo.type)
            ?: VersionControlSystem.forUrl(vcsInfo.url)
            ?: throw IOException("Could not determine VCS type for (type=$${vcsInfo.type}, url=${vcsInfo.url}).")

    private fun getWorkingTree(vcsInfo: VcsInfo, vcs: VersionControlSystem) =
        workingTrees.getOrPut(getKey(vcsInfo)) {
            val dir = createOrtTempDir()
            vcs.initWorkingTree(dir, vcsInfo.copy(revision = ""))
        }

    override suspend fun shutdown() {
        mutex.withLock {
            terminated = true

            workingTreeMutexes.forEach { (key, workingTreeMutex) ->
                workingTreeMutex.withLock {
                    workingTrees[key]?.getRootPath()?.safeDeleteRecursively(force = true)
                }
            }
            workingTrees.clear()
            workingTreeMutexes.clear()
        }
    }
}
