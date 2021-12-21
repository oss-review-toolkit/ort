/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

val commonsCompressVersion: String by project
val disklrucacheVersion: String by project
val log4jApiKotlinVersion: String by project
val jacksonVersion: String by project
val semverVersion: String by project
val springCoreVersion: String by project

plugins {
    // Apply core plugins.
    `java-library`
}

dependencies {
    api("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    api("com.vdurmont:semver4j:$semverVersion")
    api("org.apache.logging.log4j:log4j-api-kotlin:$log4jApiKotlinVersion")

    implementation("com.jakewharton:disklrucache:$disklrucacheVersion")
    implementation("org.apache.commons:commons-compress:$commonsCompressVersion")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jsr223")
    implementation("org.springframework:spring-core:$springCoreVersion")
}
