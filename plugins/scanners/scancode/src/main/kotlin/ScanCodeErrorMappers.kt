/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.scancode

import org.ossreviewtoolkit.model.Issue

// Note: The "(File: ...)" part in the patterns below is actually added by our own getRawResult() function.
private val UNKNOWN_ERROR_REGEX = Regex(
    "(ERROR: for scanner: (?<scanner>\\w+):\n)?" +
            "ERROR: Unknown error:\n.+\n(?<error>\\w+Error)(:|\n)(?<message>.*) \\(File: (?<file>.+)\\)",
    RegexOption.DOT_MATCHES_ALL
)

private val TIMEOUT_ERROR_REGEX = Regex(
    "(ERROR: for scanner: (?<scanner>\\w+):\n)?" +
            "ERROR: Processing interrupted: timeout after (?<timeout>\\d+) seconds. \\(File: (?<file>.+)\\)"
)

/**
 * Map scan errors for all files using messages that contain the relative file path.
 */
internal fun mapScanErrors(result: ScanCodeResult): List<Issue> {
    val input = result.headers.single().options.input.single()
    return result.files.flatMap { file ->
        val path = file.path.removePrefix(input).removePrefix("/")
        file.scanErrors.map { error ->
            Issue(
                source = ScanCode.SCANNER_NAME,
                message = "$error (File: $path)"
            )
        }
    }
}

/**
 * Map messages about timeout errors to a more compact form. Return true if solely timeout errors occurred, return false
 * otherwise.
 */
internal fun mapTimeoutErrors(issues: MutableList<Issue>): Boolean {
    if (issues.isEmpty()) return false

    var onlyTimeoutErrors = true

    val mappedIssues = issues.map { fullError ->
        val match = TIMEOUT_ERROR_REGEX.matchEntire(fullError.message)
        if (match?.groups?.get("timeout")?.value == ScanCode.TIMEOUT.toString()) {
            val file = match.groups["file"]!!.value
            fullError.copy(
                message = "ERROR: Timeout after ${ScanCode.TIMEOUT} seconds while scanning file '$file'."
            )
        } else {
            onlyTimeoutErrors = false
            fullError
        }
    }

    issues.clear()
    issues += mappedIssues.distinctBy { it.message }

    return onlyTimeoutErrors
}

/**
 * Map messages about unknown errors to a more compact form. Return true if solely memory errors occurred, return false
 * otherwise.
 */
internal fun mapUnknownErrors(issues: MutableList<Issue>): Boolean {
    if (issues.isEmpty()) return false

    var onlyMemoryErrors = true

    val mappedIssues = issues.map { fullError ->
        UNKNOWN_ERROR_REGEX.matchEntire(fullError.message)?.let { match ->
            val file = match.groups["file"]!!.value
            val error = match.groups["error"]!!.value
            if (error == "MemoryError") {
                fullError.copy(message = "ERROR: MemoryError while scanning file '$file'.")
            } else {
                onlyMemoryErrors = false
                val message = match.groups["message"]!!.value.trim()
                fullError.copy(message = "ERROR: $error while scanning file '$file' ($message).")
            }
        } ?: run {
            onlyMemoryErrors = false
            fullError
        }
    }

    issues.clear()
    issues += mappedIssues.distinctBy { it.message }

    return onlyMemoryErrors
}
