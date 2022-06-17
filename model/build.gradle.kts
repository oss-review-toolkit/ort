/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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
    `java-library`
}

dependencies {
    api(project(":clients:clearly-defined"))
    api(project(":utils:ort-utils"))
    api(project(":utils:spdx-utils"))

    api(libs.jacksonDatabind)
    api(libs.jacksonDataformatXml)
    api(libs.jacksonDataformatYaml)

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(libs.bundles.exposed)
    implementation(libs.bundles.hoplite)
    implementation(libs.hikari)
    implementation(libs.jacksonDatatypeJsr310)
    implementation(libs.jacksonModuleJaxbAnnotations)
    implementation(libs.jacksonModuleKotlin)
    implementation(libs.postgres)
    implementation(libs.semver4j)

    testImplementation(libs.jsonSchemaValidator)
    testImplementation(libs.mockk)
}
