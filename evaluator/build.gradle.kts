/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import de.undercouch.gradle.tasks.download.Download

plugins {
    // Apply precompiled plugins.
    id("ort-library-conventions")

    // Apply third-party plugins.
    alias(libs.plugins.download)
    alias(libs.plugins.kotlinSerialization)
}

dependencies {
    api(projects.model)
    api(projects.utils.scriptingUtils)

    implementation(projects.downloader)
    implementation(projects.utils.ortUtils)
    implementation(projects.utils.spdxUtils)

    implementation("org.jetbrains.kotlin:kotlin-scripting-common")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host")
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.mockk)
}

tasks.register<Download>("updateOsadlMatrix") {
    description = "Download the OSADL matrix in JSON format and add it as a resource."
    group = "OSADL"

    src("https://www.osadl.org/fileadmin/checklists/matrixseqexpl.json")
    dest("src/main/resources/rules/matrixseqexpl.json")
}
