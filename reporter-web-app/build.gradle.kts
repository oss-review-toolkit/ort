/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

import org.apache.tools.ant.taskdefs.condition.Os

import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsSetupTask
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnSetupTask

// The Yarn plugin is only applied programmatically for Kotlin projects that target JavaScript. As we do not target
// JavaScript from Kotlin (yet), manually apply the plugin to make its setup tasks available.
YarnPlugin.apply(rootProject).version = "1.22.10"

// The Yarn plugin registers tasks always on the root project, see
// https://github.com/JetBrains/kotlin/blob/1.4.0/libraries/tools/kotlin-gradle-plugin/src/main/kotlin/org/jetbrains/kotlin/gradle/targets/js/yarn/YarnPlugin.kt#L53-L57
val kotlinNodeJsSetup by rootProject.tasks.existing(NodeJsSetupTask::class)
val kotlinYarnSetup by rootProject.tasks.existing(YarnSetupTask::class)

val nodeDir = kotlinNodeJsSetup.get().destination
val nodeBinDir = if (Os.isFamily(Os.FAMILY_WINDOWS)) nodeDir else nodeDir.resolve("bin")
val nodeExecutable = if (Os.isFamily(Os.FAMILY_WINDOWS)) nodeBinDir.resolve("node.exe") else nodeBinDir.resolve("node")

val yarnDir = kotlinYarnSetup.get().destination
val yarnJs = yarnDir.resolve("bin/yarn.js")

tasks.addRule("Pattern: yarn<Command>") {
    val taskName = this
    if (taskName.startsWith("yarn")) {
        val command = taskName.removePrefix("yarn").decapitalize()

        tasks.register<Exec>(taskName).configure {
            // Execute the Yarn version downloaded by Gradle using the NodeJs version downloaded by Gradle.
            commandLine = listOf(nodeExecutable.path, yarnJs.path, command)

            // Prepend the directory of the bootstrapped Node.js to the PATH environment.
            environment = environment + mapOf("PATH" to "$nodeBinDir${File.pathSeparator}${environment["PATH"]}")

            outputs.cacheIf { true }
        }
    }
}

/*
 * Further configure rule tasks, e.g. with inputs and outputs.
 */

tasks {
    kotlinNodeJsSetup {
        outputs.upToDateWhen { nodeExecutable.isFile }
        outputs.cacheIf { true }

        doFirst {
            logger.quiet("Setting up Node.js / NPM in '$nodeDir'...")
        }
    }

    kotlinYarnSetup {
        outputs.upToDateWhen { yarnJs.isFile }
        outputs.cacheIf { true }

        doFirst {
            logger.quiet("Setting up Yarn in '$yarnDir'...")
        }
    }

    "yarnInstall" {
        description = "Use Yarn to install the Node.js dependencies."
        group = "Node"

        dependsOn(kotlinYarnSetup)

        inputs.files(".yarnrc", "package.json", "yarn.lock")
        outputs.dir("node_modules")
    }

    "yarnBuild" {
        description = "Use Yarn to build the Node.js application."
        group = "Node"

        dependsOn("yarnInstall")

        inputs.files(".rescriptsrc.js")
        inputs.dir("node_modules")
        inputs.dir("public")
        inputs.dir("src")

        outputs.dir("build")
    }

    "yarnLint" {
        description = "Let Yarn run the linter to check for style issues."
        group = "Node"

        dependsOn("yarnInstall")
    }
}

/*
 * Resemble the Java plugin tasks for convenience.
 */

tasks.register("build").configure {
    dependsOn(listOf("yarnBuild", "yarnLint"))
}

tasks.register("check").configure {
    dependsOn("yarnLint")
}

tasks.register<Delete>("clean").configure {
    delete("build")
    delete("node_modules")
    delete("yarn-error.log")
}
