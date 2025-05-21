/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.spdx.utils

import java.io.File

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.plugins.packagemanagers.spdx.SpdxDocumentFile
import org.ossreviewtoolkit.utils.spdxdocument.SpdxModelMapper
import org.ossreviewtoolkit.utils.spdxdocument.model.SpdxDocument

/**
 * A helper class for [SpdxDocumentFile] that deals with loading and caching of SPDX documents.
 *
 * While loading of SPDX documents is not that complicated, this class mainly is responsible for caching loaded
 * documents to prevent that they are loaded multiple times - which could be the case for instance if multiple
 * packages are resolved from an external document reference.
 *
 * Implementation note: This implementation is not thread-safe.
 */
internal class SpdxDocumentCache {
    /** A cache for the documents that have already been loaded. */
    private val documentCache = mutableMapOf<File, Result<SpdxDocument>>()

    /**
     * Load the given [file] and parse it to an [SpdxDocument]. Cache the resulting document in case the file is
     * queried again.
     */
    fun load(file: File): Result<SpdxDocument> =
        documentCache.getOrPut(file) {
            logger.info { "Loading SpdxDocument from '$file'." }

            runCatching {
                SpdxModelMapper.read<SpdxDocument>(file).apply {
                    packages.forEach { it.validate() }
                    files.forEach { it.validate() }
                }
            }
        }
}
