package com.here.provenanceanalyzer.functionaltest

import com.here.provenanceanalyzer.model.Package

import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.WordSpec

/**
 * Test cases for the Dependency model.
 */
class PackageTest : WordSpec({
    "normalizeRepositoryUrl" should {
        "properly handle NPM shortcut URLs" {
            val packages = mapOf(
                    Package("NPM", "", "", "", "1.0.0", "", "", "", "npm/npm", "")
                            to "https://github.com/npm/npm.git",
                    Package("NPM", "", "", "", "1.0.0", "", "", "", "gist:11081aaa281", "")
                            to "https://gist.github.com/11081aaa281",
                    Package("NPM", "", "", "", "1.0.0", "", "", "", "bitbucket:example/repo", "")
                            to "https://bitbucket.org/example/repo.git",
                    Package("NPM", "", "", "", "1.0.0", "", "", "", "gitlab:another/repo", "")
                            to "https://gitlab.com/another/repo.git"
            )

            packages.forEach { actual, expectedScm ->
                actual.normalizedVcsUrl shouldBe expectedScm
            }
        }

        "convert non-https to anonymous https for GitHub URLs" {
            val packages = mapOf(
                    Package("", "", "", "", "1.0.0", "", "", "", "git://github.com/cheeriojs/cheerio.git", "")
                            to "https://github.com/cheeriojs/cheerio.git",
                    Package("", "", "", "", "1.0.0", "", "", "", "git+https://github.com/fb55/boolbase.git", "")
                            to "https://github.com/fb55/boolbase.git",
                    Package("", "", "", "", "1.0.0", "", "", "",
                            "git+ssh://git@github.com/logicalparadox/idris.git", "")
                            to "https://github.com/logicalparadox/idris.git",
                    Package("", "", "", "", "1.0.0", "", "", "",
                            "https://www.github.com/DefinitelyTyped/DefinitelyTyped.git", "")
                            to "https://github.com/DefinitelyTyped/DefinitelyTyped.git"
            )

            packages.forEach { actual, expectedScm ->
                actual.normalizedVcsUrl shouldBe expectedScm
            }
        }

        "add missing .git for GitHub URLs" {
            val packages = mapOf(
                    Package("", "", "", "", "1.0.0", "", "", "", "https://github.com/fb55/nth-check", "")
                            to "https://github.com/fb55/nth-check.git",
                    Package("", "", "", "", "1.0.0", "", "", "", "git://github.com/isaacs/inherits", "")
                            to "https://github.com/isaacs/inherits.git"
            )

            packages.forEach { actual, expectedScm ->
                actual.normalizedVcsUrl shouldBe expectedScm
            }
        }
    }
})
