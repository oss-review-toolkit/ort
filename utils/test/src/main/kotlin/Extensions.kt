/*
 * Copyright (C) 2017 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import io.kotest.core.TestConfiguration
import io.kotest.engine.spec.tempfile

import java.io.File

import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.OrtResult

fun TestConfiguration.extractResource(name: String) =
    tempfile(name.substringAfterLast('/')).apply { writeText(readResource(name)) }

fun TestConfiguration.getResource(name: String) = checkNotNull(javaClass.getResource(name))

fun TestConfiguration.readResource(name: String) = getResource(name).readText()

fun TestConfiguration.readOrtResult(name: String) =
    FileFormat.forExtension(name.substringAfterLast('.')).mapper
        .readValue<OrtResult>(patchExpectedResult(readResource(name)))

inline fun <reified T : Any> TestConfiguration.readResourceValue(name: String): T =
    FileFormat.forExtension(name.substringAfterLast('.')).mapper.readValue<T>(getResource(name))

/**
 * Return the corresponding [File] location in the source tree. The function is for development only, and can help
 * to update expected result files. This function can be used to update expected results by inserting on lines into
 * the test code, for example: `getResourceAsFileInSourceTree("/expected-output.yml").writeText(result)`.
 */
@Suppress("unused") // This is intended to be used for development.
fun TestConfiguration.getResourceAsFileInSourceTree(name: String): File? {
    val path = getResource(name).takeIf { it.protocol == "file" }?.path ?: return null
    // Convert subpaths like e.g. "/build/resources/funTest/" to "/src/funTest/resources/".
    val newPath = path.replace(Regex("/build/resources/(\\w+)/"), "/src/$1/resources/")
    return File(newPath).takeIf { newPath != path }
}
