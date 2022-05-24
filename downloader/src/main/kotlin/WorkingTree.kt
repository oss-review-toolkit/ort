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

package org.ossreviewtoolkit.downloader

import java.io.File
import java.io.IOException

import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.ort.filterVersionNames

/**
 * A class representing a local VCS working tree. The passed [workingDir] does not necessarily need to be the
 * root directory of the tree. The root directory can be determined by calling [getRootPath].
 */
abstract class WorkingTree(val workingDir: File, val vcsType: VcsType) {
    /**
     * Conveniently return all VCS information about how this working tree was created, so it could be easily
     * recreated from that information. However, note that the returned path just contains the relative path of
     * [workingDir] to [getRootPath]. It is not related to the path argument that was used for downloading, and at the
     * example of Git, it does not reflect the (single) path that was cloned in a sparse checkout.
     */
    open fun getInfo() = VcsInfo(vcsType, getRemoteUrl(), getRevision(), path = getPathToRoot(workingDir))

    /**
     * Return the map of nested repositories, for example Git submodules or Git-Repo modules. The key is the path to the
     * nested repository relative to the root of this working tree.
     */
    open fun getNested() = emptyMap<String, VcsInfo>()

    /**
     * Return true if the [workingDir] is managed by this VCS, false otherwise.
     */
    abstract fun isValid(): Boolean

    /**
     * Return whether this is a shallow working tree with truncated history.
     */
    abstract fun isShallow(): Boolean

    /**
     * Return the clone URL of the associated remote repository.
     */
    abstract fun getRemoteUrl(): String

    /**
     * Return the VCS-specific working tree revision.
     */
    abstract fun getRevision(): String

    /**
     * Return the root directory of this working tree.
     */
    abstract fun getRootPath(): File

    /**
     * Return the list of branches available in the remote repository.
     */
    abstract fun listRemoteBranches(): List<String>

    /**
     * Return the list of tags available in the remote repository.
     */
    abstract fun listRemoteTags(): List<String>

    /**
     * Search (symbolic) names of VCS revisions for a match with the given [project] and [version].
     *
     * @return The matching VCS revision, never blank.
     * @throws IOException If no or multiple matching revisions are found.
     */
    fun guessRevisionName(project: String, version: String): String {
        if (version.isBlank()) throw IOException("Cannot guess a revision name from a blank version.")

        val remoteTags = listRemoteTags()
        val versionNames = filterVersionNames(version, remoteTags, project)

        return when {
            versionNames.isEmpty() ->
                throw IOException(
                    "No matching tag for version '$version' found in $remoteTags. Please create a tag whose name " +
                            "contains the version."
                )
            versionNames.size > 1 ->
                throw IOException(
                    "Multiple matching tags found for version '$version': $versionNames. Please add a curation."
                )
            else -> versionNames.first()
        }
    }

    /**
     * Return the relative path to [path] with respect to the VCS root.
     */
    fun getPathToRoot(path: File): String {
        val relativePath = path.absoluteFile.relativeTo(getRootPath())

        // Use Unix paths even on Windows for consistent output.
        return relativePath.invariantSeparatorsPath
    }
}
