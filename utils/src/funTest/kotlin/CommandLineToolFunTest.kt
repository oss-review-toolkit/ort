package org.ossreviewtoolkit.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.file.aFile
import io.kotest.matchers.result.BeFailure
import io.kotest.matchers.result.BeSuccess
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import java.io.File

class CommandLineToolFunTest : StringSpec({
    val toolName = if (Os.isWindows) "CommandLineTool.bat" else "CommandLineTool.sh"
    val toolDir = File("src/funTest/assets").absoluteFile
    val toolPath = toolDir.resolve(toolName)

    "The command line tool should exist" {
        toolPath shouldBe aFile()
    }

    "Invoking the tool without a path should fail" {
        val tool = CommandLineTool2(toolName)

        tool() should BeFailure()
    }

    "Invoking the tool with a path should succeed" {
        val currentDir = System.getProperty("user.dir")
        val tool = CommandLineTool2(toolName)

        val result = tool(path = toolDir)

        result should BeSuccess()
        with(result.getOrThrow()) {
            stdout shouldContain "Current directory: $currentDir"
            stdout shouldContain "Path to script: $toolPath"
            stderr shouldContain "Hello to stderr!"
            exitCode shouldBe 42
        }
    }

    "Invoking the tool without extension should succeed on Windows".config(enabled = Os.isWindows) {
        val tool = CommandLineTool2(toolName.substringBeforeLast("."))

        tool(path = toolDir) should BeSuccess()
    }

    "Invoking a tool from the PATH environment should succeed" {
        val whereOrWhich = if (Os.isWindows) "where" else "which"
        val tool = CommandLineTool2(whereOrWhich)

        tool() should BeSuccess()
    }

    "Getting the transformed version of the tool should succeed" {
        val tool = CommandLineTool2(toolName, arrayOf("--get-version")) { it.removePrefix("The version is ") }

        tool.version(path = toolDir) shouldBe "1.2.3"
    }
})
