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
    `java-library`
    `java-test-fixtures`
}

dependencies {
    api(project(":model"))

    implementation(project(":downloader"))
    implementation(project(":utils:ort-utils"))
    implementation(project(":utils:spdx-utils"))

    implementation(libs.mavenCore)

    // TODO: Remove this once https://issues.apache.org/jira/browse/MNG-6561 is resolved.
    implementation(libs.mavenCompat)

    // The classes from the maven-resolver dependencies are not used directly but initialized by the Plexus IoC
    // container automatically. They are required on the classpath for Maven dependency resolution to work.
    implementation(libs.bundles.mavenResolver)

    implementation(libs.jacksonModuleKotlin)
    implementation(libs.kotlinxCoroutines)
    implementation(libs.semver4j)

    implementation(libs.toml4j)
    constraints {
        implementation("com.google.code.gson:gson:2.10.1") {
            because("Earlier versions have vulnerabilities.")
        }
    }

    // Only the Java plugin's built-in "test" source set automatically depends on the test fixtures.
    funTestImplementation(testFixtures(project(":analyzer")))

    testImplementation(libs.mockk)
    testImplementation(libs.wiremock)

    testFixturesImplementation(project(":utils:test-utils"))

    testFixturesImplementation(libs.kotestAssertionsCore)
    testFixturesImplementation(libs.kotestRunnerJunit5)
}
