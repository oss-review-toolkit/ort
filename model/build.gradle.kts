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

import com.here.ort.gradle.*

import org.jetbrains.kotlin.gradle.dsl.KotlinCompile

val config4kVersion: String by project
val jacksonVersion: String by project
val semverVersion: String by project

plugins {
    // Apply core plugins.
    `java-library`
}

dependencies {
    api(project(":spdx-utils"))
    api(project(":utils"))

    api("com.fasterxml.jackson.core:jackson-annotations:$jacksonVersion")
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion")

    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")

    implementation("com.vdurmont:semver4j:$semverVersion")
    implementation("io.github.config4k:config4k:$config4kVersion")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
}

val generateVersionResource by tasks.registering {
    group = "Build"
    description = "Generates a plain text resource file containing the current application version."

    val versionFile = file("$projectDir/src/main/resources/VERSION")

    inputs.property("version", version.toString())
    outputs.file(versionFile)

    doLast {
        versionFile.writeText(version.toString())
    }
}

tasks.withType(KotlinCompile::class) {
    dependsOn(generateVersionResource)
}

rootProject.idea {
    project {
        settings {
            taskTriggers {
                afterSync(generateVersionResource.get())
                beforeBuild(generateVersionResource.get())
            }
        }
    }
}
