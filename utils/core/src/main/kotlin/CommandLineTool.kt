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

package org.ossreviewtoolkit.utils.core

import com.vdurmont.semver4j.Requirement

import java.io.File
import java.io.IOException

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
     * Get the arguments to pass to the command in order to get its version.
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
