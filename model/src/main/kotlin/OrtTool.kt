/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.model

/**
 * An enum representing the different ORT tools and how they depend on each other.
 */
enum class OrtTool(
    /**
     * Tools whose output is required to run this tool.
     */
    val requiredInput: Set<OrtTool>,

    /**
     * Tools whose output is used if it is available.
     */
    val optionalInput: Set<OrtTool>,

    /**
     * The different [OrtConfigType]s consumed by the tool.
     */
    val consumedConfig: Set<OrtConfigType>
) {
    ANALYZER(
        requiredInput = emptySet(),
        optionalInput = emptySet(),
        consumedConfig = setOf(
            OrtConfigType.ORT_CONFIG,
            OrtConfigType.PACKAGE_CURATIONS,
            OrtConfigType.RESOLUTIONS
        )
    ),

    ADVISOR(
        requiredInput = setOf(ANALYZER),
        optionalInput = emptySet(),
        consumedConfig = setOf(
            OrtConfigType.ORT_CONFIG,
            OrtConfigType.RESOLUTIONS
        )
    ),

    SCANNER(
        requiredInput = setOf(ANALYZER),
        optionalInput = emptySet(),
        consumedConfig = setOf(
            OrtConfigType.ORT_CONFIG,
            OrtConfigType.RESOLUTIONS
        )
    ),

    EVALUATOR(
        requiredInput = setOf(ANALYZER),
        optionalInput = setOf(ADVISOR, SCANNER),
        consumedConfig = setOf(
            OrtConfigType.COPYRIGHT_GARBAGE,
            OrtConfigType.EVALUATOR_RULES,
            OrtConfigType.LICENSE_CHOICES,
            OrtConfigType.LICENSE_CLASSIFICATIONS,
            OrtConfigType.ORT_CONFIG,
            OrtConfigType.PACKAGE_CONFIGURATIONS,
            OrtConfigType.PACKAGE_CURATIONS,
            OrtConfigType.RESOLUTIONS
        )
    ),

    REPORTER(
        requiredInput = setOf(ANALYZER),
        optionalInput = setOf(ADVISOR, SCANNER, EVALUATOR),
        consumedConfig = setOf(
            OrtConfigType.COPYRIGHT_GARBAGE,
            OrtConfigType.CUSTOM_LICENSE_TEXTS,
            OrtConfigType.HOW_TO_FIX_TEXTS,
            OrtConfigType.LICENSE_CHOICES,
            OrtConfigType.LICENSE_CLASSIFICATIONS,
            OrtConfigType.ORT_CONFIG,
            OrtConfigType.PACKAGE_CONFIGURATIONS,
            OrtConfigType.PACKAGE_CURATIONS,
            OrtConfigType.RESOLUTIONS
        )
    ),

    NOTIFIER(
        requiredInput = setOf(ANALYZER),
        optionalInput = setOf(ADVISOR, SCANNER, EVALUATOR),
        consumedConfig = setOf(
            OrtConfigType.COPYRIGHT_GARBAGE,
            OrtConfigType.CUSTOM_LICENSE_TEXTS,
            OrtConfigType.LICENSE_CHOICES,
            OrtConfigType.LICENSE_CLASSIFICATIONS,
            OrtConfigType.ORT_CONFIG,
            OrtConfigType.PACKAGE_CONFIGURATIONS,
            OrtConfigType.PACKAGE_CURATIONS,
            OrtConfigType.RESOLUTIONS
        )
    );

    val alias = name.lowercase()
    val input = requiredInput + optionalInput
}
