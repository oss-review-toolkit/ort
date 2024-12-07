/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.model.config

import org.ossreviewtoolkit.model.SourceCodeOrigin
import org.ossreviewtoolkit.model.licenses.LicenseCategory
import org.ossreviewtoolkit.model.utils.requireNotEmptyNoDuplicates

data class DownloaderConfiguration(
    /**
     * Toggle whether to allow downloads using symbolic names that point to moving revisions, like Git branches.
     */
    val allowMovingRevisions: Boolean = false,

    /**
     * The [categories][LicenseCategory] licenses of packages need to be part of to get included in the download, or an
     * empty list to include all packages.
     */
    val includedLicenseCategories: List<String> = emptyList(),

    /**
     * A flag to control whether excluded scopes and paths should be skipped during the download.
     */
    val skipExcluded: Boolean = false,

    /**
     * Configuration of the considered source code origins and their priority order. This must not be empty and not
     * contain any duplicates.
     */
    val sourceCodeOrigins: List<SourceCodeOrigin> = listOf(SourceCodeOrigin.VCS, SourceCodeOrigin.ARTIFACT),

    /**
     * Version control system specific configurations. The key needs to match VCS type,
     * e.g. "Git" for the Git version control system.
     */
    val versionControlSystems: Map<String, VersionControlSystemConfiguration> = emptyMap()
) {
    /**
     * A copy of [versionControlSystems] with case-insensitive keys.
     */
    private val versionControlSystemsCaseInsensitive: Map<String, VersionControlSystemConfiguration> =
        versionControlSystems.toSortedMap(String.CASE_INSENSITIVE_ORDER)

    init {
        sourceCodeOrigins.requireNotEmptyNoDuplicates()

        val duplicateVersionControlSystems =
            versionControlSystems.keys - versionControlSystemsCaseInsensitive.keys.toSet()

        require(duplicateVersionControlSystems.isEmpty()) {
            "The following version control systems have duplicate configuration: " +
                "${duplicateVersionControlSystems.joinToString()}."
        }
    }

    /**
     * Get a [VersionControlSystemConfiguration] from [versionControlSystems].
     * The difference to accessing the map directly is that VCS type can be case-insensitive.
     */
    fun getCaseInsensitiveVersionControlSystems() = versionControlSystemsCaseInsensitive
}
