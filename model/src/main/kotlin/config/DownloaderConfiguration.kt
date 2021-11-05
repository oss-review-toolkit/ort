/*
 * Copyright (C) 2021 Bosch.IO GmbH
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
import org.ossreviewtoolkit.utils.common.getDuplicates

data class DownloaderConfiguration(
    /**
     * Toggle whether to allow downloads using symbolic names that point to moving revisions, like Git branches.
     */
    val allowMovingRevisions: Boolean = false,

    /**
     * The [categories][LicenseCategory] licenses of packages need to be part of in order to get included into the
     * download, or an empty list to include all packages.
     */
    val includedLicenseCategories: List<String> = emptyList(),

    /**
     * Configuration of the considered source code origins and their priority order.
     */
    val sourceCodeOrigins: List<SourceCodeOrigin> = listOf(SourceCodeOrigin.VCS, SourceCodeOrigin.ARTIFACT)
) {
    init {
        require(sourceCodeOrigins.isNotEmpty()) {
            "'sourceCodeOrigins' must not be empty."
        }

        val duplicates = sourceCodeOrigins.getDuplicates()
        require(duplicates.isEmpty()) {
            "'sourceCodeOrigins' must not contain duplicates. Duplicates: $duplicates"
        }
    }
}
