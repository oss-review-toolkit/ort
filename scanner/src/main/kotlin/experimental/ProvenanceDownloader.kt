/*
 * Copyright (C) 2021 HERE Europe B.V.
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

import java.io.File

import kotlinx.coroutines.runBlocking

import org.ossreviewtoolkit.downloader.DownloadException
import org.ossreviewtoolkit.downloader.Downloader
import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.utils.common.safeCopyRecursively
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.ort.createOrtTempDir

/**
 * An interface that provides functionality to download source code.
 */
interface ProvenanceDownloader {
    /**
     * Download the source code specified by the provided [provenance] and return the path to the directory that
     * contains the downloaded source code.
     *
     * Throws a [DownloadException] if the download fails.
     */
    fun download(provenance: KnownProvenance): File
}

/**
 * An implementation of [ProvenanceDownloader] which uses the download functions from the [Downloader] class. As these
 * functions require a [Package] to be provided, this implementation creates empty fake packages.
 */
class DefaultProvenanceDownloader(
    config: DownloaderConfiguration,
    private val workingTreeCache: WorkingTreeCache
) : ProvenanceDownloader {
    private val downloader = Downloader(config)

    override fun download(provenance: KnownProvenance): File {
        val downloadDir = createOrtTempDir()

        when (provenance) {
            // TODO: Add dedicated download functions for VcsInfo and RemoteArtifact to the Downloader.
            is ArtifactProvenance -> {
                val pkg = Package.EMPTY.copy(sourceArtifact = provenance.sourceArtifact)
                downloader.downloadSourceArtifact(pkg, downloadDir)
            }

            is RepositoryProvenance -> {
                runBlocking { downloadFromVcs(provenance, downloadDir) }
            }
        }

        return downloadDir
    }

    private suspend fun downloadFromVcs(provenance: RepositoryProvenance, downloadDir: File) {
        workingTreeCache.use(provenance.vcsInfo) { vcs, workingTree ->
            vcs.updateWorkingTree(workingTree, provenance.resolvedRevision, recursive = false)

            // Make sure that all nested repositories are removed. Even though we do not clone recursively above, nested
            // repositories could exist if the same working tree was previously cloned recursively.
            workingTree.getNested().forEach { (path, _) ->
                workingTree.getRootPath().resolve(path).safeDeleteRecursively(force = true)
            }

            // We need to make a copy of the working tree, because it could be used by another coroutine after this
            // call has finished.
            workingTree.getRootPath().safeCopyRecursively(downloadDir, overwrite = true)
        }
    }
}
