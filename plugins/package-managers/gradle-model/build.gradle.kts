/*
 * Copyright (C) 2023 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

plugins {
    // Apply precompiled plugins.
    id("ort-kotlin-conventions")
    id("ort-publication-conventions")
}

tapmoc {
    // Classes that are sent to the build via custom build actions need to target the lowest supported Java version,
    // which is Java 8 for Gradle 5 and above, see
    // https://docs.gradle.org/current/userguide/tooling_api.html#sec:embedding_compatibility
    java(8)

    // This is the lowest version supported by the current Kotlin plugin.
    kotlin("1.9.0")
}

tasks.named<KotlinCompile>("compileKotlin") {
    compilerOptions {
        // See https://docs.gradle.org/current/userguide/compatibility.html#kotlin.
        freeCompilerArgs.add("-Xsuppress-version-warnings")
    }
}
