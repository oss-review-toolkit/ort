import org.apache.tools.ant.taskdefs.condition.Os

import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsSetupTask
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnSetupTask

// The Yarn plugin is only applied programmatically for Kotlin projects that target JavaScript. As we do not target
// JavaScript from Kotlin (yet), manually apply the plugin to make its setup tasks available.
YarnPlugin.apply(project).version = "1.17.3"

// The Yarn plugin registers tasks always on the root project, see
// https://github.com/JetBrains/kotlin/blob/2f90742/libraries/tools/kotlin-gradle-plugin/src/main/kotlin/org/jetbrains/kotlin/gradle/targets/js/yarn/YarnPlugin.kt#L27-L31
val kotlinNodeJsSetup by rootProject.tasks.existing(NodeJsSetupTask::class)
val kotlinYarnSetup by rootProject.tasks.existing(YarnSetupTask::class)

val nodeDir = kotlinNodeJsSetup.get().destination
val nodeBinDir = if (Os.isFamily(Os.FAMILY_WINDOWS)) nodeDir else nodeDir.resolve("bin")
val nodeExecutable = if (Os.isFamily(Os.FAMILY_WINDOWS)) nodeBinDir.resolve("node.exe") else nodeBinDir.resolve("node")

val yarnDir = kotlinYarnSetup.get().destination
val yarnJs = yarnDir.resolve("bin/yarn.js")

kotlinNodeJsSetup {
    logger.quiet("Will use the Node executable file from '$nodeExecutable'.")

    // If the node binary is missing, force a re-download of the NodeJs distribution, see
    // https://youtrack.jetbrains.com/issue/KT-34989.
    if (!nodeExecutable.isFile && nodeDir.deleteRecursively()) {
        logger.info("Successfully deleted the incomplete '$nodeDir' directory to trigger the NodeJs download.")
    }
}

kotlinYarnSetup {
    logger.quiet("Will use the Yarn JavaScript file from '$yarnJs'.")
}

tasks.addRule("Pattern: yarn<Command>") {
    val taskName = this
    if (taskName.startsWith("yarn")) {
        val command = taskName.removePrefix("yarn").decapitalize()

        tasks.register<Exec>(taskName) {
            // Execute the Yarn version downloaded by Gradle using the NodeJs version downloaded by Gradle.
            commandLine = listOf(nodeExecutable.path, yarnJs.path, command)
        }
    }
}

/*
 * Further configure rule tasks, e.g. with inputs and outputs.
 */

tasks {
    "yarnInstall" {
        description = "Use Yarn to install the Node.js dependencies."
        group = "Node"

        dependsOn(kotlinYarnSetup)

        inputs.files(listOf("package.json", "yarn.lock"))
        outputs.dir("node_modules")
    }

    "yarnBuild" {
        description = "Use Yarn to build the Node.js application."
        group = "Node"

        dependsOn("yarnInstall")

        inputs.dir("config")
        inputs.dir("node_modules")
        inputs.dir("public")
        inputs.dir("scripts")
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

tasks.register("build") {
    dependsOn(listOf("yarnBuild", "yarnLint"))
}

tasks.register("check") {
    dependsOn("yarnLint")
}

tasks.register<Delete>("clean") {
    delete("build")
    delete("node_modules")
    delete("yarn-error.log")
}
