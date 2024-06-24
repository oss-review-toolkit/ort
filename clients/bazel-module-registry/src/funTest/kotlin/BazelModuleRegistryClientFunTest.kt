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

package org.ossreviewtoolkit.clients.bazelmoduleregistry

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

class BazelModuleRegistryClientFunTest : WordSpec({
    val client = RemoteBazelModuleRegistryService.create()
    val repoUrl = "https://github.com/google/glog"

    "getModuleMetadata" should {
        "include a homepage URL and version 0.5.0 for the 'glog' module" {
            val metadata = client.getModuleMetadata("glog")

            metadata.homepage.toString() shouldBe repoUrl
            metadata.versions.size shouldBeGreaterThan 1
            metadata.versions shouldContain "0.5.0"
        }
    }

    "getModuleSource" should {
        "include URL and hash data" {
            val sourceInfo = client.getModuleSourceInfo("glog", "0.5.0")

            sourceInfo.url.toString() shouldBe "$repoUrl/archive/refs/tags/v0.5.0.tar.gz"
            sourceInfo.integrity shouldBe "sha256-7t5x8oNxvzmqabRd4jsynTchQBbiBVJps7Xnz9QLWfU="
        }
    }
})
