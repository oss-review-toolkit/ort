package com.here.provenanceanalyzer.functionaltest

import com.here.provenanceanalyzer.model.Dependency

import com.vdurmont.semver4j.Semver
import com.vdurmont.semver4j.Semver.SemverType

import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.WordSpec

/**
 * Test cases for the Dependency model.
 */
class DependencyTest : WordSpec({
    "normalizeRepositoryUrl" should {
        "properly handle NPM shortcut URLs" {
            val dependencies = mapOf(
                    Dependency(artifact = "", version = Semver("1.0.0", SemverType.NPM), scope = "",
                            scm = "npm/npm")
                            to "https://github.com/npm/npm.git",
                    Dependency(artifact = "", version = Semver("1.0.0", SemverType.NPM), scope = "",
                            scm = "gist:11081aaa281")
                            to "https://gist.github.com/11081aaa281",
                    Dependency(artifact = "", version = Semver("1.0.0", SemverType.NPM), scope = "",
                            scm = "bitbucket:example/repo")
                            to "https://bitbucket.org/example/repo.git",
                    Dependency(artifact = "", version = Semver("1.0.0", SemverType.NPM), scope = "",
                            scm = "gitlab:another/repo")
                            to "https://gitlab.com/another/repo.git"
            )

            dependencies.forEach { actual, expectedScm ->
                actual.normalizedScm shouldBe expectedScm
            }
        }

        "convert non-https to anonymous https for GitHub URLs" {
            val dependencies = mapOf(
                    Dependency(artifact = "", version = Semver("1.0.0"), scope = "",
                            scm = "git://github.com/cheeriojs/cheerio.git")
                            to "https://github.com/cheeriojs/cheerio.git",
                    Dependency(artifact = "", version = Semver("1.0.0"), scope = "",
                            scm = "git+https://github.com/fb55/boolbase.git")
                            to "https://github.com/fb55/boolbase.git",
                    Dependency(artifact = "", version = Semver("1.0.0"), scope = "",
                            scm = "git+ssh://git@github.com/logicalparadox/idris.git")
                            to "https://github.com/logicalparadox/idris.git",
                    Dependency(artifact = "", version = Semver("1.0.0"), scope = "",
                            scm = "https://www.github.com/DefinitelyTyped/DefinitelyTyped.git")
                            to "https://github.com/DefinitelyTyped/DefinitelyTyped.git"
            )

            dependencies.forEach { actual, expectedScm ->
                actual.normalizedScm shouldBe expectedScm
            }
        }

        "add missing .git for GitHub URLs" {
            val dependencies = mapOf(
                    Dependency(artifact = "", version = Semver("1.0.0"), scope = "",
                            scm = "https://github.com/fb55/nth-check")
                            to "https://github.com/fb55/nth-check.git",
                    Dependency(artifact = "", version = Semver("1.0.0"), scope = "",
                            scm = "git://github.com/isaacs/inherits")
                            to "https://github.com/isaacs/inherits.git"
            )

            dependencies.forEach { actual, expectedScm ->
                actual.normalizedScm shouldBe expectedScm
            }
        }
    }
})
