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

plugins {
    // Apply precompiled plugins.
    id("ort-plugin-conventions")

    // Apply third-party plugins.
    alias(libs.plugins.kotlinSerialization)
}

dependencies {
    api(projects.analyzer)
    api(projects.model)

    api(libs.maven.core)
    api(libs.maven.resolver.api)

    implementation(projects.downloader)
    implementation(projects.utils.commonUtils)

    implementation(libs.bouncyCastle)
    implementation(libs.maven.embedder)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)

    ksp(projects.analyzer)

    // The classes from the maven-resolver dependencies are not used directly but initialized by the Plexus IoC
    // container automatically. They are required on the classpath for Maven dependency resolution to work.
    runtimeOnly(libs.bundles.mavenResolver)

    // Under certain circumstances, Tycho uses Wagon to download metadata for SNAPSHOT artifacts. Therefore, at
    // least the wagon-http dependency should be available on the classpath.
    runtimeOnly(libs.wagon.http)

    // TODO: Remove this once https://issues.apache.org/jira/browse/MNG-6561 is resolved.
    runtimeOnly(libs.maven.compat)

    funTestImplementation(testFixtures(projects.analyzer))

    testImplementation(libs.mockk)
    testImplementation(libs.wiremock)
}
