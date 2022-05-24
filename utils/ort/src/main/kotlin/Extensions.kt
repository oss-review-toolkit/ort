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

package org.ossreviewtoolkit.utils.ort

import java.io.File

/**
 * Create a temporary directory with a name specific to ORT, and optional [infixes].
 */
fun createOrtTempDir(vararg infixes: String): File {
    val prefix = listOfNotNull(ORT_NAME, *infixes).joinToString("-")
    return kotlin.io.path.createTempDirectory(prefix).toFile()
}

/**
 * Create a temporary directory with a name specific to ORT, the calling class, and optional [infixes].
 */
fun Any.createOrtTempDir(vararg infixes: String): File =
    org.ossreviewtoolkit.utils.ort.createOrtTempDir(javaClass.simpleName, *infixes)

/**
 * Create a temporary file with optionally specified [prefix] and [suffix] inside a directory with a name specific to
 * ORT.
 */
fun createOrtTempFile(prefix: String? = null, suffix: String? = null): File =
    kotlin.io.path.createTempFile(createOrtTempDir().toPath(), prefix, suffix).toFile()

/**
 * Create a temporary file with optionally specified [prefix] and [suffix] inside a directory with a name specific to
 * ORT and the calling class.
 */
fun Any.createOrtTempFile(prefix: String? = null, suffix: String? = null): File =
    kotlin.io.path.createTempFile(createOrtTempDir().toPath(), prefix, suffix).toFile()

/**
 * Print the stack trace of the [Throwable] if [printStackTrace] is set to true.
 */
fun Throwable.showStackTrace(): Unit = run { if (printStackTrace) printStackTrace() }
