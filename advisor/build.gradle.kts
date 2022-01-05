/*
 * Copyright (C) 2020 Bosch.IO GmbH
 * Copyright (C) 2021 Sonatype, Inc.
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

val kotlinxCoroutinesVersion: String by project
val mockkVersion: String by project
val wiremockVersion: String by project

plugins {
    // Apply core plugins.
    `java-library`
}

dependencies {
    api(project(":clients:nexus-iq"))
    api(project(":clients:oss-index"))
    api(project(":clients:vulnerable-code"))
    api(project(":clients:github-graphql"))
    api(project(":model"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")

    testImplementation("com.github.tomakehurst:wiremock-jre8:$wiremockVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
}
