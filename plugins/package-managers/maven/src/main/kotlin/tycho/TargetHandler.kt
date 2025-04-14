/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.maven.tycho

import java.io.File

/**
 * A helper class to manage information stored in Tycho target files.
 *
 * The target platform plays an important role in Tycho builds as it contains essential information for resolving
 * dependencies. The Tycho package manager implementation delegates to Tycho itself for the dependency resolution.
 * However, some information from target files is nevertheless needed to obtain correct metadata for dependencies.
 * This class is responsible for detecting target files in the analysis root directory and extracting the relevant
 * data.
 *
 * See https://wiki.eclipse.org/Tycho/Target_Platform/.
 */
internal class TargetHandler(
    /** A set with the URLs of the P2 repositories defined in the target files. */
    val repositoryUrls: Set<String>
) {
    companion object {
        /**
         * Create an instance of [TargetHandler] that loads its data from target files found below the given
         * [projectRoot] folder.
         */
        fun create(projectRoot: File): TargetHandler {
            return TargetHandler(collectP2RepositoriesFromTargetFiles(projectRoot))
        }

        /**
         * Collect all P2 repositories defined in a Tycho target file found under the given [projectRoot].
         */
        private fun collectP2RepositoriesFromTargetFiles(projectRoot: File): Set<String> {
            // TODO: There may be a better way to locate target files by inspecting the projects found in the build.
            val targetFiles = projectRoot.walkTopDown().filter {
                it.name.endsWith(".target") && it.isFile
            }.toList()

            return targetFiles.flatMapTo(mutableSetOf(), ::parseTargetFile)
        }

        /**
         * Parse the given [targetFile] and extract the repository URLs referenced in it.
         */
        private fun parseTargetFile(targetFile: File): Set<String> {
            val handler = ElementHandler(ParseTargetFileState())
                .handleElement("repository") { state, attributes ->
                    state.repositoryUrls += attributes.getValue("location")
                    state
                }

            return parseXml(targetFile, handler).repositoryUrls
        }
    }
}

/**
 * A data class to store the state during parsing of a target file.
 */
private data class ParseTargetFileState(
    /** The [Set] with the URLs of repositories that have been found so far. */
    val repositoryUrls: MutableSet<String> = mutableSetOf()
)
