/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.system.withEnvironment
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class ProcessCaptureTest : StringSpec({
    "Environment variables should be passed correctly" {
        val env = mapOf("PREFIX" to "This is some path: ", "SOME_PATH" to "/foo/bar")
        val proc = if (Os.isWindows) {
            ProcessCapture("cmd.exe", "/c", "echo %PREFIX%%SOME_PATH%", environment = env)
        } else {
            ProcessCapture("sh", "-c", "echo \$PREFIX\$SOME_PATH", environment = env)
        }

        proc.exitValue shouldBe 0
        proc.stdout.trimEnd() shouldBe "This is some path: /foo/bar"
    }

    "Only allowed environment variables should be passed" {
        val env = mapOf("DB_USER" to "scott", "DB_PASSWORD" to "tiger", "DB_CONN" to "my-db.example.org")

        withEnvironment(env) {
            val proc = if (Os.isWindows) {
                ProcessCapture("cmd.exe", "/c", "echo %DB_USER%:%DB_PASSWORD%.%DB_CONN%")
            } else {
                ProcessCapture("sh", "-c", "echo \$DB_USER:\$DB_PASSWORD.\$DB_CONN")
            }

            proc.exitValue shouldBe 0
            proc.stdout.trimEnd() shouldBe if (Os.isWindows) {
                "%DB_USER%:%DB_PASSWORD%.my-db.example.org"
            } else {
                ":.my-db.example.org"
            }
        }
    }

    "Masked strings should be processed correctly" {
        val masked = MaskedString("echo unmasked")
        val proc = if (Os.isWindows) {
            ProcessCapture("cmd.exe", "/c", masked)
        } else {
            ProcessCapture("sh", "-c", masked)
        }

        proc.exitValue shouldBe 0
        proc.stdout.trimEnd() shouldBe "unmasked"

        proc.commandLine shouldContain MaskedString.DEFAULT_MASK
    }
})
