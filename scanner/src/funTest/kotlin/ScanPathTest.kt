package com.here.ort.scanner

import com.here.ort.scanner.scanners.ScanCode
import io.kotlintest.matchers.shouldBe

import io.kotlintest.specs.StringSpec

import java.io.File

class ScanPathTest : StringSpec() {
    private val outputDir = createTempDir()

    init {
        "ScanCode recognizes our own LICENSE" {
            val result = ScanCode.scan(File("../LICENSE"), outputDir)
            result shouldBe setOf("Apache-2.0")
        }
    }
}
