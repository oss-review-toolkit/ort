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

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
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

dependencies {
    api(projects.plugins.packageManagers.gradleModel)

    api(libs.maven.model)
    api(libs.maven.model.builder)
}

// Classes that are sent to the build via custom build actions need to target the lowest supported Java version, which
// is Java 8 for Gradle 5 and above, see
// https://docs.gradle.org/current/userguide/third_party_integration.html#sec:embedding_compatibility
val gradleToolingApiLowestSupportedJavaVersion = JvmTarget.JVM_1_8

tasks.named<JavaCompile>("compileJava") {
    targetCompatibility = gradleToolingApiLowestSupportedJavaVersion.target
}

tasks.named<KotlinCompile>("compileKotlin") {
    compilerOptions {
        jvmTarget = gradleToolingApiLowestSupportedJavaVersion

        // See https://docs.gradle.org/current/userguide/compatibility.html#kotlin.
        freeCompilerArgs = listOf("-Xsuppress-version-warnings")
        languageVersion = @Suppress("DEPRECATION") KotlinVersion.KOTLIN_1_8
        apiVersion = @Suppress("DEPRECATION") KotlinVersion.KOTLIN_1_8
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
            // Only bundle JARs that are not specific to the Gradle version.
            it.extension == "jar" && !("gradle" in it.path && gradle.gradleVersion in it.path)
        }.map {
            zipTree(it)
        }
    })
}
