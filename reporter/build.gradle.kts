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
    api(projects.plugins.api)
    api(projects.plugins.licenseFactProviders.licenseFactProviderApi)

    implementation(projects.utils.scriptingUtils)
    implementation(projects.utils.spdxUtils)

    implementation("org.jetbrains.kotlin:kotlin-scripting-common")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host")

    // Only the Java plugin's built-in "test" source set automatically depends on the test fixtures.
    funTestImplementation(testFixtures(project))

    funTestImplementation(libs.kotest.assertions.json)

    testFixturesImplementation(projects.utils.testUtils)
}
