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

package org.ossreviewtoolkit.utils.test

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils

import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.equalityMatcher
import io.kotest.matchers.neverNullMatcher

import java.io.File

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.ProjectAnalyzerResult

/**
 * A helper function to create a custom matcher that compares an [expected] collection to a collection obtained by
 * [transform] using the provided [matcher].
 */
fun <T, U> transformingCollectionMatcher(
    expected: Collection<U>,
    matcher: (Collection<U>) -> Matcher<Collection<U>>,
    transform: (T) -> Collection<U>
): Matcher<T?> = neverNullMatcher { value -> matcher(expected).test(transform(value)) }

/**
 * A helper function to create custom matchers that assert that the collection obtained by [transform] is empty.
 */
fun <T, U> transformingCollectionEmptyMatcher(
    transform: (T) -> Collection<U>
): Matcher<T?> = neverNullMatcher { value -> beEmpty<U>().test(transform(value)) }

/**
 * A matcher for comparing to expected result files, in particular serialized [ProjectAnalyzerResult]s and [OrtResult]s,
 * that displays a unified diff with the given [contextSize] if the results do not match. If the Kotest system property
 * named "kotest.assertions.multi-line-diff" is set to "simple", this just falls back to [equalityMatcher].
 */
fun matchExpectedResult(
    expectedResultFile: File,
    definitionFile: File? = null,
    custom: Map<String, String> = emptyMap(),
    contextSize: Int = 7
): Matcher<String> {
    val expected = patchExpectedResult(expectedResultFile, definitionFile, custom)

    val multiLineDiff = System.getProperty("kotest.assertions.multi-line-diff")
    if (multiLineDiff != "unified") return equalityMatcher(expected)

    return Matcher { actual ->
        val vcsDir = VersionControlSystem.forDirectory(expectedResultFile)!!
        val relativeExpectedResultFile = vcsDir.getPathToRoot(expectedResultFile)

        val expectedLines = expected.lines()
        val actualLines = actual.lines()

        MatcherResult(
            expected == actual,
            {
                val diff = UnifiedDiffUtils.generateUnifiedDiff(
                    "a/$relativeExpectedResultFile",
                    "b/$relativeExpectedResultFile",
                    expectedLines,
                    DiffUtils.diff(expectedLines, actualLines),
                    contextSize
                )
                """
                    Expected and actual results differ. To use the actual results as the new expected results, first
                    copy one of the following commands to the clipboard and paste it to a terminal without running it
                    yet:
                    - `wl-paste | cut -d ' ' -f 5- | git apply` (Linux with Wayland)
                    - `xsel -b | cut -d ' ' -f 5- | git apply` (Linux with X)
                    - `cat /dev/clipboard | dos2unix | cut -d ' ' -f 5- | git apply` (Windows with Git Bash)
                    Then copy the following lines to the clipboard and run the previously pasted commands.
                """.trimIndent() + diff.joinToString("\n", "\n")
            },
            { "Expected and actual results should differ, but they match." }
        )
    }
}
