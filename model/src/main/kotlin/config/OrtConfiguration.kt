/*
 * Copyright (C) 2019 HERE Europe B.V.
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

package com.here.ort.model.config

import com.here.ort.utils.expandTilde

import com.typesafe.config.ConfigFactory

import io.github.config4k.extract

import java.io.File

/**
 * The configuration model for all ORT components.
 */
data class OrtConfiguration(
    /**
     * The configuration of the scanner.
     */
    val scanner: ScannerConfiguration? = null
) {
    companion object {
        /**
         * Load the [OrtConfiguration]. The different sources are used with this priority:
         *
         * 1. [Command line arguments][args]
         * 2. [Configuration file][configFile]
         * 3. default.conf from resources
         */
        fun load(args: Map<String, String> = emptyMap(), configFile: File? = null): OrtConfiguration {
            val argsConfig = ConfigFactory.parseMap(args, "Command line").withOnlyPath("ort")
            val fileConfig = configFile?.expandTilde()?.let {
                ConfigFactory.parseFile(it).withOnlyPath("ort")
            }
            val defaultConfig = ConfigFactory.parseResources("default.conf")

            var combinedConfig = argsConfig
            if (fileConfig != null) {
                combinedConfig = combinedConfig.withFallback(fileConfig)
            }
            combinedConfig = combinedConfig.withFallback(defaultConfig).resolve()

            return combinedConfig.extract("ort")
        }
    }
}
