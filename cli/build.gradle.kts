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

val cliAnalyzerOnly: String? by project

plugins {
    // Apply precompiled plugins.
    id("ort-application-conventions")
}

configurations.dependencyScope("pluginClasspath")
configurations["runtimeOnly"].extendsFrom(configurations["pluginClasspath"])

application {
    applicationName = "ort"
    mainClass = "org.ossreviewtoolkit.cli.OrtMainKt"
}

dependencies {
    implementation(projects.model)
    implementation(projects.plugins.commands.commandApi)
    implementation(projects.utils.commonUtils)
    implementation(projects.utils.ortUtils)

    implementation(libs.clikt)
    implementation(libs.mordant)
    implementation(libs.slf4j)

    if (cliAnalyzerOnly.toBoolean()) {
        "pluginClasspath"(projects.plugins.commands.analyzerCommand)
        "pluginClasspath"(platform(projects.plugins.packageCurationProviders))
        "pluginClasspath"(platform(projects.plugins.packageManagers))
    } else {
        "pluginClasspath"(platform(projects.plugins.advisors))
        "pluginClasspath"(platform(projects.plugins.commands))
        "pluginClasspath"(platform(projects.plugins.licenseFactProviders))
        "pluginClasspath"(platform(projects.plugins.packageConfigurationProviders))
        "pluginClasspath"(platform(projects.plugins.packageCurationProviders))
        "pluginClasspath"(platform(projects.plugins.packageManagers))
        "pluginClasspath"(platform(projects.plugins.reporters))
        "pluginClasspath"(platform(projects.plugins.scanners))
        "pluginClasspath"(platform(projects.plugins.versionControlSystems))
    }

    funTestImplementation(platform(projects.plugins.commands))
    funTestImplementation(platform(projects.plugins.packageCurationProviders))
    funTestImplementation(platform(projects.plugins.packageManagers))
    funTestImplementation(platform(projects.plugins.reporters))
    funTestImplementation(projects.downloader)
    funTestImplementation(projects.evaluator)
    funTestImplementation(projects.notifier)
    funTestImplementation(projects.reporter)
    funTestImplementation(testFixtures(projects.analyzer))

    funTestImplementation(libs.greenmail)
}
