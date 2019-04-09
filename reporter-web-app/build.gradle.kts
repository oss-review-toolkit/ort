import org.apache.tools.ant.taskdefs.condition.Os

tasks.addRule("Pattern: yarn<Command>") {
    val taskName = this
    if (taskName.startsWith("yarn")) {
        val yarn = if (Os.isFamily(Os.FAMILY_WINDOWS)) "yarn.cmd" else "yarn"
        val command = taskName.removePrefix("yarn").decapitalize()

        tasks.register<Exec>(taskName) {
            commandLine = listOf(yarn, command)
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
