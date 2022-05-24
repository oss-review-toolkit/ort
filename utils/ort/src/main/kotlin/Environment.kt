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

package org.ossreviewtoolkit.utils.ort

import java.lang.Runtime

import org.ossreviewtoolkit.utils.common.Os

/**
 * A description of the environment that ORT was executed in.
 */
data class Environment(
    /**
     * The version of ORT used.
     */
    val ortVersion: String = ORT_VERSION,

    /**
     * The version of Java used.
     */
    val javaVersion: String = System.getProperty("java.version"),

    /**
     * Name of the operating system, defaults to [Os.name].
     */
    val os: String = Os.name,

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
    }.toMap(),

    /**
     * Map of used tools and their installed versions, defaults to an empty map.
     */
    val toolVersions: Map<String, String> = emptyMap()
) {
    companion object {
        /**
         * The version of the OSS Review Toolkit as a string.
         */
        val ORT_VERSION by lazy { this::class.java.`package`.implementationVersion ?: "IDE-SNAPSHOT" }

        /**
         * A string that is supposed to be used as the User Agent when using ORT as an HTTP client.
         */
        val ORT_USER_AGENT = "$ORT_NAME/$ORT_VERSION"

        private val RELEVANT_VARIABLES = listOf(
            // Windows variables.
            "OS",
            "COMSPEC",
            // Unix variables.
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
}
