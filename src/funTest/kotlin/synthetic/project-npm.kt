package com.here.provenanceanalyzer.functionaltest

import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec
import java.nio.file.Paths

class ProjectNpmTest : StringSpec() {

    init {
        "computed dependencies are correct" {
            val expectedDependencies = readResource("/projects/synthetic/project-npm-expected-npm-dependencies.txt")
            val resolvedDependencies = NPM.resolveDependencies(
                    listOf(Paths.get("src/funTest/resources/projects/synthetic/project-npm/package.json"))
            )

            resolvedDependencies.size shouldBe 1
            val resolvedDependencyList = resolvedDependencies.values.first().toString().split("\n").
                    filter { it.isNotEmpty() }
            resolvedDependencyList.size shouldBe expectedDependencies.size
            resolvedDependencyList.zip(expectedDependencies).forEach { (resolved, expected) ->
                resolved shouldBe expected
            }
        }
    }

}
