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

plugins {
    // Apply core plugins.
    `java-gradle-plugin`
}

gradlePlugin {
    plugins.register("OrtModelPlugin") {
        id = "org.ossreviewtoolkit.plugins.packagemanagers.gradle.plugin"
        implementationClass = "org.ossreviewtoolkit.plugins.packagemanagers.gradleplugin.OrtModelPlugin"
    }
}

dependencies {
    api(project(":plugins:package-managers:gradle-model"))

    api(libs.mavenModel)
    api(libs.mavenModelBuilder)
}

tasks.register<Jar>("fatJar") {
    description = "Creates a fat JAR that includes all required runtime dependencies."
    group = "Build"

    archiveClassifier.set("fat")

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
