/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.commands.compare

import com.github.ajalt.clikt.testing.test

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.utils.common.extractResource

class CompareCommandFunTest : WordSpec({
    "The text diff method" should {
        "ignore time and environment differences in an analyzer result" {
            val a = extractResource("/semver4j-analyzer-result-linux.yml", tempfile(suffix = ".yml"))
            val b = extractResource("/semver4j-analyzer-result-windows.yml", tempfile(suffix = ".yml"))

            val result = CompareCommand().test(
                arrayOf(
                    "--method=TEXT_DIFF",
                    "--ignore-time",
                    "--ignore-environment",
                    a.path,
                    b.path
                )
            )

            withClue(result.stdout) {
                result.statusCode shouldBe 0
            }
        }
    }
})
