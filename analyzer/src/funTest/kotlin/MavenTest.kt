package com.here.ort.analyzer

import com.here.ort.analyzer.managers.Maven

import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

class MavenTest : StringSpec({
    "computed dependencies are correct" {
        val projectDir = File("projects/external/jgnash")
        val expectedDependencies = File(
                "src/funTest/assets/projects/external/jgnash-expected-maven-dependencies.txt").readLines()
        val resolvedDependencies = Maven.resolveDependencies(projectDir, listOf(File(projectDir, "pom.xml")))

        resolvedDependencies shouldBe expectedDependencies
    }.config(enabled = false)
})
