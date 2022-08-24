/*
 * Copyright (C) 2022 EPAM Systems, Inc.
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

package org.ossreviewtoolkit.evaluator

import java.io.File

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.downloader.Downloader
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.utils.common.FileMatcher
import org.ossreviewtoolkit.utils.ort.createOrtTempDir

class SourceTree private constructor(
    val vcsType: VcsType,
    getRepositoryRoot: () -> File
) {
    companion object : Logging {
        fun forRemoteRepository(vcsInfo: VcsInfo) =
            SourceTree(
                vcsType = vcsInfo.type,
                getRepositoryRoot = {
                    logger.info {
                        "Downloading ${vcsInfo.type} source code repository: '${vcsInfo.url}'..."
                    }

                    val downloadDir = createOrtTempDir()
                    val downloader = Downloader(DownloaderConfiguration())
                    val pkg = Package.EMPTY.copy(vcsProcessed = vcsInfo)

                    downloader.download(pkg, downloadDir)

                    downloadDir
                }
            )

        fun forLocalDir(dir: File, vcsType: VcsType) =
            SourceTree(
                vcsType = vcsType,
                getRepositoryRoot = { dir }
            )
    }

    val rootDir by lazy {
        getRepositoryRoot()
    }

    /**
     * Return true if any of the given [glob expressions][patterns] match an existing directory.
     */
    fun hasDirectory(vararg patterns: String): Boolean =
        findDirectories(*patterns).isNotEmpty()

    /**
     * Return the directory paths matching any of the given [glob expressions][patterns].
     */
    fun findDirectories(vararg patterns: String): Set<String> {
        val matcher = FileMatcher(patterns.toSet())

        return rootDir.walkBottomUp().mapNotNull { file ->
            file.takeIf { it.isDirectory }?.let { it.relativeTo(rootDir).path }
        }.filterTo(mutableSetOf()) { matcher.matches(it) }
    }

    /**
     * Return true if any of the given glob expressions match an existing file.
     */
    fun hasFile(vararg patterns: String): Boolean =
        findFiles(*patterns).isNotEmpty()

    /**
     * Return the file paths matching any of the given [glob expressions][patterns].
     */
    fun findFiles(vararg patterns: String): Set<String> {
        val matcher = FileMatcher(patterns.toSet())

        return rootDir.walkBottomUp().mapNotNull { file ->
            file.takeIf { it.isFile }?.let { it.relativeTo(rootDir).path }
        }.filterTo(mutableSetOf()) { matcher.matches(it) }
    }
}
