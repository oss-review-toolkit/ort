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

import org.gradle.kotlin.dsl.support.uppercaseFirstChar

plugins {
    // Apply core plugins.
    `version-catalog`

    // Apply precompiled plugins.
    id("ort-base-conventions")
    id("ort-publication-conventions")
}

gradle.projectsEvaluated {
    catalog {
        versionCatalog {
            val hyphenRegex = Regex("([a-z])-([a-z])")
            val reservedPrefixes = setOf("bundles", "plugins", "versions")

            rootProject.subprojects.filter { subproject ->
                subproject.pluginManager.hasPlugin("ort-publication-conventions")
            }.forEach { subproject ->
                val alias = subproject.projectDir.toRelativeString(rootDir)
                    .replace(hyphenRegex) {
                        "${it.groupValues[1]}${it.groupValues[2].uppercase()}"
                    }
                    .replace(File.separatorChar, '-')

                val safeAlias = if (reservedPrefixes.any { alias.startsWith("$it-") }) {
                    // Add a prefix to work around reserved words e.g. for ORT's own plugins.
                    "ort${alias.uppercaseFirstChar()}"
                } else {
                    alias
                }

                with(subproject) {
                    library(safeAlias, "$group:$name:$version")
                }
            }

            bundle(
                "scriptDefinitions",
                listOf(
                    "evaluator", // Contains the script definition for "*.rules.kts" files.
                    "notifier",  // Contains the script definition for "*.notifications.kts" files.
                    "reporter"   // Contains the script definition for "*.how-to-fix-text-provider.kts" files.
                )
            )
        }
    }
}
