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

plugins {
    // Apply core plugins.
    `java-test-fixtures`

    // Apply precompiled plugins.
    id("ort-library-conventions")
}

dependencies {
    api(projects.model)

    implementation(projects.clients.clearlyDefinedClient)
    implementation(projects.downloader)
    implementation(projects.utils.ortUtils)

    implementation(libs.bundles.exposed)
    implementation(libs.hikari)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.postgres)
    implementation(libs.retrofit.converter.jackson)
    implementation(libs.sw360Client)

    funTestApi(testFixtures(projects.scanner))

    funTestImplementation(platform(projects.plugins.scanners))
    funTestImplementation(platform(projects.plugins.versionControlSystems))

    testImplementation(platform(projects.plugins.scanners))

    testImplementation(libs.kotlinx.serialization.core)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.mockk)
    testImplementation(libs.retrofit.converter.kotlinxSerialization)
    testImplementation(libs.wiremock)

    testFixturesImplementation(libs.kotest.assertions.core)
    testFixturesImplementation(libs.kotest.runner.junit5)
}
