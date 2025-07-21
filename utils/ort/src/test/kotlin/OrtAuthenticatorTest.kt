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

package org.ossreviewtoolkit.utils.ort

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import java.net.Authenticator
import java.net.PasswordAuthentication
import java.net.URI

class OrtAuthenticatorTest : StringSpec({
    "Credentials should be cached by default" {
        val host = "www.example.com"
        val url1 = "https://scott:tiger@$host/test"
        val url2 = "https://foo:bar@$host/test"

        val authenticator = OrtAuthenticator()
        authenticator.requestPasswordAuthentication(url1) shouldNot beNull()

        authenticator.requestPasswordAuthentication(url2) shouldNotBeNull {
            userName shouldBe "scott"
            String(password) shouldBe "tiger"
        }
    }

    "Caching of credentials can be disabled" {
        val host = "www.example.com"
        val url1 = "https://scott:tiger@$host/test"
        val url2 = "https://foo:bar@$host/test"

        val authenticator = OrtAuthenticator(cacheAuthentication = false)
        authenticator.requestPasswordAuthentication(url1) shouldNot beNull()

        authenticator.requestPasswordAuthentication(url2) shouldNotBeNull {
            userName shouldBe "foo"
            String(password) shouldBe "bar"
        }
    }
})

/**
 * Query this [Authenticator] instance for credentials information for the given [url].
 */
private fun Authenticator.requestPasswordAuthentication(url: String): PasswordAuthentication? {
    val requestUrl = URI.create(url).toURL()
    return requestPasswordAuthenticationInstance(
        requestUrl.host,
        null,
        443,
        requestUrl.protocol,
        null,
        null,
        requestUrl,
        Authenticator.RequestorType.SERVER
    )
}
