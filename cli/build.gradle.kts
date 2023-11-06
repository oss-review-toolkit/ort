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

plugins {
    // Apply precompiled plugins.
    id("ort-application-conventions")
}

configurations.dependencyScope("pluginClasspath")
configurations["runtimeClasspath"].extendsFrom(configurations["pluginClasspath"])

application {
    applicationName = "ort"
    mainClass = "org.ossreviewtoolkit.cli.OrtMainKt"
}

dependencies {
    implementation(project(":model"))
    implementation(project(":plugins:commands:command-api"))
    implementation(project(":utils:common-utils"))
    implementation(project(":utils:ort-utils"))

    implementation(libs.clikt)
    implementation(libs.mordant)
    implementation(libs.slf4j)

    "pluginClasspath"(platform(project(":plugins:advisors")))
    "pluginClasspath"(platform(project(":plugins:commands")))
    "pluginClasspath"(platform(project(":plugins:package-configuration-providers")))
    "pluginClasspath"(platform(project(":plugins:package-curation-providers")))
    "pluginClasspath"(platform(project(":plugins:package-managers")))
    "pluginClasspath"(platform(project(":plugins:reporters")))
    "pluginClasspath"(platform(project(":plugins:scanners")))
    "pluginClasspath"(platform(project(":plugins:version-control-systems")))

    funTestImplementation(platform(project(":plugins:commands")))
    funTestImplementation(platform(project(":plugins:package-managers")))
    funTestImplementation(platform(project(":plugins:reporters")))
    funTestImplementation(project(":downloader"))
    funTestImplementation(project(":evaluator"))
    funTestImplementation(project(":notifier"))
    funTestImplementation(project(":reporter"))
    funTestImplementation(testFixtures(project(":analyzer")))

    funTestImplementation(libs.greenmail)
}
