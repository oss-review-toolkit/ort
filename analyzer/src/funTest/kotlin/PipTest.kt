package com.here.ort.analyzer

import com.here.ort.analyzer.managers.PIP
import com.here.ort.util.yamlMapper

import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

class PipTest : StringSpec({
    "spdx-tools-python dependencies are resolved correctly" {
        val workingDir = File("src/funTest/assets/projects/external")
        val projectDir = File(workingDir, "spdx-tools-python")
        val packageFile = File(projectDir, "setup.py")
        val expectedResult = File(workingDir, "spdx-tools-python-expected-output.yml").readText()

        val result = PIP.resolveDependencies(projectDir, listOf(packageFile))[packageFile]

        yamlMapper.writeValueAsString(result) shouldBe expectedResult
    }
})
