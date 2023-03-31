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

import org.ossreviewtoolkit.utils.ort.ORT_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_COPYRIGHT_GARBAGE_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_CUSTOM_LICENSE_TEXTS_DIRNAME
import org.ossreviewtoolkit.utils.ort.ORT_EVALUATOR_RULES_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_HOW_TO_FIX_TEXT_PROVIDER_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_LICENSE_CLASSIFICATIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_NOTIFIER_SCRIPT_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_PACKAGE_CONFIGURATIONS_DIRNAME
import org.ossreviewtoolkit.utils.ort.ORT_PACKAGE_CONFIGURATION_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_PACKAGE_CURATIONS_DIRNAME
import org.ossreviewtoolkit.utils.ort.ORT_PACKAGE_CURATIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_REPO_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_RESOLUTIONS_FILENAME

/**
 * An enum representing the different ORT configuration files.
 */
enum class OrtConfigFile(
    /**
     * The default name of the config file.
     */
    val defaultFilename: String,

    /**
     * The default name of the config directory, if the config can be read from multiple files inside a directory.
     */
    val defaultDirectoryName: String? = null,

    /**
     * The [OrtConfigType]s provided by the config file.
     */
    val providedTypes: Set<OrtConfigType>
) {
    COPYRIGHT_GARBAGE(
        defaultFilename = ORT_COPYRIGHT_GARBAGE_FILENAME,
        providedTypes = setOf(OrtConfigType.COPYRIGHT_GARBAGE)
    ),

    CUSTOM_LICENSE_TEXTS(
        defaultFilename = ORT_CUSTOM_LICENSE_TEXTS_DIRNAME,
        providedTypes = setOf(OrtConfigType.CUSTOM_LICENSE_TEXTS)
    ),

    EVALUATOR_RULES(
        defaultFilename = ORT_EVALUATOR_RULES_FILENAME,
        providedTypes = setOf(OrtConfigType.EVALUATOR_RULES)
    ),

    HOW_TO_FIX_TEXT_PROVIDER(
        defaultFilename = ORT_HOW_TO_FIX_TEXT_PROVIDER_FILENAME,
        providedTypes = setOf(OrtConfigType.HOW_TO_FIX_TEXTS)
    ),

    LICENSE_CLASSIFICATIONS(
        defaultFilename = ORT_LICENSE_CLASSIFICATIONS_FILENAME,
        providedTypes = setOf(OrtConfigType.LICENSE_CLASSIFICATIONS)
    ),

    NOTIFIER_SCRIPT(
        defaultFilename = ORT_NOTIFIER_SCRIPT_FILENAME,
        providedTypes = setOf(OrtConfigType.NOTIFIER_RULES)
    ),

    ORT_CONFIG(
        defaultFilename = ORT_CONFIG_FILENAME,
        providedTypes = setOf(OrtConfigType.ORT_CONFIG)
    ),

    PACKAGE_CONFIGURATIONS(
        defaultFilename = ORT_PACKAGE_CONFIGURATION_FILENAME,
        defaultDirectoryName = ORT_PACKAGE_CONFIGURATIONS_DIRNAME,
        providedTypes = setOf(OrtConfigType.PACKAGE_CONFIGURATIONS)
    ),

    PACKAGE_CURATIONS(
        defaultFilename = ORT_PACKAGE_CURATIONS_FILENAME,
        defaultDirectoryName = ORT_PACKAGE_CURATIONS_DIRNAME,
        providedTypes = setOf(OrtConfigType.PACKAGE_CURATIONS)
    ),

    REPOSITORY_CONFIGURATION(
        defaultFilename = ORT_REPO_CONFIG_FILENAME,
        providedTypes = setOf(
            OrtConfigType.LICENSE_CHOICES,
            OrtConfigType.ORT_CONFIG,
            OrtConfigType.PACKAGE_CONFIGURATIONS,
            OrtConfigType.PACKAGE_CURATIONS,
            OrtConfigType.RESOLUTIONS
        )
    ),

    RESOLUTIONS(
        defaultFilename = ORT_RESOLUTIONS_FILENAME,
        providedTypes = setOf(OrtConfigType.RESOLUTIONS)
    )
}
