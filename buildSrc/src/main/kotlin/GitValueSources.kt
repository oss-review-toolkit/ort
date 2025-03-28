/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import java.io.File

import org.eclipse.jgit.api.Git as JGit
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

interface GitParameters : ValueSourceParameters {
    val workingDir: DirectoryProperty
}

abstract class GitFilesValueSource : ValueSource<List<File>, GitParameters> {
    override fun obtain(): List<File> {
        val filePaths = mutableListOf<File>()
        val workingDir = parameters.workingDir.get().asFile

        JGit.open(workingDir).use { git ->
            TreeWalk(git.repository).use { treeWalk ->
                val headCommit = RevWalk(git.repository).use {
                    val head = git.repository.resolve(Constants.HEAD)
                    it.parseCommit(head)
                }

                with(treeWalk) {
                    addTree(headCommit.tree)
                    isRecursive = true
                }

                while (treeWalk.next()) {
                    filePaths += workingDir.resolve(treeWalk.pathString)
                }
            }
        }

        return filePaths
    }
}
