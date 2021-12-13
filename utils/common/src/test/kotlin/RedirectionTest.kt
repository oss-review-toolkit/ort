/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
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

package org.ossreviewtoolkit.utils.common

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.extensions.system.SpecSystemExitListener
import io.kotest.extensions.system.SystemExitException
import io.kotest.matchers.shouldBe

import java.util.Scanner

class RedirectionTest : WordSpec({
    register(SpecSystemExitListener)

    "Redirecting output" should {
        // Use a relatively large number of lines that results in more than 64k to be written to test against the pipe
        // buffer limit on Linux, see https://unix.stackexchange.com/a/11954/53328.
        val numberOfLines = 10000

        "work for stdout only" {
            val stdout = redirectStdout {
                for (i in 1..numberOfLines) System.out.println("stdout: $i")
            }

            // The last printed line has a newline, resulting in a trailing blank line.
            val stdoutLines = stdout.lines().dropLast(1)
            stdoutLines.size shouldBe numberOfLines
            stdoutLines.last() shouldBe "stdout: $numberOfLines"
        }

        "work for stderr only" {
            val stderr = redirectStderr {
                for (i in 1..numberOfLines) System.err.println("stderr: $i")
            }

            // The last printed line has a newline, resulting in a trailing blank line.
            val stderrLines = stderr.lines().dropLast(1)
            stderrLines.size shouldBe numberOfLines
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
            stdoutLines.size shouldBe numberOfLines
            stdoutLines.last() shouldBe "stdout: $numberOfLines"

            // The last printed line has a newline, resulting in a trailing blank line.
            val stderrLines = stderr.lines().dropLast(1)
            stderrLines.size shouldBe numberOfLines
            stderrLines.last() shouldBe "stderr: $numberOfLines"
        }

        "work when trapping exit calls" {
            var e: SystemExitException? = null

            val stdout = redirectStdout {
                e = shouldThrow {
                    for (i in 1..numberOfLines) System.out.println("stdout: $i")
                    System.exit(42)
                }
            }

            e?.exitCode shouldBe 42

            // The last printed line has a newline, resulting in a trailing blank line.
            val stdoutLines = stdout.lines().dropLast(1)
            stdoutLines.size shouldBe numberOfLines
            stdoutLines.last() shouldBe "stdout: $numberOfLines"
        }
    }

    "Suppressing input" should {
        "avoid blocking for user input" {
            shouldThrow<NoSuchElementException> {
                suppressInput {
                    Scanner(System.`in`).use {
                        it.nextLine()
                    }
                }
            }
        }
    }
})
