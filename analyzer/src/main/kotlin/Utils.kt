package com.here.provenanceanalyzer

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory

import com.here.provenanceanalyzer.model.jsonMapper

import com.vdurmont.semver4j.Semver

import java.io.File
import java.io.IOException

/**
 * Operating-System-specific utility functions.
 */
object OS {
    private val OS_NAME = System.getProperty("os.name").toLowerCase()

    val isWindows = OS_NAME.contains("windows")
}

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

    init {
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
}

/**
 * Parse the standard output of a process as JSON.
 */
fun parseJsonProcessOutput(workingDir: File, vararg command: String, multiJson: Boolean = false): JsonNode {
    val process = ProcessCapture(workingDir, *command)
    if (process.exitValue() != 0) {
        throw IOException(
                "'${process.commandLine}' failed with exit code ${process.exitValue()}:\n${process.stderr()}")
    }

    // Support parsing multiple lines with one JSON object per line by wrapping the whole output into a JSON array.
    if (multiJson) {
        val array = JsonNodeFactory.instance.arrayNode()
        process.stdoutFile.readLines().forEach { array.add(jsonMapper.readTree(it)) }
        return array
    }

    return jsonMapper.readTree(process.stdout())
}

/**
 * Run a command to check it for specific version.
 */
fun requireCommandVersion(command: String, expectedVersion: Semver, versionArgument: String = "--version") {
    val version = ProcessCapture(command, versionArgument)
    if (version.exitValue() != 0) {
        throw IOException("Unable to determine the $command version:\n${version.stderr()}")
    }

    var versionString = version.stdout().trim()
    if (versionString.isEmpty()) {
        // Fall back to trying to read the version from stderr.
        versionString = version.stderr().trim()
    }

    val actualVersion = Semver(versionString, expectedVersion.type)
    if (actualVersion != expectedVersion) {
        throw IOException(
                "Unsupported $command version $actualVersion, version $expectedVersion is required.")
    }
}
