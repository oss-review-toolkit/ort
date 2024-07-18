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

package org.ossreviewtoolkit.plugins.packagemanagers.bazel

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

class CompositeBazelModuleRegistryServiceTest : WordSpec({
    "URL_REGEX" should {
        "match the server's base url and the package name" {
            val expr1 = "https://raw.githubusercontent.com/bazelbuild/bazel-central-registry/main/modules/abseil-cpp/" +
                "20230125.1/source.json"
            val group1 = CompositeBazelModuleRegistryService.Companion.URL_REGEX.matchEntire(expr1)?.groups

            group1?.get("server")
                ?.value shouldBe "https://raw.githubusercontent.com/bazelbuild/bazel-central-registry/main/"
            group1?.get("package")?.value shouldBe "abseil-cpp"

            val expr2 = "https://bcr.bazel.build/modules/rules_proto/5.3.0-21.7/source.json"
            val group2 = CompositeBazelModuleRegistryService.Companion.URL_REGEX.matchEntire(expr2)?.groups

            group2?.get("server")?.value shouldBe "https://bcr.bazel.build/"
            group2?.get("package")?.value shouldBe "rules_proto"

            val expr3 = "https://bcr.bazel.build/modules/upb/0.0.0-20220923-a547704/source.json"
            val group3 = CompositeBazelModuleRegistryService.Companion.URL_REGEX.matchEntire(expr3)?.groups

            group3?.get("server")?.value shouldBe "https://bcr.bazel.build/"
            group3?.get("package")?.value shouldBe "upb"
        }
    }
})
