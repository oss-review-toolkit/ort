/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import io.github.hfhbd.kfx.swagger.Swagger

plugins {
    // Apply precompiled plugins.
    id("ort-library-conventions")

    // Apply third-party plugins.
    alias(libs.plugins.kfx)
    alias(libs.plugins.kotlinSerialization)
}

kfx {
    register<Swagger>("ScanOssApi") {
        files.from("swagger/scanoss-vulnerabilities.swagger.json")

        packageName = "org.ossreviewtoolkit.clients.scanoss"

        dependencies {
            compiler(kotlinClasses())
            compiler(kotlinxJson())
            compiler(ktorClient())
        }

        usingKotlinSourceSet(kotlin.sourceSets.main)
    }
}

dependencies {
    api(libs.kotlinx.serialization.core)

    api(ktorLibs.client.core)

    implementation(ktorLibs.http)
    implementation(ktorLibs.utils)
}

description = "A client to communicate with SCANOSS REST API v2."
