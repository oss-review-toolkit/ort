/*
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

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kotlinxSerializationVersion: String by project
val retrofitVersion: String by project
val retrofitKotlinxSerializationConverterVersion: String by project

plugins {
    // Apply core plugins.
    `java-library`

    // Apply third-party plugins.
    kotlin("plugin.serialization")
}

dependencies {
    api("com.squareup.retrofit2:retrofit:$retrofitVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
    implementation(
        "com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:" +
                retrofitKotlinxSerializationConverterVersion
    )
}

tasks.withType<KotlinCompile> {
    val customCompilerArgs = listOf(
        "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
    )

    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + customCompilerArgs
    }
}
