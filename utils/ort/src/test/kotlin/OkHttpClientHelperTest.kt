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

package org.ossreviewtoolkit.utils.ort

import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic

import java.io.IOException
import java.time.Duration

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody

import okio.BufferedSource

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

    "Exceptions during file download should be handled" {
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

    "Exceptions when accessing the response body should be handled" {
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
})
