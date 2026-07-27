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

import java.util.Locale

import org.apache.tools.ant.taskdefs.condition.Os

import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsSetupTask

// This module uses npm (not Yarn). The Kotlin/JS NodeJsRootPlugin is bootstrapped just to download a
// pinned Node.js distribution; npm is bundled with that Node.js, so no extra setup task is required.
NodeJsRootPlugin.apply(rootProject)

rootProject.plugins.withType<NodeJsPlugin>().configureEach {
    rootProject.the<NodeJsEnvSpec>().version = "22.13.0"
}

val kotlinNodeJsSetup = rootProject.tasks.withType(NodeJsSetupTask::class).single()

val nodeDir = kotlinNodeJsSetup.destinationProvider.asFile.get()
val nodeBinDir = if (Os.isFamily(Os.FAMILY_WINDOWS)) nodeDir else nodeDir.resolve("bin")
val nodeExecutable = if (Os.isFamily(Os.FAMILY_WINDOWS)) nodeBinDir.resolve("node.exe") else nodeBinDir.resolve("node")

// npm is shipped with the Node.js distribution under "lib/node_modules/npm/bin/npm-cli.js".
val npmCliJs = nodeDir.resolve("lib/node_modules/npm/bin/npm-cli.js")
    .takeIf { it.exists() }
    ?: nodeDir.resolve("node_modules/npm/bin/npm-cli.js")

tasks.addRule("Pattern: npm<Command>") {
    val taskName = this
    if (taskName.startsWith("npm")) {
        val command = taskName.removePrefix("npm").replaceFirstChar { it.lowercase(Locale.ROOT) }

        tasks.register<Exec>(taskName) {
            // Execute the npm CLI via the Node.js version downloaded by Gradle.
            commandLine = listOf(nodeExecutable.path, npmCliJs.path, "run", command)

            val oldPath = System.getenv("PATH")
            val newPath = listOf(
                nodeBinDir.path,
                projectDir.resolve("node_modules/.bin").path,
                oldPath
            ).joinToString(File.pathSeparator)

            environment = environment + ("PATH" to newPath)
        }
    }
}

val npmInstall = tasks.register<Exec>("npmInstall") {
    description = "Use npm to install the Node.js dependencies."
    group = "Node"

    dependsOn(kotlinNodeJsSetup)

    commandLine = listOf(nodeExecutable.path, npmCliJs.path, "install", "--no-audit", "--no-fund")

    val oldPath = System.getenv("PATH")
    environment = environment + ("PATH" to listOf(nodeBinDir.path, oldPath).joinToString(File.pathSeparator))

    inputs.files("package.json", "package-lock.json")

    // Note that "node_modules" cannot be cached due to symlinks, see https://github.com/gradle/gradle/issues/3525.
    outputs.dir("node_modules")
}

val npmBuild = tasks.named("npmBuild") {
    description = "Use npm to build the single-file scan report template."
    group = "Node"

    dependsOn(npmInstall)
    inputs.dir("src")
    inputs.files("index.html", "vite.config.ts", "tsconfig.json", "tsconfig.node.json", "components.json")

    outputs.cacheIf { true }
    outputs.dir("build")
}

val npmLint = tasks.named("npmLint") {
    description = "Run Biome to check for style issues."
    group = "Node"

    dependsOn(npmInstall)
}

val npmTypecheck = tasks.named("npmTypecheck") {
    description = "Run the TypeScript type-checker."
    group = "Node"

    dependsOn(npmInstall)
}

/*
 * Resemble the Java plugin tasks for convenience.
 */

tasks.register("build") {
    dependsOn(npmBuild, npmLint, npmTypecheck)
}

tasks.register("check") {
    dependsOn(npmLint, npmTypecheck)
}

tasks.register<Delete>("clean") {
    delete("build")
    delete("node_modules")
    delete("npm-debug.log")
}

val webAppTemplateConfiguration = configurations.create("webAppTemplateConfiguration") {
    isCanBeResolved = false
}

artifacts {
    add(webAppTemplateConfiguration.name, npmBuild)
}
