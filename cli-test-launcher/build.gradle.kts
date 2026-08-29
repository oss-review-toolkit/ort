/*
 * Copyright (C) 2025 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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
    mainClass = "org.ossreviewtoolkit.clitestlauncher.TestMainKt"
}

val Project.hasFunTests
    // Registering a feature automatically creates several configurations, see
    // https://docs.gradle.org/current/userguide/how_to_create_feature_variants_of_a_library.html#sec::declare_feature_variants
    get() = configurations.findByName("funTestRuntimeElements") != null

gradle.projectsEvaluated {
    dependencies {
        rootProject.subprojects.filter { it.hasFunTests }.forEach {
            runtimeOnly(project(it.path)) {
                capabilities {
                    // Note that this uses kebab-case although "registerFeature()" uses camelCase, see
                    // https://github.com/gradle/gradle/issues/31362.
                    @Suppress("UnstableApiUsage")
                    requireFeature("fun-test")
                }
            }
        }

        implementation(projects.utils.commonUtils)
        implementation(libs.clikt)
        implementation(libs.kotest.framework.engine)
    }
}

tasks.named<Sync>("installDist") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
