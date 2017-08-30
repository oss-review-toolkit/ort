package com.here.provenanceanalyzer.functionaltest

import com.here.provenanceanalyzer.Main

import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * A test for the main entry point of the application.
 */
class MainTest : StringSpec() {
    init {
        "Activating only NPM works" {
            // Redirect standard output to a stream.
            val standardOut = System.out
            val streamOut = ByteArrayOutputStream()
            System.setOut(PrintStream(streamOut))

            Main.main(arrayOf("-m", "NPM", "-i", "src/funTest/assets/projects/synthetic/project-npm/package-lock"))

            // Restore standard output.
            System.setOut(standardOut)
            val lines = streamOut.toString().lines()

            lines.component1() shouldBe "The following package managers are activated:"
            lines.component2() shouldBe "\tNPM"
            lines.component3().startsWith("\t") shouldBe false
        }
    }
}
