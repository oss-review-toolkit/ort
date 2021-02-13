/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.utils

import com.vdurmont.semver4j.Requirement
import com.zaxxer.nuprocess.NuProcess
import com.zaxxer.nuprocess.NuProcessBuilder
import com.zaxxer.nuprocess.codec.NuAbstractCharsetHandler

import java.io.File
import java.io.IOException
import java.lang.IllegalArgumentException
import java.lang.StringBuilder
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CoderResult
import java.util.concurrent.TimeUnit

/**
 * An interface to implement by classes that are backed by a command line tool.
 */
interface CommandLineTool {
    companion object {
        /**
         * A convenience property to require any version.
         */
        val ANY_VERSION: Requirement = Requirement.buildNPM("*")
    }

    /**
     * Return the name of the executable command. As the preferred command might depend on the directory to operate in
     * the [workingDir] can be provided.
     */
    fun command(workingDir: File? = null): String

    /**
     * Get the arguments to pass to the command in order to gets its version.
     */
    fun getVersionArguments() = "--version"

    /**
     * Transform the command's version output to a string that only contains the version.
     */
    fun transformVersion(output: String) = output

    /**
     * Return the requirement for the version of the command. Defaults to any version.
     */
    fun getVersionRequirement() = ANY_VERSION

    /**
     * Return whether the executable for this command is available in the system PATH.
     */
    fun isInPath() = getPathFromEnvironment(command()) != null

    /**
     * Run the command in the [workingDir] directory with arguments as specified by [args] and the given [environment].
     */
    fun run(vararg args: String, workingDir: File? = null, environment: Map<String, String> = emptyMap()) =
        ProcessCapture(command(workingDir), *args, workingDir = workingDir, environment = environment)
            .requireSuccess()

    /**
     * Run the command in the [workingDir] directory with arguments as specified by [args].
     */
    fun run(workingDir: File?, vararg args: String) =
        ProcessCapture(workingDir, command(workingDir), *args).requireSuccess()

    /**
     * Get the version of the command by parsing its output.
     */
    fun getVersion(workingDir: File? = null): String {
        val version = run(workingDir, *getVersionArguments().split(' ').toTypedArray())

        // Some tools actually report the version to stderr, so try that as a fallback.
        val versionString = sequenceOf(version.stdout, version.stderr).map {
            transformVersion(it.trim())
        }.find {
            it.isNotBlank()
        }

        return versionString.orEmpty()
    }

    /**
     * Run a [command] to check its version against the [required version][getVersionRequirement].
     */
    fun checkVersion(ignoreActualVersion: Boolean = false, workingDir: File? = null) {
        val actualVersion = getVersion(workingDir)
        val requiredVersion = getVersionRequirement()

        if (!requiredVersion.isSatisfiedBy(actualVersion)) {
            val message = "Unsupported ${command()} version $actualVersion does not fulfill $requiredVersion."
            if (ignoreActualVersion) {
                log.warn { "$message Still continuing because you chose to ignore the actual version." }
            } else {
                throw IOException(message)
            }
        }
    }
}

private const val TIMEOUT_INFINITE = 0L

class CommandLineTool2(
    private val name: String,
    private val versionArguments: Array<String> = arrayOf("--version"),
    private val versionTransformation: (String) -> String = { it.substringAfterLast(" ", "") }
) {
    class ProcessResult(val exitCode: Int, stdout: StringBuilder, stderr: StringBuilder) {
        val stdout by lazy { stdout.toString().trim() }
        val stderr by lazy { stderr.toString().trim() }

        fun transformOutput(transformation: (String) -> String): String {
            return sequenceOf({ stdout }, { stderr }).map { transformation(it()) }.find { it.isNotBlank() }.orEmpty()
        }
    }

    operator fun invoke(
        vararg args: String,
        path: File? = null,
        env: Map<String, String>? = null
    ): Result<ProcessResult> {
        val commandPath = path?.resolve(name)?.let { resolvedPath ->
            (resolveWindowsExecutable(resolvedPath)?.takeIf { Os.isWindows } ?: resolvedPath).path
        } ?: name

        val process = NuProcessBuilder(handler, commandPath, *args).apply {
            env?.let { environment().putAll(it) }
        }.start()

        val exitCode = process.waitFor(TIMEOUT_INFINITE, TimeUnit.SECONDS)

        return if (exitCode == Integer.MIN_VALUE) {
            Result.failure(IllegalArgumentException("Unable to start process for '$commandPath'."))
        } else {
            Result.success(ProcessResult(exitCode, stdout, stderr))
        }
    }

    fun version(path: File? = null, env: Map<String, String>? = null): String {
        val result = invoke(*versionArguments, path = path, env = env).getOrThrow()
        return result.transformOutput(versionTransformation)
    }

    private val stdout = StringBuilder()
    private val stderr = StringBuilder()

    private val handler = object : NuAbstractCharsetHandler(Charset.defaultCharset()) {
        override fun onStart(nuProcess: NuProcess) {
            stdout.clear()
            stderr.clear()
        }

        override fun onStdoutChars(buffer: CharBuffer, closed: Boolean, coderResult: CoderResult) {
            if (closed) return
            stdout.append(buffer)
        }

        override fun onStderrChars(buffer: CharBuffer, closed: Boolean, coderResult: CoderResult) {
            if (closed) return
            stderr.append(buffer)
        }
    }
}
