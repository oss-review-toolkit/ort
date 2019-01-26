/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.utils.test

import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.RepositoryConfiguration

import java.io.File
import java.time.Instant

val DEFAULT_ANALYZER_CONFIGURATION = AnalyzerConfiguration(false, false)
val DEFAULT_REPOSITORY_CONFIGURATION = RepositoryConfiguration()

val USER_DIR = File(System.getProperty("user.dir"))

private val DOWNLOAD_TIME_REGEX = Regex("(download_time): \".*\"")
private val START_AND_END_TIME_REGEX = Regex("((start|end)_time): \".*\"")
private val TIMESTAMP_REGEX = Regex("(timestamp): \".*\"")

fun patchExpectedResult(result: File, custom: Pair<String, String>? = null, definitionFilePath: String? = null,
                        url: String? = null, revision: String? = null, path: String? = null,
                        urlProcessed: String? = null): String {
    fun String.replaceIfNotNull(strings: Pair<String, String>?) =
            if (strings != null) replace(strings.first, strings.second) else this
    fun String.replaceIfNotNull(oldValue: String, newValue: String?) =
            if (newValue != null) replace(oldValue, newValue) else this

    return result.readText()
            .replaceIfNotNull("<REPLACE_OS>", System.getProperty("os.name"))
            .replaceIfNotNull(custom)
            .replaceIfNotNull("<REPLACE_DEFINITION_FILE_PATH>", definitionFilePath)
            .replaceIfNotNull("<REPLACE_URL>", url)
            .replaceIfNotNull("<REPLACE_REVISION>", revision)
            .replaceIfNotNull("<REPLACE_PATH>", path)
            .replaceIfNotNull("<REPLACE_URL_PROCESSED>", urlProcessed)
}

fun patchActualResult(result: String, patchDownloadTime: Boolean = false, patchStartAndEndTime: Boolean = false)
        : String {
    fun String.replaceIf(condition: Boolean, regex: Regex, transform: (MatchResult) -> CharSequence) =
            if (condition) replace(regex, transform) else this

    return result
            .replace(TIMESTAMP_REGEX) { "${it.groupValues[1]}: \"${Instant.EPOCH}\"" }
            .replaceIf(patchDownloadTime, DOWNLOAD_TIME_REGEX) { "${it.groupValues[1]}: \"${Instant.EPOCH}\"" }
            .replaceIf(patchStartAndEndTime, START_AND_END_TIME_REGEX) { "${it.groupValues[1]}: \"${Instant.EPOCH}\"" }
}
