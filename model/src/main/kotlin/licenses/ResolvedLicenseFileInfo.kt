/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.model.licenses

import java.io.File

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.utils.ort.CopyrightStatementsProcessor

/**
 * Information about license files of a package and the licenses detected in those files.
 */
data class ResolvedLicenseFileInfo(
    /**
     * The identifier of the package.
     */
    val id: Identifier,

    /**
     * The resolved license files.
     */
    val files: List<ResolvedLicenseFile>
)

/**
 * Information about a single resolved license file.
 */
data class ResolvedLicenseFile(
    /**
     * The [Provenance] of the license file.
     */
    val provenance: Provenance,

    /**
     * The [ResolvedLicense]s detected in this file. Contains the whole resolved license including license and copyright
     * locations found in other files with the same [provenance].
     */
    val licenses: List<ResolvedLicense>,

    /**
     *  The path of the license file relative to the [provenance].
     */
    val path: String,

    /**
     * The unarchived license file.
     */
    val file: File
) {
    /**
     * Return all copyright statements associated to the licenses found in this license file. Copyright findings that
     * are excluded by [PathExclude]s are [omitted][omitExcluded] by default. The copyright statements are by default
     * [reprocessed][process] using the [CopyrightStatementsProcessor].
     */
    @JvmOverloads
    fun getCopyrights(process: Boolean = true, omitExcluded: Boolean = true): Set<String> =
        licenses.flatMapTo(mutableSetOf()) { it.getCopyrights(process, omitExcluded) }

    /**
     * The textual content of the [license file][file].
     */
    val text by lazy { file.readText() }
}
