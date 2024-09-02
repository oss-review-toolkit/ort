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
    applicationName = "orth"
    mainClass = "org.ossreviewtoolkit.helper.HelperMainKt"
}

dependencies {
    implementation(projects.analyzer)
    implementation(projects.downloader)

    // There are commands with a hard-coded compile-time dependency on these plugins.
    implementation(projects.plugins.packageConfigurationProviders.dirPackageConfigurationProvider)
    implementation(projects.plugins.packageCurationProviders.filePackageCurationProvider)

    implementation(projects.scanner)
    implementation(projects.utils.configUtils)
    implementation(projects.utils.ortUtils)

    implementation(libs.clikt)
    implementation(libs.commonsCompress)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jslt)
    implementation(libs.log4j.api)
    implementation(libs.slf4j)

    "pluginClasspath"(platform(projects.plugins.versionControlSystems))
}
