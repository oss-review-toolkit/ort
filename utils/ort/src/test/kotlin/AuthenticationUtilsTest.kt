/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify

import java.net.Authenticator
import java.net.PasswordAuthentication
import java.net.URI

class AuthenticationUtilsTest : WordSpec({
    afterEach {
        unmockkAll()
    }

    "requestPasswordAuthentication" should {
        "return a correct authentication object for a host, port, and scheme combination" {
            val host = "www.example.org"
            val port = 442
            val scheme = "https"

            mockkObject(OrtAuthenticator)
            mockkObject(OrtProxySelector)
            mockkStatic(Authenticator::class)

            val passwordAuth = mockk<PasswordAuthentication>()

            every {
                Authenticator.requestPasswordAuthentication(
                    host,
                    null,
                    port,
                    scheme,
                    null,
                    null,
                    null,
                    Authenticator.RequestorType.SERVER
                )
            } returns passwordAuth

            requestPasswordAuthentication(host, port, scheme) shouldBe passwordAuth

            verify {
                OrtAuthenticator.install()
                OrtProxySelector.install()
            }
        }

        "return a correct authentication object for a URL" {
            val url = URI.create("https://www.example.org:442/auth/test")

            mockkObject(OrtAuthenticator)
            mockkObject(OrtProxySelector)
            mockkStatic(Authenticator::class)

            val passwordAuth = mockk<PasswordAuthentication>()

            every {
                Authenticator.requestPasswordAuthentication(
                    "www.example.org",
                    null,
                    442,
                    "https",
                    null,
                    null,
                    url.toURL(),
                    Authenticator.RequestorType.SERVER
                )
            } returns passwordAuth

            requestPasswordAuthentication(url) shouldBe passwordAuth

            verify {
                OrtAuthenticator.install()
                OrtProxySelector.install()
            }
        }

        "return the credentials present in the URL if any" {
            val host = "www.example.org"
            val port = 442
            val scheme = "https"
            val url = URI("https://foo:bar@www.example.org").toURL()

            val auth = OrtAuthenticator().requestPasswordAuthenticationInstance(
                host,
                null,
                port,
                null,
                null,
                scheme,
                url,
                Authenticator.RequestorType.SERVER
            )

            auth shouldNotBeNull {
                userName shouldBe "foo"
                String(password) shouldBe "bar"
            }
        }
    }
})
