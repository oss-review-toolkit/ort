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

package com.here.ort.model

import com.here.ort.utils.OS

/**
 * A description of the environment that ORT was executed in.
 */
data class Environment(
        /**
         * The version of ORT used.
         */
        val ortVersion: String = ORT_VERSION,

        /**
         * Name of the operating system, defaults to [OS.name].
          */
        val os: String = OS.name,

        /**
         * Map of selected environment variables that might be relevant for debugging.
         */
        val variables: Map<String, String> = System.getenv().mapKeys { (key, _) -> key.toUpperCase() }.let { env ->
            RELEVANT_VARIABLES.mapNotNull { key ->
                env[key]?.let { value -> key to value }
            }.toMap()
        },

        /**
         * Map of used tools and their installed versions, defaults to an empty map.
         */
        val toolVersions: Map<String, String> = emptyMap()
) {
    companion object {
        val ORT_VERSION = this::class.java.getResource("/VERSION").readText()

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
                "JAVA_HOME",
                "ANDROID_HOME",
                "GOPATH"
        )
    }
}
