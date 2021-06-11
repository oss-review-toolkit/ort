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

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.spdx.model.LicenseChoice
import org.ossreviewtoolkit.utils.ORT_REPO_CONFIG_FILENAME

/**
 * The license choices configured for a repository.
 */
data class LicenseChoices(
    /**
     * [LicenseChoice]s that are applied to all packages in the repository.
     * Since the [LicenseChoice] is applied to each package that offers this license as a choice, [LicenseChoice.given]
     * can not be null. This helps only applying the choice to a wanted [LicenseChoice.given] as opposed to all
     * licenses with that choice, which could lead to unwanted applied choices.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val repositoryLicenseChoices: List<LicenseChoice> = emptyList(),

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val packageLicenseChoices: List<PackageLicenseChoice> = emptyList()
) {
    @JsonIgnore
    fun isEmpty() = packageLicenseChoices.isEmpty() && repositoryLicenseChoices.isEmpty()

    init {
        val choicesWithoutGiven = repositoryLicenseChoices.filter { it.given == null }
        require(choicesWithoutGiven.isEmpty()) {
            "LicenseChoices ${choicesWithoutGiven.joinToString()} defined in $ORT_REPO_CONFIG_FILENAME are missing " +
                    "the 'given' expression."
        }
    }
}

/**
 * [LicenseChoice]s defined for an artifact.
 */
data class PackageLicenseChoice(
    val packageId: Identifier,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val licenseChoices: List<LicenseChoice> = emptyList()
)
