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

val apacheCommonsEmailVersion: String by project
val jiraRestApiVersion: String by project
val mockkVersion: String by project
val wiremockVersion: String by project

plugins {
    // Apply core plugins.
    `java-library`
}

repositories {
    exclusiveContent {
        forRepository {
            maven("https://packages.atlassian.com/maven-external")
        }

        filter {
            includeGroupByRegex("com\\.atlassian\\..*")
        }
    }
}

dependencies {
    api(project(":model"))

    implementation(project(":utils"))

    implementation("com.atlassian.jira:jira-rest-java-client-api:$jiraRestApiVersion")
    implementation("com.atlassian.jira:jira-rest-java-client-app:$jiraRestApiVersion")
    implementation("org.apache.commons:commons-email:$apacheCommonsEmailVersion")

    testImplementation("com.github.tomakehurst:wiremock:$wiremockVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
}
