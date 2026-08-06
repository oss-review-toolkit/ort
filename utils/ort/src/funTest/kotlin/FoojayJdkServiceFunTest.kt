/*
 * Copyright (C) 2024 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.utils.ort

import io.kotest.core.spec.style.WordSpec

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify

import okhttp3.OkHttpClient

import org.ossreviewtoolkit.clients.foojay.DiscoService

class FoojayJdkServiceFunTest : WordSpec({
    "FoojayJdkService.create()" should {
        "pass no URL to the Disco service if none is configured" {
            mockkObject(DiscoService.Companion) {
                every { DiscoService.create(any(), any<OkHttpClient>()) } returns mockk()

                FoojayJdkService.create()

                verify(exactly = 1) { DiscoService.create(null, any<OkHttpClient>()) }
            }
        }

        "pass the configured URL to the Disco service" {
            val customUrl = "https://custom.foojay.example.com/disco/"

            mockkObject(DiscoService.Companion) {
                every { DiscoService.create(any(), any<OkHttpClient>()) } returns mockk()

                FoojayJdkService.create(customUrl)

                verify(exactly = 1) { DiscoService.create(customUrl, any<OkHttpClient>()) }
            }
        }
    }
})
