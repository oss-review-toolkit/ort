/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll

import java.io.IOException
import java.time.Duration

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody

import okio.BufferedSource

class OkHttpClientHelperTest : WordSpec({
    afterTest {
        unmockkAll()
    }

    "buildClient()" should {
        "return the same client instance when no configuration is specified" {
            val clientA = OkHttpClientHelper.buildClient()
            val clientB = OkHttpClientHelper.buildClient()

            clientA shouldBeSameInstanceAs clientB
        }

        "return the same client instance when specifying the same configuration instance" {
            val timeout: BuilderConfiguration = { readTimeout(Duration.ofSeconds(100)) }
            val clientA = OkHttpClientHelper.buildClient(timeout)
            val clientB = OkHttpClientHelper.buildClient(timeout)

            clientA shouldBeSameInstanceAs clientB
        }

        "return different clients when specifying different configurations" {
            val timeoutA: BuilderConfiguration = { readTimeout(Duration.ofSeconds(100)) }
            val timeoutB: BuilderConfiguration = { readTimeout(Duration.ofSeconds(500)) }
            val clientA = OkHttpClientHelper.buildClient(timeoutA)
            val clientB = OkHttpClientHelper.buildClient(timeoutB)

            clientA shouldNotBeSameInstanceAs clientB
        }

        "return different clients when specifying the same configuration using different instances" {
            val timeoutA: BuilderConfiguration = { readTimeout(Duration.ofSeconds(100)) }
            val timeoutB: BuilderConfiguration = { readTimeout(Duration.ofSeconds(100)) }
            val clientA = OkHttpClientHelper.buildClient(timeoutA)
            val clientB = OkHttpClientHelper.buildClient(timeoutB)

            clientA shouldNotBeSameInstanceAs clientB
        }
    }

    "downloadFile()" should {
        "handle exceptions during file download" {
            val outputDirectory = tempdir()
            val invalidUrl = "https://example.org/folder/"

            mockkStatic(OkHttpClient::download)
            val response = mockk<Response>(relaxed = true)
            val request = mockk<Request>(relaxed = true)
            val body = mockk<ResponseBody>(relaxed = true)
            val source = mockk<BufferedSource>(relaxed = true)

            every { request.url } returns invalidUrl.toHttpUrl()
            every { response.headers(any()) } returns emptyList()
            every { response.request } returns request
            every { body.source() } returns source
            every { source.read(any(), any()) } returnsMany listOf(100, -1)

            val client = OkHttpClientHelper.buildClient()
            every { client.download(any(), any()) } returns Result.success(response to body)

            val result = client.downloadFile(invalidUrl, outputDirectory)
            result.shouldBeFailure {
                it should beInstanceOf<IOException>()
            }
        }
    }

    "downloadText()" should {
        "handle exceptions when accessing the response body" {
            val failureUrl = "https://example.org/fault/"
            val exception = IOException("Connection closed")

            mockkStatic(OkHttpClient::download)
            val response = mockk<Response>(relaxed = true)
            val request = mockk<Request>(relaxed = true)
            val body = mockk<ResponseBody>(relaxed = true)

            every { request.url } returns failureUrl.toHttpUrl()
            every { response.headers(any()) } returns emptyList()
            every { response.request } returns request
            every { body.string() } throws exception

            val client = OkHttpClientHelper.buildClient()
            every { client.download(any(), any()) } returns Result.success(response to body)

            val result = client.downloadText(failureUrl)
            result.shouldBeFailure {
                it shouldBe exception
            }
        }
    }
})
