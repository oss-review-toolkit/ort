/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2021 Bosch.IO GmbH
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
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import java.io.File

import org.ossreviewtoolkit.utils.common.Os.getPathFromEnvironment
import org.ossreviewtoolkit.utils.test.shouldNotBeNull

class OsTest : WordSpec({
    "getPathFromEnvironment" should {
        "find system executables on Windows".config(enabled = Os.isWindows) {
            val winverPath = File(Os.env["SYSTEMROOT"], "system32/winver.exe")

            getPathFromEnvironment("winver") shouldNot beNull()
            getPathFromEnvironment("winver") shouldBe winverPath

            getPathFromEnvironment("winver.exe") shouldNot beNull()
            getPathFromEnvironment("winver.exe") shouldBe winverPath

            getPathFromEnvironment("") should beNull()
            getPathFromEnvironment("*") should beNull()
            getPathFromEnvironment("nul") should beNull()
        }

        "find system executables on non-Windows".config(enabled = !Os.isWindows) {
            getPathFromEnvironment("sh") shouldNotBeNull {
                toString() shouldBeIn listOf("/bin/sh", "/usr/bin/sh")
            }

            getPathFromEnvironment("") should beNull()
            getPathFromEnvironment("/") should beNull()
        }
    }
})
