package com.here.provenanceanalyzer.functionaltest

import com.here.provenanceanalyzer.managers.NPM

import io.kotlintest.TestCaseContext
import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

class NpmTest : StringSpec() {

    val projectDir = File("src/funTest/resources/projects/synthetic/project-npm")

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
            val expectedDependenciesList = readResource("/projects/synthetic/project-npm-expected-yarn-dependencies.txt")

            val resolvedDependencies = NPM.resolveYarnDependencies(projectDir)
            val resolvedDependencyList = resolvedDependencies.toString().lines().filter { it.isNotEmpty() }

            resolvedDependencyList.zip(expectedDependenciesList).forEach { (resolved, expected) ->
                resolved shouldBe expected
            }

            // As zip() returns a list of pairs whose length is that of the shortest collection we need to compare the
            // sizes, too.
            resolvedDependencyList.size shouldBe expectedDependenciesList.size
        }

        "NPM dependencies are resolved correctly" {
            val expectedDependenciesList = readResource("/projects/synthetic/project-npm-expected-npm-dependencies.txt")

            val resolvedDependencies = NPM.resolveNpmDependencies(projectDir)
            val resolvedDependencyList = resolvedDependencies.toString().lines().filter { it.isNotEmpty() }

            resolvedDependencyList.zip(expectedDependenciesList).forEach { (resolved, expected) ->
                resolved shouldBe expected
            }

            // As zip() returns a list of pairs whose length is that of the shortest collection we need to compare the
            // sizes, too.
            resolvedDependencyList.size shouldBe expectedDependenciesList.size
        }
    }
}
