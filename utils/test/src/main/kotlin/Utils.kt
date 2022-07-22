/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import com.fasterxml.jackson.module.kotlin.readValue

import io.kotest.matchers.Matcher
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.neverNullMatcher

import java.io.File
import java.time.Instant

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.mapper
import org.ossreviewtoolkit.model.yamlMapper

val DEFAULT_ANALYZER_CONFIGURATION = AnalyzerConfiguration(allowDynamicVersions = false)
val DEFAULT_REPOSITORY_CONFIGURATION = RepositoryConfiguration()

val USER_DIR = File(System.getProperty("user.dir"))

private val ORT_VERSION_REGEX = Regex("(ort_version): \".*\"")
private val JAVA_VERSION_REGEX = Regex("(java_version): \".*\"")
private val ENV_VAR_REGEX = Regex("(\\s{4}variables:)\\n(?:\\s{6}.+)+")
private val ENV_TOOL_REGEX = Regex("(\\s{4}tool_versions:)\\n(?:\\s{6}.+)+")
private val START_AND_END_TIME_REGEX = Regex("((start|end)_time): \".*\"")
private val TIMESTAMP_REGEX = Regex("(timestamp): \".*\"")

/**
 * Return the asset file located under [path] relative to the 'assets' directory as text.
 */
fun getAssetAsString(path: String): String = File("src/funTest/assets").resolve(path).readText()

fun patchExpectedResult(
    result: File,
    custom: Map<String, String> = emptyMap(),
    definitionFilePath: String? = null,
    url: String? = null,
    revision: String? = null,
    path: String? = null,
    urlProcessed: String? = null
): String {
    fun String.replaceIfNotNull(oldValue: String, newValue: String?) =
        if (newValue != null) replace(oldValue, newValue) else this

    return custom.entries.fold(result.readText()) { text, entry -> text.replaceIfNotNull(entry.key, entry.value) }
        .replaceIfNotNull("<REPLACE_JAVA>", System.getProperty("java.version"))
        .replaceIfNotNull("<REPLACE_OS>", System.getProperty("os.name"))
        .replaceIfNotNull("\"<REPLACE_PROCESSORS>\"", Runtime.getRuntime().availableProcessors().toString())
        .replaceIfNotNull("\"<REPLACE_MAX_MEMORY>\"", Runtime.getRuntime().maxMemory().toString())
        .replaceIfNotNull("<REPLACE_DEFINITION_FILE_PATH>", definitionFilePath)
        .replaceIfNotNull("<REPLACE_URL>", url)
        .replaceIfNotNull("<REPLACE_REVISION>", revision)
        .replaceIfNotNull("<REPLACE_PATH>", path)
        .replaceIfNotNull("<REPLACE_URL_PROCESSED>", urlProcessed)
}

fun patchActualResult(
    result: String,
    patchStartAndEndTime: Boolean = false
): String {
    fun String.replaceIf(condition: Boolean, regex: Regex, transform: (MatchResult) -> CharSequence) =
        if (condition) replace(regex, transform) else this

    return result
        .replace(ORT_VERSION_REGEX) { "${it.groupValues[1]}: \"HEAD\"" }
        .replace(JAVA_VERSION_REGEX) { "${it.groupValues[1]}: \"${System.getProperty("java.version")}\"" }
        .replace(ENV_VAR_REGEX) { "${it.groupValues[1]} {}" }
        .replace(ENV_TOOL_REGEX) { "${it.groupValues[1]} {}" }
        .replace(TIMESTAMP_REGEX) { "${it.groupValues[1]}: \"${Instant.EPOCH}\"" }
        .replaceIf(patchStartAndEndTime, START_AND_END_TIME_REGEX) { "${it.groupValues[1]}: \"${Instant.EPOCH}\"" }
}

fun patchActualResult(
    result: OrtResult,
    patchStartAndEndTime: Boolean = false
): String =
    patchActualResult(yamlMapper.writeValueAsString(result), patchStartAndEndTime)

fun patchActualResultObject(result: OrtResult, patchStartAndEndTime: Boolean = false): OrtResult =
    yamlMapper.readValue(patchActualResult(result, patchStartAndEndTime))

fun readOrtResult(file: String) = readOrtResult(File(file))

fun readOrtResult(file: File) = file.mapper().readValue<OrtResult>(patchExpectedResult(file))

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
