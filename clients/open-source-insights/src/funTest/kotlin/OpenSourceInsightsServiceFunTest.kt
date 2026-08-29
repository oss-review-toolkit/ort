/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.clients.opensourceinsights

import deps_dev.v3.System as PackageSystem

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import java.time.Instant

class OpenSourceInsightsServiceFunTest : WordSpec({
    val service by lazy { OpenSourceInsightsService() }

    "getVersion()" should {
        "return the publishing date for a known package" {
            val version = service.getVersion(PackageSystem.MAVEN, "org.apache.commons:commons-lang3", "3.12.0")

            version.published_at shouldBe Instant.parse("2021-02-26T20:40:51Z")
        }
    }
})
