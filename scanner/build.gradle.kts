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

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val exposedVersion: String by project
val hikariVersion: String by project
val jacksonVersion: String by project
val kotlinxCoroutinesVersion: String by project
val kotlinxSerializationVersion: String by project
val mockkVersion: String by project
val postgresVersion: String by project
val retrofitKotlinxSerializationConverterVersion: String by project
val retrofitVersion: String by project
val scanossVersion: String by project
val sw360ClientVersion: String by project
val wiremockVersion: String by project

val askalonoVersion: String by project
val boyterLcVersion: String by project
val licenseeVersion: String by project
val scancodeVersion: String by project

plugins {
    // Apply core plugins.
    `java-library`
}

repositories {
    exclusiveContent {
        forRepository {
            maven("https://repo.eclipse.org/content/repositories/sw360-releases/")
        }

        filter {
            includeGroup("org.eclipse.sw360")
        }
    }
}

dependencies {
    api(project(":model"))

    implementation(project(":clients:clearly-defined"))
    implementation(project(":clients:fossid-webapp"))
    implementation(project(":clients:scanoss"))
    implementation(project(":downloader"))
    implementation(project(":utils:core-utils"))

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.scanoss:scanner:$scanossVersion")
    implementation("com.squareup.retrofit2:converter-jackson:$retrofitVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.eclipse.sw360:client:$sw360ClientVersion")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")

    testImplementation("com.github.tomakehurst:wiremock-jre8:$wiremockVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
    testImplementation(
        "com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:" +
                retrofitKotlinxSerializationConverterVersion
    )
}

buildConfig {
    packageName("org.ossreviewtoolkit.scanner")

    buildConfigField("String", "ASKALONO_VERSION", "\"$askalonoVersion\"")
    buildConfigField("String", "BOYTER_LC_VERSION", "\"$boyterLcVersion\"")
    buildConfigField("String", "LICENSEE_VERSION", "\"$licenseeVersion\"")
    buildConfigField("String", "SCANCODE_VERSION", "\"$scancodeVersion\"")
    buildConfigField("String", "SCANOSS_VERSION", "\"$scanossVersion\"")
}

tasks.withType<KotlinCompile> {
    val customCompilerArgs = listOf(
        "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
    )

    if ("test" in name.toLowerCase()) {
        kotlinOptions {
            freeCompilerArgs = freeCompilerArgs + customCompilerArgs
        }
    }
}
