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

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Apply core plugins.
    `java-library`

    // Apply third-party plugins.
    alias(libs.plugins.kotlinSerialization)
}

repositories {
    exclusiveContent {
        forRepository {
            maven("https://repo.gradle.org/gradle/libs-releases/")
        }

        filter {
            includeGroup("org.gradle")
        }
    }

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
    api(project(":clients:clearly-defined"))
    api(project(":model"))

    implementation(project(":downloader"))
    implementation(project(":utils:ort-utils"))
    implementation(project(":utils:spdx-utils"))

    implementation("org.gradle:gradle-tooling-api:${gradle.gradleVersion}")
    implementation(libs.bundles.maven)

    // The classes from the maven-resolver dependencies are not used directly but initialized by the Plexus IoC
    // container automatically. They are required on the classpath for Maven dependency resolution to work.
    implementation(libs.bundles.mavenResolver)

    implementation(libs.jacksonModuleJaxbAnnotations)
    implementation(libs.jacksonModuleKotlin)
    implementation(libs.jruby)
    implementation(libs.kotlinxCoroutines)
    implementation(libs.kotlinxSerialization)
    implementation(libs.semver4j)
    implementation(libs.sw360Client)
    implementation(libs.toml4j)

    testImplementation(libs.mockk)
    testImplementation(libs.wiremock)
}

tasks.withType<KotlinCompile>().configureEach {
    val customCompilerArgs = listOf(
        "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
    )

    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + customCompilerArgs
    }
}
