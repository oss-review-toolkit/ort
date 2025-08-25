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
    id("ort-plugin-conventions")

    // Apply third-party plugins.
    alias(libs.plugins.ideaExt)
}

dependencies {
    api(projects.analyzer)
    api(projects.model)

    implementation(projects.downloader)
    implementation(projects.plugins.packageManagers.gradleModel)
    implementation(projects.utils.commonUtils)
    implementation(projects.utils.ortUtils)
    implementation(projects.utils.spdxUtils)

    implementation("org.gradle:gradle-tooling-api:${gradle.gradleVersion}")

    ksp(projects.analyzer)

    funTestImplementation(projects.plugins.versionControlSystems.gitVersionControlSystem)
    funTestImplementation(testFixtures(projects.analyzer))

    funTestImplementation(libs.kotest.assertions.table)
}

val processResources = tasks.named<Copy>("processResources") {
    val gradlePluginProject = project.project(projects.plugins.packageManagers.gradlePlugin.path)
    val gradlePluginJarTask = gradlePluginProject.tasks.named<Jar>("fatJar")
    val gradlePluginJarFile = gradlePluginJarTask.get().outputs.files.singleFile

    // As the Copy-task simply skips non-existing files, add explicit dependencies on the Jar-tasks.
    dependsOn(gradlePluginJarTask)

    // Bundle the plugin JAR as a resource so the inspector can copy it at runtime to the init script's classpath.
    from(gradlePluginJarFile)

    // Ensure a constant file name without a version suffix.
    rename(Regex.escape(gradlePluginJarFile.name), "gradle-plugin.jar")
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
    val gradlePackageManagerProject = project.project(projects.plugins.packageManagers.gradlePackageManager.path)
    val gradlePackageManagerFunTestTask = gradlePackageManagerProject.tasks.named<Test>("funTest")

    // Enforce ordering to avoid conflicts e.g. during Android SDK component installation.
    mustRunAfter(gradlePackageManagerFunTestTask)
}
