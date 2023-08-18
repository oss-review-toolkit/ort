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

import org.gradle.accessors.dm.LibrariesForLibs

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

private val Project.libs: LibrariesForLibs
    get() = extensions.getByType()

plugins {
    // Use Kotlin DSL to write precompiled script plugins.
    `kotlin-dsl`
}

repositories {
    // Allow to resolve external plugins from precompiled script plugins.
    gradlePluginPortal()
}

dependencies {
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))

    implementation(libs.detekt)
    implementation(libs.dokkatoo)
    implementation(libs.graalVmNativeImage)
    implementation(libs.jgit)
    implementation(libs.kotlin)
}

val javaVersion = JavaVersion.current()
val maxKotlinJvmTarget = runCatching { JvmTarget.fromTarget(javaVersion.majorVersion) }
    .getOrDefault(enumValues<JvmTarget>().max())

tasks.withType<JavaCompile>().configureEach {
    // Align this with Kotlin to avoid errors, see https://youtrack.jetbrains.com/issue/KT-48745.
    sourceCompatibility = maxKotlinJvmTarget.target
    targetCompatibility = maxKotlinJvmTarget.target
}

tasks.withType<KotlinCompile>().configureEach {
    val customCompilerArgs = listOf(
        "-opt-in=kotlin.ExperimentalStdlibApi"
    )

    compilerOptions {
        freeCompilerArgs.addAll(customCompilerArgs)
        languageVersion = KotlinVersion.KOTLIN_1_9
        jvmTarget = maxKotlinJvmTarget
    }
}
