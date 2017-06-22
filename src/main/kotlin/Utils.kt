package com.here.provenanceanalyzer

import java.io.File
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InputStreamReader
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
