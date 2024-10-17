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

import com.gradleup.gr8.FilterTransform

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Apply core plugins.
    `java-gradle-plugin`

    // Apply precompiled plugins.
    id("ort-kotlin-conventions")

    // Apply third-party plugins.
    alias(libs.plugins.gr8)
}

gradlePlugin {
    plugins.register("OrtModelPlugin") {
        id = "org.ossreviewtoolkit.plugins.packagemanagers.gradle.plugin"
        implementationClass = "org.ossreviewtoolkit.plugins.packagemanagers.gradleplugin.OrtModelPlugin"
    }
}

val shadow by configurations.creating

repositories {
    google()
}

dependencies {
    api(projects.plugins.packageManagers.gradleModel)

    shadow(libs.maven.model)
    shadow(libs.maven.model.builder)

    compileOnly(gradleApi())
}

val compileOnlyR8 by configurations.creating {
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named<Usage>(Usage.JAVA_API))
        attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, FilterTransform.artifactType)
    }

    extendsFrom(configurations.compileOnly.get())
}

gr8 {
    val shadowedJar = create("plugin") {
        addProgramJarsFrom(tasks.named<Jar>("jar"))
        addProgramJarsFrom(shadow)

        // Classpath JARs are only used by R8 for analysis but are not included in the shadowed JAR.
        addClassPathJarsFrom(compileOnlyR8)

        // See https://issuetracker.google.com/u/1/issues/380805015 for why this is required.
        registerFilterTransform(listOf(".*/impldep/META-INF/versions/.*"))

        proguardFile("rules.pro")
    }

    // Remove the Gradle API dependency that the "java-gradle-plugin" automatically adds.
    removeGradleApiFromApi()

    // Replace the regular JAR with the shadowed one in the publication.
    replaceOutgoingJar(shadowedJar)

    // Allow to compile the module without exposing the shadowed dependencies downstream.
    configurations.compileOnly.get().extendsFrom(shadow)
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
