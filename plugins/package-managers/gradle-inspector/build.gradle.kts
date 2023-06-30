/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import org.jetbrains.gradle.ext.settings
import org.jetbrains.gradle.ext.taskTriggers

plugins {
    // Apply precompiled plugins.
    id("ort-library-conventions")

    // Apply third-party plugins.
    alias(libs.plugins.ideaExt)
}

dependencies {
    api(project(":analyzer"))
    api(project(":model"))

    implementation(project(":downloader"))
    implementation(project(":plugins:package-managers:gradle-model"))
    implementation(project(":utils:common-utils"))
    implementation(project(":utils:ort-utils"))
    implementation(project(":utils:spdx-utils"))

    // Use the latest version that is not affected by https://github.com/gradle/gradle/issues/23208.
    implementation("org.gradle:gradle-tooling-api:7.6.2")

    implementation(libs.log4jApi)
    implementation(libs.log4jApiKotlin)

    funTestImplementation(testFixtures(project(":analyzer")))
}

val processResources = tasks.named<Copy>("processResources") {
    val gradlePluginProject = project(":plugins:package-managers:gradle-plugin")
    val gradlePluginJarTask = gradlePluginProject.tasks.named<Jar>("fatJar")
    val gradlePluginJarFile = gradlePluginJarTask.get().outputs.files.singleFile

    // As the Copy-task simply skips non-existing files, add explicit dependencies on the Jar-tasks.
    dependsOn(gradlePluginJarTask)

    // Bundle the plugin JAR as a resource so the inspector can copy it at runtime to the init script's classpath.
    from(gradlePluginJarFile)

    // Ensure a constant file name without a version suffix.
    rename(gradlePluginJarFile.name, "gradle-plugin.jar")
}

// Work around https://youtrack.jetbrains.com/issue/IDEA-173367.
rootProject.idea.project.settings.taskTriggers.beforeBuild(processResources)

tasks.named<JacocoReport>("jacocoFunTestReport") {
    classDirectories.setFrom(
        files(
            classDirectories.files.map {
                fileTree(it) {
                    // Avoid JaCoCo to look at the JAR resource.
                    exclude("**/gradle-plugin.jar/**")
                }
            }
        )
    )
}

tasks.named<Test>("funTest") {
    val gradlePackageManagerProject = project(":plugins:package-managers:gradle-package-manager")
    val gradlePackageManagerFunTestTask = gradlePackageManagerProject.tasks.named<Test>("funTest")

    // Enforce ordering to avoid conflicts e.g. during Android SDK component installation.
    mustRunAfter(gradlePackageManagerFunTestTask)
}
