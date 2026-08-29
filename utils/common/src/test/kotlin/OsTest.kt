/*
 * Copyright (C) 2017 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.file.aFile
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.utils.common.Os.getPathFromEnvironment

class OsTest : WordSpec({
    "getPathFromEnvironment()" should {
        "return the path to system executables on Windows".config(enabled = Os.isWindows) {
            getPathFromEnvironment("winver") shouldNotBeNull {
                this shouldBe aFile()
                name shouldBe "winver.exe"
            }

            getPathFromEnvironment("winver.exe") shouldNotBeNull {
                this shouldBe aFile()
                name shouldBe "winver.exe"
            }
        }

        "return null for special names on Windows".config(enabled = Os.isWindows) {
            getPathFromEnvironment("") should beNull()
            getPathFromEnvironment("*") should beNull()
            getPathFromEnvironment("nul") should beNull()
        }

        "return the path to system executables on non-Windows".config(enabled = !Os.isWindows) {
            getPathFromEnvironment("chown") shouldNotBeNull {
                this shouldBe aFile()
                name shouldBe "chown"
            }

            getPathFromEnvironment("env") shouldNotBeNull {
                this shouldBe aFile()
                name shouldBe "env"
            }
        }

        "return null for special names on non-Windows".config(enabled = !Os.isWindows) {
            getPathFromEnvironment("") should beNull()
            getPathFromEnvironment("/") should beNull()
        }
    }
})
