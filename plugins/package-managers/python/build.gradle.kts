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

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Apply precompiled plugins.
    id("ort-library-conventions")

    // Apply third-party plugins.
    alias(libs.plugins.kotlinSerialization)
}

dependencies {
    api(project(":analyzer"))
    api(project(":model"))
    api(project(":utils:common-utils")) {
        because("This is a CommandLineTool.")
    }

    api(libs.log4jApiKotlin)
    api(libs.semver4j) {
        because("This is a CommandLineTool.")
    }

    implementation(project(":downloader"))
    implementation(project(":utils:ort-utils"))
    implementation(project(":utils:spdx-utils"))

    implementation(libs.bundles.kotlinxSerialization)

    funTestImplementation(testFixtures(project(":analyzer")))
}

tasks.withType<KotlinCompile>().configureEach {
    val customCompilerArgs = listOf(
        "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
    )

    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + customCompilerArgs
    }
}
