/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import org.ossreviewtoolkit.downloader.WorkingTree
import org.ossreviewtoolkit.model.VcsType

internal class MercurialWorkingTree(workingDir: File, vcsType: VcsType) : WorkingTree(workingDir, vcsType) {
    override fun isValid(): Boolean {
        if (!workingDir.isDirectory) return false

        val hgRootPath = MercurialCommand.run(workingDir, "root")
        return hgRootPath.isSuccess && workingDir.path.startsWith(hgRootPath.stdout.trimEnd())
    }

    override fun isShallow() = false

    override fun getRemoteUrl() = runHg("paths", "default").stdout.trimEnd()

    override fun getRevision() = runHg("--debug", "id", "-i").stdout.trimEnd()

    override fun getRootPath() = File(runHg("root").stdout.trimEnd())

    override fun listRemoteBranches(): List<String> {
        val branches = runHg("branches").stdout.trimEnd()
        return branches.lines().map {
            it.substringBefore(' ')
        }.sorted()
    }

    override fun listRemoteTags(): List<String> {
        // Mercurial does not have the concept of global remote tags. Its "regular tags" are defined per
        // branch as part of the committed ".hgtags" file. See https://stackoverflow.com/a/2059189/1127485.
        runHg("pull", "-r", "default")
        val tags = runHg("cat", "-r", "default", ".hgtags").stdout.trimEnd()
        return tags.lines().map {
            it.substringAfterLast(' ')
        }.sorted()
    }
}
