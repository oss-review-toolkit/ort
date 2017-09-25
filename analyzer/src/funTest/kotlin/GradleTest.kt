package com.here.ort.analyzer

import com.here.ort.analyzer.managers.Gradle
import com.here.ort.util.yamlMapper

import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

class GradleTest : StringSpec() {
    init {
        "Root project dependencies are detected correctly" {
            val projectDir = File("src/funTest/assets/projects/synthetic/gradle")
            val packageFile = File(projectDir, "build.gradle")
            val expectedResult = File("src/funTest/assets/projects/synthetic/project-gradle-expected-output-root.yml")
                    .readText()

            val result = Gradle.resolveDependencies(projectDir, listOf(packageFile))[packageFile]

            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }

        "Project dependencies are detected correctly" {
            val projectDir = File("src/funTest/assets/projects/synthetic/gradle")
            val packageFile = File(projectDir, "app/build.gradle")
            val expectedResult = File("src/funTest/assets/projects/synthetic/project-gradle-expected-output-app.yml")
                    .readText()

            val result = Gradle.resolveDependencies(projectDir, listOf(packageFile))[packageFile]

            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }

        "External dependencies are detected correctly" {
            val projectDir = File("src/funTest/assets/projects/synthetic/gradle")
            val packageFile = File(projectDir, "lib/build.gradle")
            val expectedResult = File("src/funTest/assets/projects/synthetic/project-gradle-expected-output-lib.yml")
                    .readText()

            val result = Gradle.resolveDependencies(projectDir, listOf(packageFile))[packageFile]

            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }

        "Unresolved dependencies are detected correctly" {
            val projectDir = File("src/funTest/assets/projects/synthetic/gradle")
            val packageFile = File(projectDir, "lib-without-repo/build.gradle")
            val expectedResult =
                    File("src/funTest/assets/projects/synthetic/project-gradle-expected-output-lib-without-repo.yml")
                            .readText()

            val result = Gradle.resolveDependencies(projectDir, listOf(packageFile))[packageFile]

            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }
    }
}
