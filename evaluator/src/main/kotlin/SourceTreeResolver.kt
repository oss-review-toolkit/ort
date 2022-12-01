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

package org.ossreviewtoolkit.evaluator

import java.io.File

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.downloader.Downloader
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.SourceCodeOrigin
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.utils.ort.createOrtTempDir

class SourceTreeResolver private constructor(getRepositoryRoot: () -> File) {
    companion object : Logging {
        fun forRemoteRepository(vcsInfo: VcsInfo) =
            SourceTreeResolver {
                val downloadDir = createOrtTempDir()
                val downloaderConfiguration = DownloaderConfiguration(sourceCodeOrigins = listOf(SourceCodeOrigin.VCS))
                val downloader = Downloader(downloaderConfiguration)
                val pkg = Package.EMPTY.copy(vcsProcessed = vcsInfo)

                downloader.download(pkg, downloadDir)

                downloadDir
            }

        fun forLocalDirectory(dir: File) = SourceTreeResolver { dir }
    }

    val rootDir: File by lazy {
        logger.info {
            "The evaluator rules attempt to access the project's source code for the first time. Fetching sources..."
        }

        getRepositoryRoot()
    }
}
