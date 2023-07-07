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

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Apply core plugins.
    `java-test-fixtures`

    // Apply precompiled plugins.
    id("ort-library-conventions")
}

dependencies {
    api(project(":model"))

    implementation(project(":clients:clearly-defined-client"))
    implementation(project(":downloader"))
    implementation(project(":utils:ort-utils"))

    implementation(libs.bundles.exposed)
    implementation(libs.hikari)
    implementation(libs.jacksonModuleKotlin)
    implementation(libs.kotlinxCoroutines)
    implementation(libs.postgres)
    implementation(libs.retrofitConverterJackson)
    implementation(libs.sw360Client)

    funTestApi(testFixtures(project(":scanner")))

    funTestImplementation(platform(project(":plugins:scanners")))

    testImplementation(platform(project(":plugins:scanners")))

    testImplementation(libs.bundles.kotlinxSerialization)
    testImplementation(libs.mockk)
    testImplementation(libs.retrofitConverterKotlinxSerialization)
    testImplementation(libs.wiremock)

    testFixturesImplementation(libs.kotestAssertionsCore)
    testFixturesImplementation(libs.kotestRunnerJunit5)
}

tasks.named<KotlinCompile>("compileTestKotlin") {
    val customCompilerArgs = listOf(
        "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
    )

    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + customCompilerArgs
    }
}
