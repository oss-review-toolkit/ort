/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
 * Copyright (C) 2020-2021 Bosch.IO GmbH
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

val digraphVersion: String by project
val jacksonVersion: String by project
val jrubyVersion: String by project
val kotlinxCoroutinesVersion: String by project
val mavenVersion: String by project
val mavenResolverVersion: String by project
val mockkVersion: String by project
val semverVersion: String by project
val sw360ClientVersion: String by project
val toml4jVersion: String by project
val wiremockVersion: String by project

plugins {
    // Apply core plugins.
    `java-library`
}

repositories {
    exclusiveContent {
        forRepository {
            maven("https://repo.gradle.org/gradle/libs-releases/")
        }

        filter {
            includeGroup("org.gradle")
        }
    }

    exclusiveContent {
        forRepository {
            maven("https://repo.eclipse.org/content/repositories/sw360-releases/")
        }

        filter {
            includeGroup("org.eclipse.sw360")
        }
    }
}

dependencies {
    api(project(":clients:clearly-defined"))
    api(project(":model"))

    implementation(project(":downloader"))
    implementation(project(":utils:core-utils"))
    implementation(project(":utils:spdx-utils"))

    implementation("com.fasterxml.jackson.module:jackson-module-jaxb-annotations:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.moandjiezana.toml:toml4j:$toml4jVersion")
    implementation("com.paypal.digraph:digraph-parser:$digraphVersion")
    implementation("com.vdurmont:semver4j:$semverVersion")
    implementation("org.apache.maven:maven-core:$mavenVersion")
    implementation("org.apache.maven:maven-compat:$mavenVersion")

    // The classes from the maven-resolver dependencies are not used directly but initialized by the Plexus IoC
    // container automatically. They are required on the classpath for Maven dependency resolution to work.
    implementation("org.apache.maven.resolver:maven-resolver-connector-basic:$mavenResolverVersion")
    implementation("org.apache.maven.resolver:maven-resolver-transport-file:$mavenResolverVersion")
    implementation("org.apache.maven.resolver:maven-resolver-transport-http:$mavenResolverVersion")
    implementation("org.apache.maven.resolver:maven-resolver-transport-wagon:$mavenResolverVersion")

    implementation("org.eclipse.sw360:client:$sw360ClientVersion")
    implementation("org.gradle:gradle-tooling-api:${gradle.gradleVersion}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
    implementation("org.jruby:jruby-complete:$jrubyVersion")

    testImplementation("com.github.tomakehurst:wiremock-jre8:$wiremockVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
}
