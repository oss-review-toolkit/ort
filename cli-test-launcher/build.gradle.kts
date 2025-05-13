/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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
    id("ort-application-conventions")
}

application {
    applicationName = "ort-test-launcher"
    mainClass = "io.kotest.engine.launcher.MainKt"
}

val Project.hasFunTests
    // Do not dig into sourceSets to avoid coupling between projects.
    get() = projectDir.resolve("src/funTest").isDirectory

dependencies {
    rootProject.subprojects.filter { it.hasFunTests }.forEach {
        implementation(project(it.path)) {
            capabilities {
                // Note that this uses kebab-case although "registerFeature()" uses camelCase, see
                // https://github.com/gradle/gradle/issues/31362.
                @Suppress("UnstableApiUsage")
                requireFeature("fun-test")
            }
        }
    }
}

tasks.named<Sync>("installDist") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
