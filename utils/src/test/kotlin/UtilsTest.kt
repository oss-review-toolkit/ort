/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package com.here.ort.utils

import com.here.ort.utils.spdx.calculatePackageVerificationCode
import com.here.ort.utils.spdx.getLicenseText

import io.kotlintest.matchers.endWith
import io.kotlintest.matchers.startWith
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.WordSpec

import java.io.File
import java.nio.file.Paths

class UtilsTest : WordSpec({
    "calculatePackageVerificationCode" should {
        "work for given SHA1s" {
            val sha1sums = listOf(
                    "0811bcab4e7a186f4d0d08d44cc5f06d721e7f6d",
                    "f7a535db519cf832c1119fecdf1ea0514f583886",
                    "0db752599b67b64dd1bdeff77ed9f5aa5437d027",
                    "9706f99c85a781c016a22fd23313e55257e7b3e8",
                    "1d96c52b533a38492ce290bc6831f8702f690e8e",
                    "e77075d2fb2cdeb4406538d9b33d00fd823a527a",
                    "b2f60873fd2c0feaf21eaccaf7ba052ceb12b146",
                    "1c2d4960f5d156e444f9819cec0c44c09f98970f",
                    "ca5fa85664a953084ca9a1de1e393698257495c0",
                    "0d172bac2b6712ecdc7b1e639c3cf8104f2a6a2a",
                    "14b6a02753d05f72ac57144f1ea0da52d97a0ce3",
                    "25306873d3e2434aade745e8a1a6914c215165f6",
                    "afd495c14035961a55d725ba0127e166349f28b9",
                    "1b8f69fa87f1abedd02f6c4766f07f0ceeea7a02",
                    "a4410f034f97b67eccbb8c9596d58c97ad3de988",
                    "a1663801985f4361395831ae17b3256e38810dc2",
                    "2a06f5906e5afb1b283b6f4fd6a21e7906cdde4f",
                    "1a409fc2dcd3dd10549c47793a80c42c3a06c9f0",
                    "2cc787ebd4d29f2e24646f76f9c525336949783e",
                    "3ea2f82d7ce6f638e9466365a328a201f2caa579",
                    "9257afd2d46c3a189ec0d40a45722701d47e9ca5",
                    "4ff8a82b52e1e1c5f8bf0abb25c20859d3f06c62",
                    "0e8faebf9505c9b1d5462adcf34e01e83d110cc8",
                    "824cadd41d399f98d17ae281737c6816846ac75d",
                    "f54dd0df3ab62f1d5687d98076dffdbf690840f6",
                    "91742d83b0feadb4595afeb4e7f4bab2e85f4a98",
                    "64dd561478479f12deda240ae9fe569952328bff",
                    "310fc965173381a02fbe83a889f7c858c4499862"
            )

            calculatePackageVerificationCode(sha1sums) shouldBe "1a74d8321c452522ec516a46893e6a42f36b5953"
        }
    }

    "filterVersionNames" should {
        "return an empty list for a blank version" {
            val names = listOf("dummy")

            filterVersionNames("", names) shouldBe emptyList()
            filterVersionNames(" ", names) shouldBe emptyList()
        }

        "return an empty list for empty names" {
            val names = listOf("")

            filterVersionNames("", names) shouldBe emptyList()
            filterVersionNames(" ", names) shouldBe emptyList()
            filterVersionNames("1.0", names) shouldBe emptyList()
        }

        "return an empty list for blank names" {
            val names = listOf(" ")

            filterVersionNames("", names) shouldBe emptyList()
            filterVersionNames(" ", names) shouldBe emptyList()
            filterVersionNames("1.0", names) shouldBe emptyList()
        }

        "find names separated by underscores" {
            val names = listOf(
                    "A02",
                    "A03",
                    "A04",
                    "DEV0_9_3",
                    "DEV0_9_4",
                    "DEV_0_9_7",
                    "Exoffice",
                    "PROD_1_0_1",
                    "PROD_1_0_2",
                    "PROD_1_0_3",
                    "before_SourceForge",
                    "before_debug_changes"
            )

            filterVersionNames("1.0.3", names).joinToString("\n") shouldBe "PROD_1_0_3"
            filterVersionNames("1", names).joinToString("\n") shouldBe ""
            filterVersionNames("0.3", names).joinToString("\n") shouldBe ""
        }

        "find names separated by dots" {
            val names = listOf(
                    "0.10.0",
                    "0.10.1",
                    "0.5.0",
                    "0.6.0",
                    "0.7.0",
                    "0.8.0",
                    "0.9.0"
            )

            filterVersionNames("0.10.0", names).joinToString("\n") shouldBe "0.10.0"
            filterVersionNames("0.10", names).joinToString("\n") shouldBe ""
            filterVersionNames("10.0", names).joinToString("\n") shouldBe ""
            filterVersionNames("1", names).joinToString("\n") shouldBe ""
        }

        "find names with mixed separators" {
            val names = listOf(
                    "docutils-0.10",
                    "docutils-0.11",
                    "docutils-0.12",
                    "docutils-0.13.1",
                    "docutils-0.14",
                    "docutils-0.14.0a",
                    "docutils-0.14a0",
                    "docutils-0.14rc1",
                    "docutils-0.14rc2",
                    "docutils-0.3.7",
                    "docutils-0.3.9",
                    "docutils-0.4",
                    "docutils-0.5",
                    "docutils-0.6",
                    "docutils-0.7",
                    "docutils-0.8",
                    "docutils-0.8.1",
                    "docutils-0.9",
                    "docutils-0.9.1",
                    "initial",
                    "merged_to_nesting",
                    "prest-0.3.10",
                    "prest-0.3.11",
                    "start"
            )

            filterVersionNames("0.3.9", names).joinToString("\n") shouldBe "docutils-0.3.9"
            filterVersionNames("0.14", names).joinToString("\n") shouldBe "docutils-0.14"
            filterVersionNames("0.3.10", names).joinToString("\n") shouldBe "prest-0.3.10"
            filterVersionNames("0.13", names).joinToString("\n") shouldBe ""
            filterVersionNames("13.1", names).joinToString("\n") shouldBe ""
            filterVersionNames("1.0", names).joinToString("\n") shouldBe ""
        }

        "find names with mixed separators and different capitalization" {
            val names = listOf("CLASSWORLDS_1_1_ALPHA_2")

            filterVersionNames("1.1-alpha-2", names).joinToString("\n") shouldBe "CLASSWORLDS_1_1_ALPHA_2"
        }

        "find names with a 'v' prefix" {
            val names = listOf(
                    "v6.2.0",
                    "v6.2.1",
                    "v6.2.2",
                    "v6.20.0",
                    "v6.20.1",
                    "v6.20.2",
                    "v6.20.3",
                    "v6.21.0",
                    "v6.21.1",
                    "v6.22.0",
                    "v6.22.1",
                    "v6.22.2",
                    "v6.23.0",
                    "v6.23.1",
                    "v6.24.0",
                    "v6.24.1",
                    "v6.25.0",
                    "v6.26.0",
                    "v7.0.0-beta.0",
                    "v7.0.0-beta.1",
                    "v7.0.0-beta.2",
                    "v7.0.0-beta.3"
            )

            filterVersionNames("6.26.0", names).joinToString("\n") shouldBe "v6.26.0"
            filterVersionNames("7.0.0-beta.2", names).joinToString("\n") shouldBe "v7.0.0-beta.2"
            filterVersionNames("7.0.0", names).joinToString("\n") shouldBe ""
            filterVersionNames("2.0", names).joinToString("\n") shouldBe ""
        }

        "find names with the project name as the prefix" {
            val names = listOf(
                    "babel-plugin-transform-member-expression-literals@6.9.0",
                    "babel-plugin-transform-simplify-comparison-operators@6.9.0",
                    "babel-plugin-transform-property-literals@6.9.0"
            )

            filterVersionNames("6.9.0", names, "babel-plugin-transform-simplify-comparison-operators")
                    .joinToString("\n") shouldBe "babel-plugin-transform-simplify-comparison-operators@6.9.0"
        }

        "find names when others with trailing digits are present" {
            val names = listOf(
                    "1.11.6", "1.11.60", "1.11.61", "1.11.62", "1.11.63", "1.11.64", "1.11.65", "1.11.66", "1.11.67",
                    "1.11.68", "1.11.69"
            )

            filterVersionNames("1.11.6", names).joinToString("\n") shouldBe "1.11.6"
        }
    }

    "getLicenseText" should {
        "return the full license text for a valid license id" {
            val text = getLicenseText("Apache-2.0").trim()

            text should startWith("Apache License")
            text should endWith("limitations under the License.")
        }

        "throw an exception for an invalid license id" {
            shouldThrow<IOException> { getLicenseText("FooBar-1.0") }
        }

        "return the exception text for an exception id if handling exceptions is enabled" {
            val text = getLicenseText("Autoconf-exception-2.0", true).trim()

            text should startWith("As a special exception,")
            text should endWith("this special exception to the GPL from your modified version.")
        }

        "throw an exception for an exception id if handling exceptions is disabled" {
            shouldThrow<IOException> { getLicenseText("Autoconf-exception-2.0", false) }
        }
    }

    "getPathFromEnvironment" should {
        "find system executables on Windows".config(enabled = OS.isWindows) {
            val winverPath = File(System.getenv("SYSTEMROOT"), "system32/winver.exe")

            getPathFromEnvironment("winver") shouldNotBe null
            getPathFromEnvironment("winver") shouldBe winverPath

            getPathFromEnvironment("winver.exe") shouldNotBe null
            getPathFromEnvironment("winver.exe") shouldBe winverPath

            getPathFromEnvironment("") shouldBe null
            getPathFromEnvironment("*") shouldBe null
            getPathFromEnvironment("nul") shouldBe null
        }

        "find system executables on non-Windows".config(enabled = !OS.isWindows) {
            getPathFromEnvironment("sh") shouldNotBe null
            getPathFromEnvironment("sh") shouldBe File("/bin/sh")

            getPathFromEnvironment("") shouldBe null
            getPathFromEnvironment("/") shouldBe null
        }
    }

    "normalizeVcsUrl" should {
        "do nothing for empty URLs" {
            normalizeVcsUrl("") shouldBe ""
        }

        "handle anonymous Git / HTTPS URL schemes" {
            val packages = mapOf(
                    "git://github.com/cheeriojs/cheerio.git"
                            to "https://github.com/cheeriojs/cheerio.git",
                    "git+https://github.com/fb55/boolbase.git"
                            to "https://github.com/fb55/boolbase.git",
                    "https://www.github.com/DefinitelyTyped/DefinitelyTyped.git"
                            to "https://github.com/DefinitelyTyped/DefinitelyTyped.git"
            )

            packages.forEach { actualUrl, expectedUrl ->
                normalizeVcsUrl(actualUrl) shouldBe expectedUrl
            }
        }

        "handle authenticated SSH URL schemes" {
            val packages = mapOf(
                    "git+ssh://git@github.com/logicalparadox/idris.git"
                            to "ssh://git@github.com/logicalparadox/idris.git",
                    "git@github.com:heremaps/oss-review-toolkit.git"
                            to "ssh://git@github.com/heremaps/oss-review-toolkit.git",
                    "ssh://user@gerrit.server.com:29418/parent/project"
                            to "ssh://user@gerrit.server.com:29418/parent/project"
            )

            packages.forEach { actualUrl, expectedUrl ->
                normalizeVcsUrl(actualUrl) shouldBe expectedUrl
            }
        }

        "add missing https:// for GitHub URLs" {
            val packages = mapOf(
                    "github.com/leanovate/play-mockws"
                            to "https://github.com/leanovate/play-mockws.git"
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

        "add missing git@ for GitHub SSH URLs" {
            val packages = mapOf(
                    "ssh://github.com/heremaps/here-aaa-java-sdk.git"
                            to "ssh://git@github.com/heremaps/here-aaa-java-sdk.git",
                    "ssh://github.com/heremaps/here-aaa-java-sdk"
                            to "ssh://git@github.com/heremaps/here-aaa-java-sdk.git"
            )

            packages.forEach { actualUrl, expectedUrl ->
                normalizeVcsUrl(actualUrl) shouldBe expectedUrl
            }
        }

        "handle a trailing slash correctly" {
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
                userDir = "/$userDir"
            }

            if (!userRoot.startsWith("/")) {
                userRoot = "/$userRoot"
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

        "convert git to https for GitHub URLs" {
            val packages = mapOf(
                    "git://github.com:ccavanaugh/jgnash.git"
                            to "https://github.com/ccavanaugh/jgnash.git",
                    "git://github.com/netty/netty.git/netty-buffer"
                            to "https://github.com/netty/netty.git/netty-buffer"
            )

            packages.forEach { actualUrl, expectedUrl ->
                normalizeVcsUrl(actualUrl) shouldBe expectedUrl
            }
        }

        "not strip svn+ prefix from Subversion URLs" {
            val packages = mapOf(
                    "svn+ssh://svn.code.sf.net/p/stddecimal/code/trunk"
                            to "svn+ssh://svn.code.sf.net/p/stddecimal/code/trunk"
            )

            packages.forEach { actualUrl, expectedUrl ->
                normalizeVcsUrl(actualUrl) shouldBe expectedUrl
            }
        }

        "fixup crazy URLs" {
            val packages = mapOf(
                    "git@github.com/cisco/node-jose.git"
                            to "ssh://git@github.com/cisco/node-jose.git",
                    "https://git@github.com:hacksparrow/node-easyimage.git"
                            to "https://github.com/hacksparrow/node-easyimage.git"
            )

            packages.forEach { actualUrl, expectedUrl ->
                normalizeVcsUrl(actualUrl) shouldBe expectedUrl
            }
        }
    }

    "redirecting output" should {
        // Use a relatively large number of lines that results in more than 64k to be written to test against the pipe
        // buffer limit on Linux, see https://unix.stackexchange.com/a/11954/53328.
        val numberOfLines = 10000

        "work for stdout only" {
            val stdout = redirectStdout {
                for (i in 1..numberOfLines) System.out.println("stdout: $i")
            }

            // The last printed line has a newline, resulting in a trailing blank line.
            val stdoutLines = stdout.lines().dropLast(1)
            stdoutLines.count() shouldBe numberOfLines
            stdoutLines.last() shouldBe "stdout: $numberOfLines"
        }

        "work for stderr only" {
            val stderr = redirectStderr {
                for (i in 1..numberOfLines) System.err.println("stderr: $i")
            }

            // The last printed line has a newline, resulting in a trailing blank line.
            val stderrLines = stderr.lines().dropLast(1)
            stderrLines.count() shouldBe numberOfLines
            stderrLines.last() shouldBe "stderr: $numberOfLines"
        }

        "work for stdout and stderr at the same time" {
            var stderr = ""
            val stdout = redirectStdout {
                stderr = redirectStderr {
                    for (i in 1..numberOfLines) {
                        System.out.println("stdout: $i")
                        System.err.println("stderr: $i")
                    }
                }
            }

            // The last printed line has a newline, resulting in a trailing blank line.
            val stdoutLines = stdout.lines().dropLast(1)
            stdoutLines.count() shouldBe numberOfLines
            stdoutLines.last() shouldBe "stdout: $numberOfLines"

            // The last printed line has a newline, resulting in a trailing blank line.
            val stderrLines = stderr.lines().dropLast(1)
            stderrLines.count() shouldBe numberOfLines
            stderrLines.last() shouldBe "stderr: $numberOfLines"
        }

        "work when trapping exit calls" {
            var exitCode : Int? = null

            val stdout = redirectStdout {
                exitCode = trapSystemExitCall {
                    for (i in 1..numberOfLines) System.out.println("stdout: $i")
                    System.exit(42)
                }
            }

            exitCode shouldBe 42

            // The last printed line has a newline, resulting in a trailing blank line.
            val stdoutLines = stdout.lines().dropLast(1)
            stdoutLines.count() shouldBe numberOfLines
            stdoutLines.last() shouldBe "stdout: $numberOfLines"
        }
    }
})
