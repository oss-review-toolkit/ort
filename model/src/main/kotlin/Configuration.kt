/*
 * Copyright (c) 2017 HERE Europe B.V.
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

import com.here.ort.utils.yamlMapper

import java.io.File

data class Configuration(
        val scanner: ScannerConfiguration?
) {
    companion object {
        var value = Configuration(
                ScannerConfiguration(null)
        )

        fun parse(configFile: File): Configuration = yamlMapper.readValue(configFile, Configuration::class.java)
                .also { value = it }

        fun parse(config: String): Configuration = yamlMapper.readValue(config, Configuration::class.java)
                .also { value = it }
    }
}

data class ScannerConfiguration(
        val cache: CacheConfiguration?
)

data class CacheConfiguration(
        val artifactory: ArtifactoryCache?
)

data class ArtifactoryCache(
        val url: String,
        val apiToken: String
)
