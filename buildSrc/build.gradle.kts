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
    implementation(libs.kotlin)
}
