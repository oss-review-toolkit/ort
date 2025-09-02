/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.utils.ort

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

import java.io.File
import java.util.jar.JarFile

import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.getConflictingKeys

/**
 * A description of the environment that ORT was executed in.
 */
@JsonIgnoreProperties("tool_versions")
data class Environment(
    /**
     * The version of the OSS Review Toolkit as a string.
     */
    val ortVersion: String = ORT_VERSION,

    /**
     * The version of Java used to build ORT.
     */
    val buildJdk: String = BUILD_JDK,

    /**
     * The version of Java used to run ORT.
     */
    val javaVersion: String = JAVA_VERSION,

    /**
     * Name of the operating system, defaults to [Os.Name].
     */
    val os: String = Os.Name(),

    /**
     * The number of logical processors available.
     */
    val processors: Int = Runtime.getRuntime().availableProcessors(),

    /**
     * The maximum amount of memory available.
     */
    val maxMemory: Long = Runtime.getRuntime().maxMemory(),

    /**
     * Map of selected environment variables that might be relevant for debugging.
     */
    val variables: Map<String, String> = RELEVANT_VARIABLES.mapNotNull { key ->
        Os.env[key]?.let { value -> key to value }
    }.toMap()
) {
    companion object {
        /**
         * The version of Java used to build ORT.
         */
        val BUILD_JDK: String by lazy {
            runCatching {
                val codeSource = this::class.java.protectionDomain.codeSource
                JarFile(codeSource?.location?.file).use {
                    it.manifest.mainAttributes.getValue("Build-Jdk")
                }
            }.getOrDefault(JAVA_VERSION)
        }

        /**
         * The version of Java used to run ORT.
         */
        val JAVA_VERSION: String by lazy { System.getProperty("java.version") }

        private val RELEVANT_VARIABLES = listOf(
            // ORT variables.
            ORT_DATA_DIR_ENV_NAME,
            ORT_CONFIG_DIR_ENV_NAME,
            ORT_TOOLS_DIR_ENV_NAME,
            // Windows variables.
            "USERPROFILE",
            "OS",
            "COMSPEC",
            // Unix variables.
            "HOME",
            "OSTYPE",
            "HOSTTYPE",
            "SHELL",
            "TERM",
            // General variables.
            "http_proxy",
            "https_proxy",
            "JAVA_HOME",
            "ANDROID_HOME",
            "GOPATH"
        )
    }

    /**
     * Merge this [Environment] with the given [other] [Environment].
     *
     * Both [Environment]s must have the same properties except for [variables], otherwise an [IllegalArgumentException]
     * is thrown.
     */
    operator fun plus(other: Environment) =
        Environment(
            ortVersion = ortVersion.also {
                require(it == other.ortVersion) {
                    "Cannot merge Environments with different ORT versions: '$ortVersion' != '${other.ortVersion}'."
                }
            },
            buildJdk = buildJdk.also {
                require(it == other.buildJdk) {
                    "Cannot merge Environments with different build JDKs: '$buildJdk' != '${other.buildJdk}'."
                }
            },
            javaVersion = javaVersion.also {
                require(it == other.javaVersion) {
                    "Cannot merge Environments with different Java versions: '$javaVersion' != '${other.javaVersion}'."
                }
            },
            os = os.also {
                require(it == other.os) {
                    "Cannot merge Environments with different operating systems: '$os' != '${other.os}'."
                }
            },
            processors = processors.also {
                require(it == other.processors) {
                    "Cannot merge Environments with different processor counts: '$processors' != '${other.processors}'."
                }
            },
            maxMemory = maxMemory.also {
                require(it == other.maxMemory) {
                    "Cannot merge Environments with different memory amounts: '$maxMemory' != '${other.maxMemory}'."
                }
            },
            variables = variables.getConflictingKeys(other.variables).let { conflictingKeys ->
                require(conflictingKeys.isEmpty()) {
                    "Cannot merge Environments with conflicting variable keys: $conflictingKeys"
                }

                variables + other.variables
            }
        )
}

/**
 * The directory to store ORT (read-write) data in, like archives, caches, configuration, and tools. Defaults to the
 * ".ort" directory below the current user's home directory.
 */
val ortDataDirectory by lazy {
    Os.env[ORT_DATA_DIR_ENV_NAME]?.takeUnless {
        it.isEmpty()
    }?.let {
        File(it)
    } ?: (Os.userHomeDirectory / ".ort")
}

/**
 * The directory to store ORT (read-only) configuration in. Defaults to the "config" directory below the data directory.
 */
val ortConfigDirectory by lazy {
    Os.env[ORT_CONFIG_DIR_ENV_NAME]?.takeUnless {
        it.isEmpty()
    }?.let {
        File(it)
    } ?: (ortDataDirectory / "config")
}

/**
 * The directory to store ORT (read-write) tools in. Defaults to the "tools" directory below the data directory.
 */
val ortToolsDirectory by lazy {
    Os.env[ORT_TOOLS_DIR_ENV_NAME]?.takeUnless {
        it.isEmpty()
    }?.let {
        File(it)
    } ?: (ortDataDirectory / "tools")
}
