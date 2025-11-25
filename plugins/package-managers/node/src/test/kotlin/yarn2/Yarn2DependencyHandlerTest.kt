/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.node.yarn2

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

class Yarn2DependencyHandlerTest : WordSpec({
    "Locator.parse()" should {
        "work for patched packages" {
            val locator = Locator.parse(
                "resolve@patch:resolve@npm%3A1.22.8#optional!builtin<compat/resolve>::version=1.22.8&hash=c3c19d"
            )

            locator.moduleName shouldBe "resolve"
            locator.remainder shouldBe
                "patch:resolve@npm%3A1.22.8#optional!builtin<compat/resolve>::version=1.22.8&hash=c3c19d"
        }

        "work for a package with a scope" {
            val locator = Locator.parse("@failing/package-with-lightningcss@workspace:packages/spark")

            locator.moduleName shouldBe "@failing/package-with-lightningcss"
            locator.remainder shouldBe "workspace:packages/spark"
        }
    }
})
