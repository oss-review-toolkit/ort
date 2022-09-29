/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.scanner.scanners.fossid

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockkStatic

import java.net.PasswordAuthentication
import java.net.URI

import org.ossreviewtoolkit.utils.ort.requestPasswordAuthentication

class FossIdUrlProviderTest : StringSpec({
    "URLs are not changed if no credentials need to be added" {
        val urlProvider = FossIdUrlProvider.create(addAuthenticationToUrl = false)

        urlProvider.getUrl(REPO_URL) shouldBe REPO_URL
    }

    "Credentials should be added to URLs" {
        mockAuthenticator()
        val expectedUrl = "https://$USER:$PASSWORD@$HOST:$PORT/$PATH"
        val urlProvider = FossIdUrlProvider.create(addAuthenticationToUrl = true)

        val url = urlProvider.getUrl(REPO_URL)

        url shouldBe expectedUrl
    }

    "Missing credentials should be handled" {
        mockAuthenticator(authentication = null)
        val urlProvider = FossIdUrlProvider.create(addAuthenticationToUrl = true)

        val url = urlProvider.getUrl(REPO_URL)

        url shouldBe REPO_URL
    }
})

private const val HOST = "repo.example.org"
private const val PORT = 4711
private const val PATH = "tests/fossid.git"
private const val REPO_URL = "https://$HOST:$PORT/$PATH"
private const val USER = "scott"
private const val PASSWORD = "tiger"
private val AUTHENTICATION = PasswordAuthentication(USER, PASSWORD.toCharArray())

/**
 * Mock a request for authentication for the given [url] to return the specified [authentication].
 */
private fun mockAuthenticator(url: String = REPO_URL, authentication: PasswordAuthentication? = AUTHENTICATION) {
    mockkStatic("org.ossreviewtoolkit.utils.ort.UtilsKt")
    every { requestPasswordAuthentication(URI(url)) } returns authentication
}
