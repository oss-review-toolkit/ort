package com.here.ort.util

import ch.frankel.slf4k.*

import java.io.File
import java.io.IOException

/**
 * An (almost) drop-in replacement for ProcessBuilder that is able to capture huge outputs to the standard output and
 * standard error streams by redirecting output to temporary files.
 */
class ProcessCapture(workingDir: File?, vararg command: String) {
    constructor(vararg command: String) : this(null, *command)

    val commandLine = command.joinToString(" ")

    @Suppress("UnsafeCallOnNullableType")
    val stdoutFile = File.createTempFile(command.first(), ".stdout")!!

    @Suppress("UnsafeCallOnNullableType")
    val stderrFile = File.createTempFile(command.first(), ".stderr")!!

    private val builder = ProcessBuilder(*command)
            .directory(workingDir)
            .redirectOutput(stdoutFile)
            .redirectError(stderrFile)

    private val process = builder.start()

    /**
     * A generic error message, can be used when [exitValue] is not 0.
     */
    val failMessage
        get() = "'$commandLine' in directory '${builder.directory()?.let { it } ?: System.getProperty("user.dir")}' " +
                "failed with exit code ${exitValue()}:\n${stderr()}"

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
        if (exitValue() != 0) {
            throw IOException(failMessage)
        }
        return this
    }
}
