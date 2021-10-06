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

import org.ossreviewtoolkit.downloader.DownloadException
import org.ossreviewtoolkit.downloader.Downloader
import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.config.DownloaderConfiguration

/**
 * An interface that provides functionality to download source code.
 */
interface ProvenanceDownloader {
    /**
     * Download the source code specified by the provided [provenance] to the [downloadDir]. Throws a
     * [DownloadException] if the download fails.
     */
    fun download(provenance: KnownProvenance, downloadDir: File)
}

/**
 * An implementation of [ProvenanceDownloader] which uses the download functions from the [Downloader] class. As these
 * functions require a [Package] to be provided, this implementation creates empty fake packages.
 */
class DefaultProvenanceDownloader(config: DownloaderConfiguration) : ProvenanceDownloader {
    private val downloader = Downloader(config)

    override fun download(provenance: KnownProvenance, downloadDir: File) {
        when (provenance) {
            // TODO: Add dedicated download functions for VcsInfo and RemoteArtifact to the Downloader.
            is ArtifactProvenance -> {
                val pkg = Package.EMPTY.copy(sourceArtifact = provenance.sourceArtifact)
                downloader.downloadSourceArtifact(pkg, downloadDir)
            }

            is RepositoryProvenance -> {
                val pkg = Package.EMPTY.copy(
                    vcsProcessed = provenance.vcsInfo.copy(revision = provenance.resolvedRevision)
                )
                downloader.downloadFromVcs(pkg, downloadDir, recursive = false)
            }
        }
    }
}
