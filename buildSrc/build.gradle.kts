/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

import java.io.FileInputStream
import java.util.Properties

// Get the root project's properties as extras.
FileInputStream("${rootDir.parentFile}/gradle.properties").use {
    Properties().apply { load(it) }.forEach {
        extra[it.key.toString()] = it.value.toString()
    }
}

plugins {
    `kotlin-dsl`
}

repositories {
    exclusiveContent {
        forRepository {
            maven("https://plugins.gradle.org/m2/")
        }

        filter {
            // The regex has to match at least these group strings:
            // "org.gradle.kotlin.kotlin-dsl"
            // "gradle.plugin.org.jetbrains.gradle.plugin.idea-ext"
            includeGroupByRegex(".*\\bgradle\\.(plugin|kotlin)\\b.*")
        }
    }
}

val ideaExtPluginVersion = extra["ideaExtPluginVersion"]

dependencies {
    implementation("gradle.plugin.org.jetbrains.gradle.plugin.idea-ext:gradle-idea-ext:$ideaExtPluginVersion")
}
