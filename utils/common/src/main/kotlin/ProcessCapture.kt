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

import java.io.File
import java.io.IOException

import kotlin.io.path.createTempDirectory

import org.apache.logging.log4j.kotlin.Logging

/**
 * An (almost) drop-in replacement for ProcessBuilder that is able to capture huge outputs to the standard output and
 * standard error streams by redirecting output to temporary files.
 */
class ProcessCapture(vararg command: String, workingDir: File? = null, environment: Map<String, String> = emptyMap()) {
    // A convenience constructor to avoid the need for a named parameter if only the [workingDir] argument needs to be
    // specified. Even in unambiguous cases Kotlin unfortunately requires named parameters for arguments that follow
    // vararg parameters, see https://stackoverflow.com/a/46456379/1127485.
    constructor(workingDir: File?, vararg command: String) : this(*command, workingDir = workingDir)

    companion object : Logging {
        private const val MAX_OUTPUT_LINES = 20
        private const val MAX_OUTPUT_FOOTER =
            "(Above output is limited to each $MAX_OUTPUT_LINES heading and tailing lines.)"

        private fun limitOutputLines(message: String): String {
            val lines = message.lines()
            val lineCount = lines.size

            return if (lineCount > MAX_OUTPUT_LINES * 2) {
                val prefix = lines.take(MAX_OUTPUT_LINES)
                val suffix = lines.takeLast(MAX_OUTPUT_LINES)
                val skippedLineCount = lineCount - MAX_OUTPUT_LINES * 2

                // Insert an ellipsis in the middle of a long message.
                (prefix + "[...skipping $skippedLineCount lines...]" + suffix + MAX_OUTPUT_FOOTER).joinToString("\n")
            } else {
                message
            }
        }
    }

    private val tempDir = createTempDirectory("$command-process").toFile().apply { deleteOnExit() }
    private val tempPrefix = command.first().substringAfterLast(File.separatorChar)

    private val stdoutFile = tempDir.resolve("$tempPrefix.stdout").apply { deleteOnExit() }
    private val stderrFile = tempDir.resolve("$tempPrefix.stderr").apply { deleteOnExit() }

    /**
     * Get the standard output stream of the terminated process as a string.
     */
    val stdout
        get() = stdoutFile.readText()

    /**
     * Get the standard error stream of the terminated process as a string.
     */
    val stderr
        get() = stderrFile.readText()

    private val builder = ProcessBuilder(*command)
        .directory(workingDir)
        .redirectOutput(stdoutFile)
        .redirectError(stderrFile)
        .apply {
            environment().putAll(environment)
        }

    val commandLine = command.joinToString(" ")
    val usedWorkingDir = builder.directory() ?: System.getProperty("user.dir")!!

    private val process = builder.start()

    /**
     * Get the exit value of the terminated process.
     */
    val exitValue
        get() = process.exitValue()

    /**
     * Is true if the process terminated with an error, i.e. the [exitValue] is not 0.
     */
    val isError
        get() = exitValue != 0

    /**
     * Is true if the process terminated without an error, i.e. the [exitValue] is 0.
     */
    val isSuccess
        get() = exitValue == 0

    /**
     * A generic error message, can be used when [exitValue] is not 0.
     */
    val errorMessage
        get(): String {
            // Fall back to stdout for the error message if stderr does not provide meaningful information.
            val message = stderr.takeUnless {
                val notContainsErrorButStdoutDoes =
                    !it.contains("error", ignoreCase = true) && stdout.contains("error", ignoreCase = true)
                it.isBlank() || notContainsErrorButStdoutDoes
            } ?: stdout

            return "Running '$commandLine' in '$usedWorkingDir' failed with exit code $exitValue:\n" +
                    limitOutputLines(message)
        }

    init {
        logger.info {
            "Running '$commandLine' in '$usedWorkingDir'..."
        }

        process.waitFor()

        if (logger.delegate.isDebugEnabled) {
            // No need to use curly-braces-syntax for logging below as the log level check is already done above.

            if (stdoutFile.length() > 0L) {
                limitOutputLines(stdout).lines().forEach { logger.debug(it) }
            }

            if (stderrFile.length() > 0L) {
                limitOutputLines(stderr).lines().forEach { logger.debug(it) }
            }
        }
    }

    /**
     * Throw an [IOException] in case [exitValue] is not 0.
     */
    fun requireSuccess() = also { if (isError) throw IOException(errorMessage) }
}
