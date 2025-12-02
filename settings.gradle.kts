/*
 * Copyright (C) 2017 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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
include(":detekt-rules")
include(":downloader")
include(":evaluator")
include(":model")
include(":notifier")
include(":reporter")
include(":scanner")
include(":version-catalog")

includeSubprojects("clients", maxDepth = 2)
includeSubprojects("utils", maxDepth = 2)
includeSubprojects("plugins", maxDepth = 3, setOf("gradle-inspector", "gradle-model", "gradle-plugin", "web-app-template"))

/**
 * Add include statements and custom names for the projects hosted inside [directoryName] and up to [maxDepth] directory
 * levels below, with [accompanyingProjects] to be excluded from the custom project naming logic.
 */
fun includeSubprojects(directoryName: String, maxDepth: Int, accompanyingProjects: Set<String> = emptySet()) {
    file(directoryName).walk().maxDepth(maxDepth).filter {
        it.isFile && it.name == "build.gradle.kts"
    }.mapTo(mutableListOf()) {
        it.parentFile.toRelativeString(rootDir).replace(File.separatorChar, ':')
    }.forEach { projectPath ->
        include(":$projectPath")

        // Give API and subprojects of a type a dedicated name, but keep the names of accompanying project as-is.
        val parts = projectPath.split(':', limit = maxDepth)
        val projectName = parts.last()
        if (parts.size == maxDepth && projectName !in accompanyingProjects) {
            // Convert the plural name for the type of plugin to singular.
            val singularTypeName = parts[maxDepth - 2].let { if (it == "utils") it else it.removeSuffix("s") }

            project(":$projectPath").name = when(projectName) {
                "api" -> "$singularTypeName-api"
                else -> "$projectName-$singularTypeName"
            }
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
    id("dev.aga.gradle.version-catalog-generator").version("3.4.0")
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
