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

package org.ossreviewtoolkit.plugins.scanners.fossid

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll

import java.net.PasswordAuthentication
import java.net.URI

import org.ossreviewtoolkit.utils.common.replaceCredentialsInUri
import org.ossreviewtoolkit.utils.ort.requestPasswordAuthentication

class FossIdUrlProviderTest : StringSpec({
    afterTest {
        unmockkAll()
    }

    "URLs are not changed if no mapping is provided" {
        val urlProvider = FossIdUrlProvider.create()

        urlProvider.getUrl(REPO_URL) shouldBe REPO_URL
    }

    "Credentials should be added to URLs" {
        val repoUrl = "https://$HOST/$PATH"
        mockAuthenticator(port = -1)
        val expectedUrl = "https://$URL_USER:$URL_PASSWORD@$HOST/$PATH"
        val urlProvider = FossIdUrlProvider.create(listOf(ADD_CREDENTIALS_MAPPING))

        val url = urlProvider.getUrl(repoUrl)

        url shouldBe expectedUrl
    }

    "Missing credentials should be handled" {
        mockAuthenticator(authentication = null)
        val urlProvider = FossIdUrlProvider.create(listOf(ADD_CREDENTIALS_MAPPING))

        val url = urlProvider.getUrl(REPO_URL)

        url shouldBe REPO_URL
    }

    "URL mapping should be applied" {
        val otherHost = "mapped.example.org"
        val otherPort = 8765
        val otherScheme = "ssh"
        val regex = "https://$HOST:(\\d+)(?<path>.*)"
        val replace = "$otherScheme://$otherHost:$otherPort\${path}"
        val urlMapping = listOf("$regex -> $replace", "foo  ->  bar", ADD_CREDENTIALS_MAPPING)

        val urlProvider = FossIdUrlProvider.create(urlMapping)
        val url = urlProvider.getUrl(REPO_URL)

        url shouldBe "$otherScheme://$otherHost:$otherPort/$PATH"
    }

    "URL mapping with credentials should be applied" {
        val otherHost = "mapped-auth.example.org"
        val regex = "(?<scheme>)://$HOST:(?<port>\\d+)(?<path>.*)"
        val replace = "\${scheme}://#username:#password@$otherHost:\${port}\${path}"
        val expectedUrl = "https://$URL_USER:$URL_PASSWORD@$otherHost:$PORT/$PATH"
        val urlMapping = listOf("$regex  ->  $replace", "foo -> bar")
        mockAuthenticator(host = otherHost)

        val urlProvider = FossIdUrlProvider.create(urlMapping)
        val url = urlProvider.getUrl(REPO_URL)

        url shouldBe expectedUrl
    }

    "URL encoding should be applied for credentials" {
        val username = "test/user"
        val password = "se#@et?boh"
        val auth = PasswordAuthentication(username, password.toCharArray())
        val expectedUrl = REPO_URL.replaceCredentialsInUri("$username:$password")
        val urlMapping = listOf(ADD_CREDENTIALS_MAPPING)
        mockAuthenticator(authentication = auth)

        val urlProvider = FossIdUrlProvider.create(urlMapping)
        val url = urlProvider.getUrl(REPO_URL)

        url shouldBe expectedUrl
    }

    "Invalid URL mappings are ignored" {
        val invalidMapping = ADD_CREDENTIALS_MAPPING.replace("->", "=>")
        val urlMapping = listOf(invalidMapping)

        val urlProvider = FossIdUrlProvider.create(urlMapping)
        val url = urlProvider.getUrl(REPO_URL)

        url shouldBe REPO_URL
    }
})

private const val HOST = "repo.example.org"
private const val PORT = 4711
private const val PATH = "tests/fossid.git"
private const val REPO_URL = "https://$HOST:$PORT/$PATH"
private const val URL_USER = "scott"
private const val URL_PASSWORD = "tiger"
private val AUTHENTICATION = PasswordAuthentication(URL_USER, URL_PASSWORD.toCharArray())

/** A URL mapping that adds credentials to arbitrary URLs. */
private const val ADD_CREDENTIALS_MAPPING = "(?<scheme>)://(?<host>)(?<port>:\\d+)?(?<path>.*) -> " +
    "\${scheme}://#username:#password@\${host}\${port}\${path}"

/**
 * Mock a request for authentication for the given [host] and [port] to return the specified
 * [authentication].
 */
private fun mockAuthenticator(
    host: String = HOST,
    port: Int = PORT,
    authentication: PasswordAuthentication? = AUTHENTICATION
) {
    mockkStatic("org.ossreviewtoolkit.utils.ort.AuthenticationUtilsKt")
    every { requestPasswordAuthentication(any()) } answers {
        val uri = firstArg<URI>()
        authentication.takeIf { uri.host == host && uri.port == port && uri.scheme == "https" && uri.userInfo == null }
    }
}
