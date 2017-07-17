package com.here.provenanceanalyzer.functionaltest

import com.here.provenanceanalyzer.managers.NPM

import io.kotlintest.TestCaseContext
import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

class NpmTest : StringSpec() {
    private val projectDir = File("src/funTest/assets/projects/synthetic/project-npm")

    @Suppress("CatchException")
    override fun interceptTestCase(context: TestCaseContext, test: () -> Unit) {
        try {
            test()
        } catch (exception: Exception) {
            // Make sure the node_modules directory is always deleted to prevent side-effects from failing tests.
            val nodeModulesDir = File(projectDir, "node_modules")
            if (nodeModulesDir.isDirectory) {
                nodeModulesDir.deleteRecursively()
            }

            throw exception
        }
    }

    init {
        "yarn dependencies are resolved correctly" {
            val expectedDependencies = File(
                    "src/funTest/assets/projects/synthetic/project-npm-expected-yarn-dependencies.txt").readText()
            val resolvedDependencies = NPM.installDependencies(projectDir, NPM.yarn).toString()

            resolvedDependencies shouldBe expectedDependencies
        }

        "NPM dependencies are resolved correctly" {
            val expectedDependencies = File(
                    "src/funTest/assets/projects/synthetic/project-npm-expected-npm-dependencies.txt").readText()

            val resolvedDependencies = NPM.installDependencies(projectDir, NPM.npm).toString()

            resolvedDependencies shouldBe expectedDependencies
        }
    }
}
