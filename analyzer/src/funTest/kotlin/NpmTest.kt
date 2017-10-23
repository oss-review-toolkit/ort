package com.here.ort.analyzer

import com.fasterxml.jackson.databind.node.ArrayNode

import com.here.ort.analyzer.managers.NPM
import com.here.ort.util.parseJsonProcessOutput
import com.here.ort.util.yamlMapper

import io.kotlintest.TestCaseContext
import io.kotlintest.matchers.endWith
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldNotBe
import io.kotlintest.matchers.shouldThrow
import io.kotlintest.matchers.startWith
import io.kotlintest.specs.WordSpec

import java.io.File

class NpmTest : WordSpec() {
    private val projectDir = File("src/funTest/assets/projects/synthetic/project-npm")

    @Suppress("CatchException")
    override fun interceptTestCase(context: TestCaseContext, test: () -> Unit) {
        try {
            test()
        } catch (exception: Exception) {
            // Make sure the node_modules directory is always deleted from each subdirectory to prevent side-effects
            // from failing tests.
            projectDir.listFiles().forEach {
                if (it.isDirectory) {
                    val nodeModulesDir = File(it, "node_modules")
                    val gitKeepFile = File(nodeModulesDir, ".gitkeep")
                    if (nodeModulesDir.isDirectory && !gitKeepFile.isFile) {
                        nodeModulesDir.deleteRecursively()
                    }
                }
            }

            throw exception
        }
    }

    init {
        "NPM" should {
            "resolve shrinkwrap dependencies correctly" {
                val workingDir = File(projectDir, "shrinkwrap")
                val packageFile = File(workingDir, "package.json")
                val expectedResult =
                        File("src/funTest/assets/projects/synthetic/project-npm-expected-output.yml")
                                .readText()
                                .replaceFirst("project-npm", "project-npm-${workingDir.name}")

                val result = NPM.resolveDependencies(projectDir, listOf(packageFile))[packageFile]

                NPM.command(workingDir) shouldBe NPM.npm
                yamlMapper.writeValueAsString(result) shouldBe expectedResult
            }

            "resolve package-lock dependencies correctly" {
                val workingDir = File(projectDir, "package-lock")
                val packageFile = File(workingDir, "package.json")
                val expectedResult =
                        File("src/funTest/assets/projects/synthetic/project-npm-expected-output.yml")
                                .readText()
                                .replaceFirst("project-npm", "project-npm-${workingDir.name}")

                val result = NPM.resolveDependencies(projectDir, listOf(packageFile))[packageFile]

                NPM.command(workingDir) shouldBe NPM.npm
                yamlMapper.writeValueAsString(result) shouldBe expectedResult
            }

            "abort if no lockfile is present" {
                val workingDir = File(projectDir, "no-lockfile")
                val packageFile = File(workingDir, "package.json")

                val exception = shouldThrow<IllegalArgumentException> {
                    NPM.resolveDependencies(projectDir, listOf(packageFile))
                }
                @Suppress("UnsafeCallOnNullableType")
                exception.message!! should startWith("No lockfile found in")
            }

            "abort if multiple lockfiles are present" {
                val workingDir = File(projectDir, "multiple-lockfiles")
                val packageFile = File(workingDir, "package.json")

                val exception = shouldThrow<IllegalArgumentException> {
                    NPM.resolveDependencies(projectDir, listOf(packageFile))
                }
                @Suppress("UnsafeCallOnNullableType")
                exception.message!! should endWith("contains multiple lockfiles. It is ambiguous which one to use.")
            }

            "resolve dependencies even if the node_modules directory already exists" {
                val workingDir = File(projectDir, "node-modules")
                val packageFile = File(workingDir, "package.json")
                val expectedResult =
                        File("src/funTest/assets/projects/synthetic/project-npm-expected-output.yml")
                                .readText()
                                .replaceFirst("project-npm", "project-npm-${workingDir.name}")

                val result = NPM.resolveDependencies(projectDir, listOf(packageFile))[packageFile]

                NPM.command(workingDir) shouldBe NPM.npm
                yamlMapper.writeValueAsString(result) shouldBe expectedResult
            }
        }

        "yarn" should {
            "resolve dependencies correctly" {
                val workingDir = File(projectDir, "yarn")
                val packageFile = File(workingDir, "package.json")
                val expectedResult =
                        File("src/funTest/assets/projects/synthetic/project-npm-expected-output.yml")
                                .readText()
                                .replaceFirst("project-npm", "project-npm-${workingDir.name}")

                val result = NPM.resolveDependencies(projectDir, listOf(packageFile))[packageFile]

                NPM.command(workingDir) shouldBe NPM.yarn
                yamlMapper.writeValueAsString(result) shouldBe expectedResult
            }
        }
    }
}
