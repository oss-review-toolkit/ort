/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

rootProject.name = "oss-review-toolkit"

include(":advisor")
include(":analyzer")
include(":cli")
include(":clients:clearly-defined")
include(":clients:dos")
include(":clients:fossid-webapp")
include(":clients:github-graphql")
include(":clients:nexus-iq")
include(":clients:oss-index")
include(":clients:osv")
include(":clients:scanoss")
include(":clients:vulnerable-code")
include(":detekt-rules")
include(":downloader")
include(":evaluator")
include(":helper-cli")
include(":model")
include(":notifier")
include(":reporter")
include(":scanner")
include(":utils:common")
include(":utils:ort")
include(":utils:scripting")
include(":utils:spdx")
include(":utils:test")

project(":clients:clearly-defined").name = "clearly-defined-client"
project(":clients:dos").name = "dos-client"
project(":clients:fossid-webapp").name = "fossid-webapp-client"
project(":clients:github-graphql").name = "github-graphql-client"
project(":clients:nexus-iq").name = "nexus-iq-client"
project(":clients:oss-index").name = "oss-index-client"
project(":clients:osv").name = "osv-client"
project(":clients:scanoss").name = "scanoss-client"
project(":clients:vulnerable-code").name = "vulnerable-code-client"

project(":utils:common").name = "common-utils"
project(":utils:ort").name = "ort-utils"
project(":utils:scripting").name = "scripting-utils"
project(":utils:spdx").name = "spdx-utils"
project(":utils:test").name = "test-utils"

file("plugins").walk().maxDepth(3).filter {
    it.isFile && it.name == "build.gradle.kts"
}.mapTo(mutableListOf()) {
    it.parentFile.toRelativeString(rootDir).replace(File.separatorChar, ':')
}.forEach { projectPath ->
    include(":$projectPath")

    // Give API and package-manager projects a dedicated name that includes the type of plugin, but keep the names of
    // accompanying project as-is.
    val accompanyingProjects = setOf("gradle-inspector", "gradle-model", "gradle-plugin", "web-app-template")

    val parts = projectPath.split(':')
    if (parts.size == 3 && parts[2] !in accompanyingProjects) {
        // Convert the plural name for the type of plugin to singular.
        val singularTypeName = parts[1].removeSuffix("s")

        project(":$projectPath").name = when(parts[2]) {
            "api" -> "$singularTypeName-api"
            else -> "${parts[2]}-$singularTypeName"
        }
    }
}

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Gradle cannot access the version catalog from here, so hard-code the dependency.
    id("org.gradle.toolchains.foojay-resolver-convention").version("0.7.0")
}
