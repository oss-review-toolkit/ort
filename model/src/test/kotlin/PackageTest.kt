package com.here.ort.model

import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.WordSpec

/**
 * Test cases for [Package].
 */
class PackageTest : WordSpec({
    "normalizedVcsUrl" should {
        "actually be normalized" {
            val pkg = Package("", "", "", "", "1.0.0", "", "", "", "", "", "", "https://github.com/fb55/nth-check", "")
            val expectedUrl = "https://github.com/fb55/nth-check.git"

            pkg.normalizedVcsUrl shouldBe expectedUrl
        }

        "should understand NPM shortcuts for NPM packages" {
            val pkg = Package("NPM", "", "", "", "1.0.0", "", "", "", "", "", "", "npm/npm", "")
            val expectedUrl = "https://github.com/npm/npm.git"

            pkg.normalizedVcsUrl shouldBe expectedUrl
        }
    }
})
