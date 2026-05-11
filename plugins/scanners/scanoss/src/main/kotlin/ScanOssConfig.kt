/*
 * Copyright (C) 2022 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.scanners.scanoss

import com.scanoss.rest.ScanApi

import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.Secret

data class ScanOssConfig(
    /** The URL of the SCANOSS server. */
    @OrtPluginOption(defaultValue = ScanApi.DEFAULT_BASE_URL)
    val apiUrl: String,

    /** The API key used to authenticate with the SCANOSS server. */
    @OrtPluginOption(defaultValue = "")
    val apiKey: Secret,

    /**
     * Whether to write scan results to the storage.
     */
    @OrtPluginOption(defaultValue = "true")
    val writeToStorage: Boolean,

    /**
     * Whether to enable path obfuscation when sending file paths to the SCANOSS server.
     * When enabled, the actual file paths will be obfuscated in the requests to protect sensitive information.
     */
    @OrtPluginOption(defaultValue = "false")
    val enablePathObfuscation: Boolean,

    /**
     * The minimum number of snippet matches required to report a snippet finding.
     * This parameter controls the quality filter for snippet detection results.
     * A higher value reduces false positives but may miss some legitimate matches.
     */
    @OrtPluginOption(defaultValue = "5")
    val minSnippetHits: Int,

    /**
     * The minimum number of lines required in a snippet match to report a finding.
     * This parameter controls the minimum length threshold for snippet detections.
     * Snippets shorter than this will be filtered out.
     */
    @OrtPluginOption(defaultValue = "3")
    val minSnippetLines: Int,

    /**
     * Whether to honour file extension matching when detecting snippets.
     * If enabled, snippet matches must be within files with matching extension to the source file.
     * Set to false to allow matches across different file types.
     */
    @OrtPluginOption(defaultValue = "true")
    val honourFileExts: Boolean,

    /**
     * Whether to enable ranking-based filtering for snippet results.
     * When enabled, snippets are filtered based on the ranking threshold value.
     * Set to false to disable ranking-based filtering.
     */
    @OrtPluginOption(defaultValue = "false")
    val rankingEnabled: Boolean,

    /**
     * The ranking threshold used to filter snippet results when ranking is enabled.
     * Snippets with a ranking score below this threshold will be filtered out.
     * This value is typically between 0 and 100. Only used when rankingEnabled is true.
     */
    @OrtPluginOption(defaultValue = "0")
    val rankingThreshold: Int,

    /**
     * Whether to skip header files (files with common header extensions) when detecting snippets.
     * When enabled, files matching standard header patterns will be excluded from snippet matching.
     * Set to false to include header files in snippet detection.
     */
    @OrtPluginOption(defaultValue = "false")
    val skipHeaders: Boolean,

    /**
     * The maximum number of lines to skip in header files when skipHeaders is enabled.
     * This limits how many lines at the beginning of a file are considered "headers" for skipping.
     * Set to 0 to skip the entire header file if skipHeaders is enabled.
     */
    @OrtPluginOption(defaultValue = "0")
    val skipHeadersLimit: Int
)
