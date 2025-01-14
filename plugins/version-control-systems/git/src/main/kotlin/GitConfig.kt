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

package org.ossreviewtoolkit.plugins.versioncontrolsystems.git

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.plugins.api.OrtPluginOption

const val DEFAULT_HISTORY_DEPTH = 50

/** Git-specific [VersionControlSystem] configuration. */
data class GitConfig(
    /** Depth of the commit history to fetch. */
    @OrtPluginOption(defaultValue = "$DEFAULT_HISTORY_DEPTH")
    val historyDepth: Int,

    /** Whether nested submodules should be updated, or if only top-level submodules should be considered. */
    @OrtPluginOption(defaultValue = "true")
    val updateNestedSubmodules: Boolean
)
