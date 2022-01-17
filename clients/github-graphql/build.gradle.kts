/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

import com.expediagroup.graphql.plugin.gradle.config.GraphQLSerializer
import com.expediagroup.graphql.plugin.gradle.graphql

val graphqlPluginVersion: String by project
val kotlinxSerializationVersion: String by project
val ktorVersion: String by project
val log4jApiKotlinVersion: String by project
val wiremockVersion: String by project

plugins {
    `java-library`

    kotlin("plugin.serialization")

    id("com.expediagroup.graphql")
}

graphql {
    client {
        packageName = "org.ossreviewtoolkit.clients.github"
        schemaFile = file("${project.projectDir}/src/main/assets/schema.docs.graphql")
        queryFileDirectory = "${project.projectDir}/src/main/assets/"
        serializer = GraphQLSerializer.KOTLINX
    }
}

dependencies {
    implementation("com.expediagroup:graphql-kotlin-ktor-client:$graphqlPluginVersion")
    implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
    implementation("org.apache.logging.log4j:log4j-api-kotlin:$log4jApiKotlinVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")

    testImplementation("com.github.tomakehurst:wiremock-jre8:$wiremockVersion")
}
