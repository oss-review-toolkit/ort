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

package org.ossreviewtoolkit.analyzer.managers

import java.io.File

import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.utils.spdx.SpdxModelMapper
import org.ossreviewtoolkit.utils.spdx.model.SpdxChecksum
import org.ossreviewtoolkit.utils.spdx.model.SpdxDocument

/**
 * A helper class for [[SpdxDocumentFile]] that deals with loading of SPDX documents.
 *
 * While loading of SPDX documents is not that complicated, this class mainly is responsible for caching loaded
 * documents to prevent that they are loaded multiple times - which could be the case for instance if multiple
 * packages are resolved from an external document reference.
 *
 * Implementation note: This implementation is not thread-safe.
 */
internal class SpdxDocumentLoader {
    /** A cache for the documents that have already been loaded. */
    private val documentCache = mutableMapOf<File, DocumentEntry>()

    /**
     * Load the given [file] and parse it to an [SpdxDocument]. Cache the resulting document in case the file is
     * queried again.
     */
    fun load(file: File): SpdxDocument =
        fetch(file).document

    /**
     * Load the given [file], parse it to an [SpdxDocument], and verify that it matches the given [checksum].
     */
    fun loadAndVerify(file: File, checksum: SpdxChecksum): VerifiedDocument {
        val entry = fetch(file)

        return VerifiedDocument(entry.document, entry.verify(file, checksum))
    }

    /**
     * Fetch the [DocumentEntry] for the given [File]. Either load it or get it from the cache.
     */
    private fun fetch(file: File) =
        documentCache.getOrPut(file) {
            DocumentEntry(SpdxModelMapper.read(file), mutableMapOf())
        }
}

/**
 * A data class combining an [SpdxDocument] with a flag whether it has been successfully validated against a checksum.
 * Instances of this class are returned by [SpdxDocumentLoader.loadAndVerify] to represent the two return values: the
 * document itself and the result of the checksum verification.
 */
internal data class VerifiedDocument(
    /** The [SpdxDocument] that was loaded. */
    val document: SpdxDocument,

    /** Flag whether verification against the provided checksum was successful. */
    val valid: Boolean
)

/**
 * An internal data class to store information about SPDX documents that have been loaded. In addition to the document
 * itself, the verified checksums are recorded.
 */
private data class DocumentEntry(
    /** The [SpdxDocument] that was loaded. */
    val document: SpdxDocument,

    /** A map with checksums that have been validated. */
    val checksums: MutableMap<SpdxChecksum, Boolean>
) {
    /**
     * Verify this document [file] against the given [checksum]. Record checksums that have been verified.
     */
    fun verify(file: File, checksum: SpdxChecksum): Boolean =
        checksums.getOrPut(checksum) {
            val hash = Hash.create(checksum.checksumValue, checksum.algorithm.name)
            hash.verify(file)
        }
}
