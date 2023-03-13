/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

@Suppress("DSL_SCOPE_VIOLATION") // See https://youtrack.jetbrains.com/issue/KTIJ-19369.
plugins {
    `java-library`

    alias(libs.plugins.graphQl)
    alias(libs.plugins.kotlinSerialization)
}

graphql {
    client {
        packageName = "org.ossreviewtoolkit.clients.github"
        schemaFile = file("${project.projectDir}/src/main/assets/schema.graphql")
        queryFileDirectory = "${project.projectDir}/src/main/assets/"
        serializer = GraphQLSerializer.KOTLINX

        parserOptions {
            // Raise the default of 15000 tokens to be able to parse the GitHub grammar.
            maxTokens = 55000
        }
    }
}

dependencies {
    api(libs.ktorClientCore)

    implementation(libs.bundles.kotlinxSerialization)
    implementation(libs.graphQlKtorClient)
    implementation(libs.log4jApiKotlin)

    testImplementation(libs.wiremock)
}
