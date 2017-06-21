package com.here.provenanceanalyzer.functionaltest

import com.here.provenanceanalyzer.managers.NPM

import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

class NpmTest : StringSpec() {
    init {
        "computed yarn dependencies are correct" {
            val expectedDependencies = readResource("/projects/synthetic/project-npm-expected-yarn-dependencies.txt")
            val resolvedDependencies = NPM.resolveYarnDependencies(
                    File("src/funTest/resources/projects/synthetic/project-npm"))

            val resolvedDependencyList = resolvedDependencies.toString().split("\n").filter { it.isNotEmpty() }
            resolvedDependencyList.size shouldBe expectedDependencies.size
            resolvedDependencyList.zip(expectedDependencies).forEach { (resolved, expected) ->
                resolved shouldBe expected
            }
        }

        "computed shrinkwrap dependencies are correct" {
            val expectedDependencies = readResource("/projects/synthetic/project-npm-expected-shrinkwrap-dependencies.txt")
            val resolvedDependencies = NPM.resolveShrinkwrapDependencies(
                    File("src/funTest/resources/projects/synthetic/project-npm/npm-shrinkwrap.json"))

            val resolvedDependencyList = resolvedDependencies.toString().split("\n").filter { it.isNotEmpty() }
            resolvedDependencyList.size shouldBe expectedDependencies.size
            resolvedDependencyList.zip(expectedDependencies).forEach { (resolved, expected) ->
                resolved shouldBe expected
            }
        }
    }

}
