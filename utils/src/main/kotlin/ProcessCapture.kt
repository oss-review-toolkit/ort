/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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
class ProcessCapture(workingDir: File?, vararg command: String) {
    constructor(vararg command: String) : this(null, *command)

    val commandLine = command.joinToString(" ")

    private val tempDir = createTempDir("ort")
    private val tempPrefix = command.first().substringAfterLast(File.separatorChar)

    val stdoutFile = File(tempDir, "$tempPrefix.stdout")
    val stderrFile = File(tempDir, "$tempPrefix.stderr")

    private val builder = ProcessBuilder(*command)
            .directory(workingDir)
            .redirectOutput(stdoutFile)
            .redirectError(stderrFile)

    private val process = builder.start()

    /**
     * A generic error message, can be used when [exitValue] is not 0.
     */
    val failMessage
        get(): String {
            val workingDir = builder.directory() ?: System.getProperty("user.dir")
            val message = stderr().takeUnless { it.isBlank() } ?: stdout()
            return "Running '$commandLine' in directory '$workingDir' failed with exit code ${exitValue()}:\n$message"
        }

    init {
        log.info { "Running '$commandLine'..." }

        if (log.isDebugEnabled) {
            // No need to use curly-braces-syntax for logging here as the log level check is already done above.
            log.debug("Keeping temporary files:")
            log.debug(stdoutFile.absolutePath)
            log.debug(stderrFile.absolutePath)
        } else {
            stdoutFile.deleteOnExit()
            stderrFile.deleteOnExit()
        }

        process.waitFor()
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
            throw IOException(failMessage)
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
            println("Still continuing because you chose to ignore the actual version.")
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
    val commandLine = arrayOf(command).plus(versionArguments.split(' '))
    val version = ProcessCapture(workingDir, *commandLine).requireSuccess()

    // Some tools, like pipdeptree, actually report the version to stderr.
    val versionString = sequenceOf(version.stdout(), version.stderr()).map {
        transform(it.trim())
    }.find {
        it.isNotBlank()
    }

    return versionString ?: ""
}
