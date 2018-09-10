/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

package com.here.ort.utils

import ch.frankel.slf4k.*

import com.vdurmont.semver4j.Requirement
import com.vdurmont.semver4j.Semver

import java.io.File
import java.io.IOException

/**
 * An (almost) drop-in replacement for ProcessBuilder that is able to capture huge outputs to the standard output and
 * standard error streams by redirecting output to temporary files.
 */
class ProcessCapture(vararg command: String, workingDir: File? = null, environment: Map<String, String> = emptyMap()) {
    // A convenience constructor to avoid the need for a named parameter if only the [workingDir] argument needs to be
    // specified. Even in unambiguous cases Kotlin unfortunately requires named parameters for arguments that follow
    // vararg parameters, see https://stackoverflow.com/a/46456379/1127485.
    constructor(workingDir: File?, vararg command: String) : this(*command, workingDir = workingDir)

    companion object {
        private const val MAX_LOG_LINES = 20
        private const val MAX_LOG_LINES_MESSAGE = "(Above output is limited to $MAX_LOG_LINES lines.)"

        private fun logLinesLimited(prefix: String, lines: Sequence<String>, logLine: (String) -> Unit) {
            val chunkIterator = lines.chunked(MAX_LOG_LINES).iterator()
            chunkIterator.next().forEach { logLine("$prefix: $it") }

            // This actually already evaluates the next element, but since that is also only a chunk, not the whole
            // rest, we can live with that.
            if (chunkIterator.hasNext()) { logLine(MAX_LOG_LINES_MESSAGE) }
        }
    }

    private val tempDir = createTempDir("ort")
    private val tempPrefix = command.first().substringAfterLast(File.separatorChar)

    val stdoutFile = File(tempDir, "$tempPrefix.stdout").apply { deleteOnExit() }
    val stderrFile = File(tempDir, "$tempPrefix.stderr").apply { deleteOnExit() }

    private val builder = ProcessBuilder(*command)
            .directory(workingDir)
            .redirectOutput(stdoutFile)
            .redirectError(stderrFile)
            .apply {
                environment().putAll(environment)
            }

    val commandLine = command.joinToString(" ")
    val usedWorkingDir = builder.directory() ?: System.getProperty("user.dir")

    private val process = builder.start()

    /**
     * A generic error message, can be used when [exitValue] is not 0.
     */
    val errorMessage
        get(): String {
            var message = stderr().takeUnless { it.isBlank() } ?: stdout()

            // Insert ellipsis in the middle of a long error message.
            val lines = message.lines()
            if (lines.count() > MAX_LOG_LINES) {
                val prefix = lines.take(MAX_LOG_LINES / 2)
                val suffix = lines.takeLast(MAX_LOG_LINES / 2)
                message = (prefix + "[...]" + suffix + MAX_LOG_LINES_MESSAGE).joinToString("\n")
            }

            return "Running '$commandLine' in '$usedWorkingDir' failed with exit code ${exitValue()}:\n$message"
        }

    init {
        log.info {
            "Running '$commandLine' in '$usedWorkingDir'..."
        }

        process.waitFor()

        if (log.isDebugEnabled) {
            // No need to use curly-braces-syntax for logging below as the log level check is already done above.

            if (stdoutFile.length() > 0L) {
                stdoutFile.useLines { lines ->
                    logLinesLimited("stdout", lines) { log.debug(it) }
                }
            }

            if (stderrFile.length() > 0L) {
                stderrFile.useLines { lines ->
                    logLinesLimited("stderr", lines) { log.debug(it) }
                }
            }
        }
    }

    /**
     * Return the exit value of the terminated process.
     */
    fun exitValue() = process.exitValue()

    /**
     * Return true if the [exitValue] is not 0.
     */
    fun isError() = exitValue() != 0

    /**
     * Return true if the [exitValue] is 0.
     */
    fun isSuccess() = exitValue() == 0

    /**
     * Return the standard output stream of the terminated process as a string.
     */
    fun stdout() = stdoutFile.readText()

    /**
     * Return the standard errors stream of the terminated process as a string.
     */
    fun stderr() = stderrFile.readText()

    /**
     * Throw an [IOException] in case [exitValue] is not 0.
     */
    fun requireSuccess(): ProcessCapture {
        if (isError()) {
            throw IOException(errorMessage)
        }
        return this
    }
}

/**
 * Run a [command] to check its version against a [requirement].
 */
fun checkCommandVersion(
        command: String,
        requirement: Requirement,
        versionArguments: String = "--version",
        workingDir: File? = null,
        ignoreActualVersion: Boolean = false,
        transform: (String) -> String = { it }
) {
    val toolVersionOutput = getCommandVersion(command, versionArguments, workingDir, transform)
    val actualVersion = Semver(toolVersionOutput, Semver.SemverType.LOOSE)
    if (!requirement.isSatisfiedBy(actualVersion)) {
        val message = "Unsupported $command version $actualVersion does not fulfill $requirement."
        if (ignoreActualVersion) {
            log.warn { "$message Still continuing because you chose to ignore the actual version." }
        } else {
            throw IOException(message)
        }
    }
}

/**
 * Run a [command] to get its version.
 */
fun getCommandVersion(
        command: String,
        versionArguments: String = "--version",
        workingDir: File? = null,
        transform: (String) -> String = { it }
): String {
    val commandLine = arrayOf(command) + versionArguments.split(' ')
    val version = ProcessCapture(workingDir, *commandLine).requireSuccess()

    // Some tools, like pipdeptree, actually report the version to stderr.
    val versionString = sequenceOf(version.stdout(), version.stderr()).map {
        transform(it.trim())
    }.find {
        it.isNotBlank()
    }

    return versionString ?: ""
}
