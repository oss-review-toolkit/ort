/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

pluginManagement {
    resolutionStrategy {
        eachPlugin {
            // Work around https://github.com/gradle/gradle/issues/1697.
            if (requested.id.namespace != "org.gradle" && requested.version == null) {
                val versionPropertyName = if (requested.id.id.startsWith("org.jetbrains.kotlin.")) {
                    "kotlinPluginVersion"
                } else {
                    val pluginName = requested.id.name.split('-').joinToString("") { it.capitalize() }.decapitalize()
                    "${pluginName}PluginVersion"
                }

                logger.info("Checking for plugin version property '$versionPropertyName'.")

                gradle.rootProject.properties[versionPropertyName]?.let { version ->
                    logger.info("Setting '${requested.id.id}' plugin version to $version.")
                    useVersion(version.toString())
                } ?: logger.warn("No version specified for plugin '${requested.id.id}' and property " +
                        "'$versionPropertyName' does not exist.")
            }
        }
    }
}

rootProject.name = "oss-review-toolkit"

include(":advisor")
include(":analyzer")
include(":cli")
include(":clients:clearly-defined")
include(":clients:fossid-webapp")
include(":clients:nexus-iq")
include(":clients:vulnerable-code")
include(":detekt-rules")
include(":downloader")
include(":evaluator")
include(":helper-cli")
include(":model")
include(":reporter")
include(":reporter-web-app")
include(":scanner")
include(":spdx-utils")
include(":test-utils")
include(":utils")

val buildCacheRetentionDays: String by settings

buildCache {
    local {
        removeUnusedEntriesAfterDays = buildCacheRetentionDays.toInt()
    }
}
