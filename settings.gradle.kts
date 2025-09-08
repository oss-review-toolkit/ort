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

import dev.aga.gradle.versioncatalogs.Generator.generate
import dev.aga.gradle.versioncatalogs.GeneratorConfig

// Enable type-safe project accessors, see:
// https://docs.gradle.org/current/userguide/declaring_dependencies.html#sec:type-safe-project-accessors
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "oss-review-toolkit"

include(":advisor")
include(":analyzer")
include(":cli")
include(":cli-helper")
include(":cli-test-launcher")
include(":clients:bazel-module-registry")
include(":clients:clearly-defined")
include(":clients:dos")
include(":clients:foojay")
include(":clients:fossid-webapp")
include(":clients:oss-index")
include(":clients:osv")
include(":clients:vulnerable-code")
include(":detekt-rules")
include(":downloader")
include(":evaluator")
include(":model")
include(":notifier")
include(":reporter")
include(":scanner")
include(":utils:common")
include(":utils:config")
include(":utils:ort")
include(":utils:scripting")
include(":utils:spdx")
include(":utils:spdx-document")
include(":utils:test")
include(":version-catalog")

project(":clients:bazel-module-registry").name = "bazel-module-registry-client"
project(":clients:clearly-defined").name = "clearly-defined-client"
project(":clients:dos").name = "dos-client"
project(":clients:fossid-webapp").name = "fossid-webapp-client"
project(":clients:oss-index").name = "oss-index-client"
project(":clients:osv").name = "osv-client"
project(":clients:vulnerable-code").name = "vulnerable-code-client"

project(":utils:common").name = "common-utils"
project(":utils:config").name = "config-utils"
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
    id("dev.aga.gradle.version-catalog-generator").version("3.3.0")
    id("org.gradle.toolchains.foojay-resolver-convention").version("1.0.0")
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }

    generate("jacksonLibs") {
        fromToml("jackson-bom") {
            aliasPrefixGenerator = GeneratorConfig.NO_PREFIX
        }
    }
}
