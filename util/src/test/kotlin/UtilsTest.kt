package com.here.ort.util

import com.vdurmont.semver4j.Semver

import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.WordSpec

import java.nio.file.Paths

class UtilsTest : WordSpec({
    "normalizeVcsUrl" should {
        "properly handle NPM shortcut URLs" {
            val packages = mapOf(
                    "npm/npm"
                            to "https://github.com/npm/npm.git",
                    "gist:11081aaa281"
                            to "https://gist.github.com/11081aaa281",
                    "bitbucket:example/repo"
                            to "https://bitbucket.org/example/repo.git",
                    "gitlab:another/repo"
                            to "https://gitlab.com/another/repo.git"
            )

            packages.forEach { actualUrl, expectedUrl ->
                normalizeVcsUrl(actualUrl, Semver.SemverType.NPM) shouldBe expectedUrl
            }
        }

        "convert non-https to anonymous https for GitHub URLs" {
            val packages = mapOf(
                    "git://github.com/cheeriojs/cheerio.git"
                            to "https://github.com/cheeriojs/cheerio.git",
                    "git+https://github.com/fb55/boolbase.git"
                            to "https://github.com/fb55/boolbase.git",
                    "git+ssh://git@github.com/logicalparadox/idris.git"
                            to "https://github.com/logicalparadox/idris.git",
                    "https://www.github.com/DefinitelyTyped/DefinitelyTyped.git"
                            to "https://github.com/DefinitelyTyped/DefinitelyTyped.git"
            )

            packages.forEach { actualUrl, expectedUrl ->
                normalizeVcsUrl(actualUrl) shouldBe expectedUrl
            }
        }

        "add missing .git for GitHub URLs" {
            val packages = mapOf(
                    "https://github.com/fb55/nth-check"
                            to "https://github.com/fb55/nth-check.git",
                    "git://github.com/isaacs/inherits"
                            to "https://github.com/isaacs/inherits.git"
            )

            packages.forEach { actualUrl, expectedUrl ->
                normalizeVcsUrl(actualUrl) shouldBe expectedUrl
            }
        }

        "handle trailing slash correctly" {
            val packages = mapOf(
                    "https://github.com/kilian/electron-to-chromium/"
                            to "https://github.com/kilian/electron-to-chromium.git",
                    "git://github.com/isaacs/inherits.git/"
                            to "https://github.com/isaacs/inherits.git"
            )

            packages.forEach { actualUrl, expectedUrl ->
                normalizeVcsUrl(actualUrl) shouldBe expectedUrl
            }
        }

        "handle file schemes correctly" {
            var userDir = System.getProperty("user.dir").replace("\\", "/")
            var userRoot = Paths.get(userDir).root.toString().replace("\\", "/")

            if (!userDir.startsWith("/")) {
                userDir = "/" + userDir
            }

            if (!userRoot.startsWith("/")) {
                userRoot = "/" + userRoot
            }

            val packages = mapOf(
                    "relative/path/to/local/file"
                            to "file://$userDir/relative/path/to/local/file",
                    "relative/path/to/local/dir/"
                            to "file://$userDir/relative/path/to/local/dir",
                    "/absolute/path/to/local/file"
                            to "file://${userRoot}absolute/path/to/local/file",
                    "/absolute/path/to/local/dir/"
                            to "file://${userRoot}absolute/path/to/local/dir"
            )

            packages.forEach { actualUrl, expectedUrl ->
                normalizeVcsUrl(actualUrl) shouldBe expectedUrl
            }
        }
    }
})
