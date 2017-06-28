package com.here.provenanceanalyzer

import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.jsonArray

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject

import com.here.provenanceanalyzer.managers.NPM

import java.io.File
import java.io.IOException

object OS {
    private val OS_NAME = System.getProperty("os.name").toLowerCase()

    val isWindows = OS_NAME.contains("windows")
}

class ProcessCapture(workingDir: File, vararg command: String) {
    val stdoutFile = File.createTempFile(command.first(), ".stdout")!!
    val stderrFile = File.createTempFile(command.first(), ".stderr")!!

    private val builder = ProcessBuilder(*command)
            .directory(workingDir)
            .redirectOutput(stdoutFile)
            .redirectError(stderrFile)

    private val process = builder.start()

    init {
        if (Main.debug) {
            Main.logger.debug("Keeping temporary files:")
            Main.logger.debug(stdoutFile.absolutePath)
            Main.logger.debug(stderrFile.absolutePath)
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
fun parseJsonProcessOutput(workingDir: File, vararg command: String): JsonElement {
    val process = ProcessCapture(workingDir, *command)
    if (process.exitValue() != 0) {
        throw IOException("${command.joinToString(" ")} failed with exit code ${process.exitValue()}: ${process.stderr()}")
    }

    val gson = Gson()

    // Wrap yarn's output, which is one JSON object per line, so the output as a whole can be parsed as a JSON array.
    if (command.first() == NPM.yarn && command.contains("--json")) {
        return jsonArray(process.stdoutFile.readLines().map { gson.fromJson<JsonObject>(it) })
    }

    return gson.fromJson<JsonElement>(process.stdout())
}
