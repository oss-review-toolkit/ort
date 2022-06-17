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
    implementation(project(":utils:ort-utils"))

    implementation(libs.bundles.exposed)
    implementation(libs.hikari)
    implementation(libs.jacksonModuleKotlin)
    implementation(libs.kotlinxCoroutines)
    implementation(libs.postgres)
    implementation(libs.retrofitConverterJackson)
    implementation(libs.scanoss)
    implementation(libs.sw360Client)

    testImplementation(libs.kotlinxSerialization)
    testImplementation(libs.mockk)
    testImplementation(libs.retrofitConverterKotlinxSerialization)
    testImplementation(libs.wiremock)
}

buildConfig {
    packageName("org.ossreviewtoolkit.scanner")

    buildConfigField("String", "ASKALONO_VERSION", "\"$askalonoVersion\"")
    buildConfigField("String", "BOYTER_LC_VERSION", "\"$boyterLcVersion\"")
    buildConfigField("String", "LICENSEE_VERSION", "\"$licenseeVersion\"")
    buildConfigField("String", "SCANCODE_VERSION", "\"$scancodeVersion\"")
    buildConfigField("String", "SCANOSS_VERSION", "\"${libs.versions.scanoss.get()}\"")
}

tasks.withType<KotlinCompile>().configureEach {
    val customCompilerArgs = listOf(
        "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
    )

    if ("test" in name.toLowerCase()) {
        kotlinOptions {
            freeCompilerArgs = freeCompilerArgs + customCompilerArgs
        }
    }
}
