/*
 * Copyright (C) 2021 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.advisors.vulnerablecode

import kotlin.coroutines.cancellation.CancellationException

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.percentEncode

/**
 * The maximum length for the summary as derived from the description of a vulnerability.
 */
internal const val MAX_SUMMARY_LENGTH = 64

/**
 * The number of PURLs to request at once in a bulk request. A relatively large chunk size reduces the number of
 * top-level bulk requests while still keeping individual responses reasonably small.
 */
internal const val BULK_REQUEST_SIZE = 100

/**
 * Return a short summary derived from this string, or null if this string is null or blank.
 */
internal fun String?.deriveSummary(): String? {
    val description = this?.ifBlank { null }

    return description?.take(MAX_SUMMARY_LENGTH)?.let {
        if (it.length < description.length) "$it..." else it
    }
}

/**
 * Handle a failure while requesting a chunk of PURLs. Populate [allVulnerabilities] with empty entries for all PURLs
 * in [chunk] as the current data model does not allow returning issues that are not associated to any package.
 */
internal suspend fun <T> Throwable.handleChunkRequestFailure(
    chunk: Collection<String>,
    chunkIndex: Int,
    chunkCount: Int,
    issueSource: String,
    allVulnerabilities: MutableMap<String, List<T>>,
    issues: MutableList<Issue>
) {
    if (this is CancellationException) currentCoroutineContext().ensureActive()

    allVulnerabilities += chunk.associateWith { emptyList<T>() }
    issues += Issue(source = issueSource, message = collectMessages())

    logger.error {
        "The request of chunk ${chunkIndex + 1} of $chunkCount failed for the following ${chunk.size} PURL(s):"
    }

    chunk.forEach(logger::error)
}

private val BACKSLASH_ESCAPE_REGEX = """\\\\?(.)""".toRegex()

internal fun String.fixupUrlEscaping(): String =
    replace("""\/""", "/").replace(BACKSLASH_ESCAPE_REGEX) {
        it.groupValues[1].percentEncode()
    }
