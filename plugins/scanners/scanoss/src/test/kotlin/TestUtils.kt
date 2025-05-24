/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.plugins.api.Secret

// A test project name.
internal const val PROJECT = "scanoss-test-project"

// A (resolved) test revision.
private const val REVISION = "0123456789012345678901234567890123456789"

/**
 * Create a new [ScanOss] instance with the specified [config].
 */
internal fun createScanOss(config: ScanOssConfig): ScanOss = ScanOss(config = config)

/**
 * Create a standard [ScanOssConfig] whose properties can be partly specified.
 */
internal fun createScanOssConfig(
    apiUrl: String = ScanApi.DEFAULT_BASE_URL,
    apiKey: Secret = Secret(""),
    writeToStorage: Boolean = true,
    enablePathObfuscation: Boolean = false
): ScanOssConfig =
    ScanOssConfig(
        apiUrl = apiUrl,
        apiKey = apiKey,
        writeToStorage = writeToStorage,
        enablePathObfuscation = enablePathObfuscation
    )

/**
 * Create a [VcsInfo] object for a project with the given [name][projectName] and the optional parameters for [type],
 * [path], and [revision].
 */
internal fun createVcsInfo(
    projectName: String = PROJECT,
    type: VcsType = VcsType.GIT,
    path: String = "",
    revision: String = REVISION
): VcsInfo = VcsInfo(type = type, path = path, revision = revision, url = "https://github.com/test/$projectName.git")
