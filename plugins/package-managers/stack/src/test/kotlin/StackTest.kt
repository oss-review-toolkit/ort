/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.stack

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration

class StackTest : WordSpec({
    "transformVersion()" should {
        "return the transformed version" {
            val stack = Stack("Stack", tempdir(), AnalyzerConfiguration(), RepositoryConfiguration())

            mapOf(
                "Version 1.7.1 x86_64" to "1.7.1",
                "Version 1.7.1 x86_64" to "1.7.1",
                "Version 2.1.1, Git revision f612ea8 (7648 commits) x86_64 hpack-0.31.2" to "2.1.1"
            ).forAll { (input, expectedVersion) ->
                stack.transformVersion(input) shouldBe expectedVersion
            }
        }
    }
})
