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
    // Apply core plugins.
    `java-gradle-plugin`

    // Apply precompiled plugins.
    id("ort-kotlin-conventions")
}

gradlePlugin {
    plugins.register("OrtModelPlugin") {
        id = "org.ossreviewtoolkit.plugins.packagemanagers.gradle.plugin"
        implementationClass = "org.ossreviewtoolkit.plugins.packagemanagers.gradleplugin.OrtModelPlugin"
    }
}

tapmoc {
    // Classes that are sent to the build via custom build actions need to target the lowest supported Java version,
    // which is Java 8 for Gradle 5 and above, see
    // https://docs.gradle.org/current/userguide/tooling_api.html#sec:embedding_compatibility
    java(8)

    // This is the lowest version supported by the current Kotlin plugin.
    kotlin("1.9.0")
}

dependencies {
    api(projects.plugins.packageManagers.gradleModel)

    api(libs.maven.model)
    api(libs.maven.model.builder)
}

tasks.named<KotlinCompile>("compileKotlin") {
    compilerOptions {
        // See https://docs.gradle.org/current/userguide/compatibility.html#kotlin.
        freeCompilerArgs.add("-Xsuppress-version-warnings")
    }
}

tasks.register<Jar>("fatJar") {
    description = "Creates a fat JAR that includes all required runtime dependencies."
    group = "Build"

    archiveClassifier = "fat"

    // Handle duplicate `META-INF/DEPENDENCIES` files.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)

    from({
        configurations.runtimeClasspath.get().filter {
            check(it.extension == "jar")

            val isGradleDependency = "gradle" in it.path && "-${gradle.gradleVersion}" in it.path
            if (isGradleDependency) logger.lifecycle("Filtering out '$it' from '${archiveFile.get().asFile.name}'.")

            // Omit JARs for Gradle dependencies from the fat JAR.
            !isGradleDependency
        }.map {
            zipTree(it)
        }
    })
}
