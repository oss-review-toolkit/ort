/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

val antennaVersion: String by project
val hikariVersion: String by project
val jacksonVersion: String by project
val kotlinxCoroutinesVersion: String by project
val mockkVersion: String by project
val postgresVersion: String by project
val postgresEmbeddedVersion: String by project
val wiremockVersion: String by project

plugins {
    // Apply core plugins.
    `java-library`
}

repositories {
    exclusiveContent {
        forRepository {
            maven("https://download.eclipse.org/antenna/releases/")
        }

        filter {
            includeGroup("org.eclipse.sw360.antenna")
        }
    }
}

dependencies {
    api(project(":model"))

    implementation(project(":clients:clearly-defined"))
    implementation(project(":downloader"))
    implementation(project(":utils"))

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.eclipse.sw360.antenna:sw360-client:$antennaVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")

    testImplementation("com.github.tomakehurst:wiremock:$wiremockVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")

    funTestImplementation("com.opentable.components:otj-pg-embedded:$postgresEmbeddedVersion")
}
