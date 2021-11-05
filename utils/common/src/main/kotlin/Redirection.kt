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

package org.ossreviewtoolkit.utils.common

import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.PrintStream

import kotlin.io.path.createTempFile

private fun redirectOutput(originalOutput: PrintStream, setOutput: (PrintStream) -> Unit, block: () -> Unit): String {
    val tempFile = createTempFile("redirect").toFile()
    val fileStream = FileOutputStream(tempFile)

    try {
        PrintStream(fileStream).use {
            setOutput(it)
            block()
        }
    } finally {
        setOutput(originalOutput)
    }

    return tempFile.readText().also { tempFile.delete() }
}

/**
 * Redirect the standard error stream to a [String] during the execution of [block].
 */
fun redirectStderr(block: () -> Unit) = redirectOutput(System.err, System::setErr, block)

/**
 * Redirect the standard output stream to a [String] during the execution of [block].
 */
fun redirectStdout(block: () -> Unit) = redirectOutput(System.out, System::setOut, block)

/**
 * Suppress any prompts for input by redirecting standard input to the null device.
 */
fun <T> suppressInput(block: () -> T): T {
    val originalInput = System.`in`

    val nullDevice = FileInputStream(if (Os.isWindows) "NUL" else "/dev/null")
    System.setIn(nullDevice)

    return try {
        block()
    } finally {
        System.setIn(originalInput)
    }
}
