/*
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

package org.ossreviewtoolkit.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.types.shouldBeSameInstanceAs

import java.time.Duration

class OkHttpClientHelperTest : StringSpec({
    "Passing no lambda blocks should return the same client" {
        val clientA = OkHttpClientHelper.buildClient()
        val clientB = OkHttpClientHelper.buildClient()

        clientA shouldBeSameInstanceAs clientB
    }

    "Passing the same lambda blocks should return the same client" {
        val timeout: BuilderConfiguration = { readTimeout(Duration.ofSeconds(100)) }
        val clientA = OkHttpClientHelper.buildClient(timeout)
        val clientB = OkHttpClientHelper.buildClient(timeout)

        clientA shouldBeSameInstanceAs clientB
    }
})
