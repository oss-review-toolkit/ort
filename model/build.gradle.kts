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
    api(projects.clients.clearlyDefinedClient)
    api(projects.plugins.api)
    api(projects.utils.ortUtils)
    api(projects.utils.spdxUtils)

    api(jacksonLibs.jacksonDatabind)
    api(jacksonLibs.jacksonDataformatYaml)
    api(libs.log4j.api)

    implementation(jacksonLibs.jacksonDatatypeJsr310)
    implementation(jacksonLibs.jacksonModuleKotlin)
    implementation(libs.bundles.exposed)
    implementation(libs.bundles.hoplite)
    implementation(libs.hikari)
    implementation(libs.postgres)
    implementation(libs.semver4j)
    implementation(libs.tika)

    testFixturesImplementation(projects.utils.testUtils)

    testImplementation(libs.jsonSchemaValidator)
    testImplementation(libs.mockk)
}
