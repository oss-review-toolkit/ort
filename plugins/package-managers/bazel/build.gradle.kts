/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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
    // Apply precompiled plugins.
    id("ort-plugin-conventions")

    // Apply third-party plugins.
    alias(libs.plugins.kotlinSerialization)
}

dependencies {
    api(projects.analyzer)
    api(projects.model)
    api(projects.utils.commonUtils) {
        because("This is a CommandLineTool.")
    }

    api(libs.semver4j) {
        because("This is a CommandLineTool.")
    }

    implementation(projects.downloader)
    implementation(projects.clients.bazelModuleRegistryClient)
    implementation(projects.utils.ortUtils)
    implementation(projects.utils.spdxUtils)

    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)

    ksp(projects.analyzer)

    testImplementation(libs.mockk)

    funTestImplementation(testFixtures(projects.analyzer))
    funTestImplementation(projects.plugins.packageManagers.conanPackageManager)
}

tasks.named<Test>("funTest") {
    val conanPackageManagerProject = project.project(projects.plugins.packageManagers.conanPackageManager.path)
    val conanPackageManagerFunTestTask = conanPackageManagerProject.tasks.named<Test>("funTest")

    // Prevent conflicts with the Conan configuration database by running after the Conan package manager tests.
    mustRunAfter(conanPackageManagerFunTestTask)
}
