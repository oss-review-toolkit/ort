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

            rootProject.subprojects.filter { subproject ->
                subproject.pluginManager.hasPlugin("ort-publication-conventions")
            }.forEach { subproject ->
                val alias = subproject.projectDir.toRelativeString(rootDir)
                    .replace(hyphenRegex) {
                        "${it.groupValues[1]}${it.groupValues[2].uppercase()}"
                    }
                    .replace(File.separatorChar, '-')

                with(subproject) {
                    library("ort-$alias", "$group:$name:$version")
                }
            }
        }
    }
}
