package com.here.provenanceanalyzer

import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.jsonArray

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject

import com.here.provenanceanalyzer.managers.NPM

import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.IOException
import java.io.OutputStream

object OS {
    private val OS_NAME = System.getProperty("os.name").toLowerCase()

    val isWindows = OS_NAME.contains("windows")
}

class ProcessCapture(directory: File, vararg command: String) {
    private class StreamGobbler(private val stream: InputStream, private val writer: OutputStream) : Thread() {
        override fun run() {
            BufferedReader(InputStreamReader(stream)).use { reader ->
                do {
                    val value = reader.read()
                    if (value == -1) {
                        break
                    }
                    writer.write(value)
                } while (true)
            }
        }
    }

    private val builder = ProcessBuilder(*command).directory(directory)
    private val process = builder.start()
    private val stdoutStream = ByteArrayOutputStream()
    private val stderrStream = ByteArrayOutputStream()
    private val stdoutGobbler = StreamGobbler(process.inputStream, stdoutStream)
    private var stderrGobbler = StreamGobbler(process.errorStream, stderrStream)

    init {
        stdoutGobbler.start()
        stderrGobbler.start()

        process.waitFor()

        stdoutGobbler.join()
        stderrGobbler.join()
    }

    fun exitValue() = process.exitValue()

    fun stdout() = stdoutStream.toString()
    fun stderr() = stderrStream.toString()
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
        return jsonArray(process.stdout().lines().map { gson.fromJson<JsonObject>(it) })
    }

    return gson.fromJson<JsonElement>(process.stdout())
}
