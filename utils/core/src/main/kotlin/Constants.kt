/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2020 Bosch.IO GmbH
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

package org.ossreviewtoolkit.utils.core

/**
 * The (short) name of the OSS Review Toolkit.
 */
const val ORT_NAME = "ort"

/**
 * The full name of the OSS Review Toolkit.
 */
const val ORT_FULL_NAME = "OSS Review Toolkit"

/**
 * The name of the environment variable to customize the ORT config directory.
 */
const val ORT_CONFIG_DIR_ENV_NAME = "ORT_CONFIG_DIR"

/**
 * The name of the environment variable to customize the ORT tools directory.
 */
const val ORT_TOOLS_DIR_ENV_NAME = "ORT_TOOLS_DIR"

/**
 * The name of the environment variable to customize the ORT data directory.
 */
const val ORT_DATA_DIR_ENV_NAME = "ORT_DATA_DIR"

/**
 * The name of the ORT (main) configuration file.
 */
const val ORT_CONFIG_FILENAME = "ort.conf"

/**
 * The name of the ORT copyright garbage configuration file.
 */
const val ORT_COPYRIGHT_GARBAGE_FILENAME = "copyright-garbage.yml"

/**
 * The name of the ORT custom license texts configuration directory.
 */
const val ORT_CUSTOM_LICENSE_TEXTS_DIRNAME = "custom-license-texts"

/**
 * The name of the ORT package curations directory.
 */
const val ORT_PACKAGE_CURATIONS_DIRNAME = "curations"

/**
 * The name of the ORT package curations configuration file.
 */
const val ORT_PACKAGE_CURATIONS_FILENAME = "curations.yml"

/**
 * The name of the ORT how to fix text provider script file.
 */
const val ORT_HOW_TO_FIX_TEXT_PROVIDER_FILENAME = "how-to-fix-text-provider.kts"

/**
 * The name of the ORT license classifications file.
 */
const val ORT_LICENSE_CLASSIFICATIONS_FILENAME = "license-classifications.yml"

/**
 * The name of the ORT package configurations directory.
 */
const val ORT_PACKAGE_CONFIGURATIONS_DIRNAME = "package-configurations"

/**
 * The name of the ORT repository configuration file.
 */
const val ORT_REPO_CONFIG_FILENAME = ".ort.yml"

/**
 * The name of the ORT resolutions configuration file.
 */
const val ORT_RESOLUTIONS_FILENAME = "resolutions.yml"

/**
 * The name of the ORT evaluator rules script.
 */
const val ORT_EVALUATOR_RULES_FILENAME = "evaluator.rules.kts"

/**
 * The name of the ORT notifier script.
 */
const val ORT_NOTIFIER_SCRIPT_FILENAME = "notifier.notifications.kts"
