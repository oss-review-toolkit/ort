/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.evaluator.osadl

import kotlinx.serialization.Serializable

/**
 * The license compatibility matrix.
 */
@Serializable
internal data class MatrixLicenses(
    /** The Python-style format string for parsing the [timestamp]. */
    val timeformat: String,

    /** The timestamp when the matrix data was acquired. */
    val timestamp: String,

    /** The matrix of licenses stored as a list of rows. */
    val licenses: List<MatrixRow>
)

/**
 * A row in the license compatibility matrix.
 */
@Serializable
internal data class MatrixRow(
    /** The SPDX ID of the license this row refers to. */
    val name: String,

    /** The compatibility information for this license. */
    val compatibilities: List<MatrixCell>
)

/**
 * A cell in the license compatibility matrix.
 */
@Serializable
internal data class MatrixCell(
    /** The SPDX ID of the license this cell refers to. */
    val name: String,

    /** The compatibility information for this license. */
    val compatibility: Compatibility,

    /** The explanation for the stated compatibility, or "n.a." if none is available. */
    val explanation: String
)
