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

package org.ossreviewtoolkit.downloader.vcs

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify

import java.net.Authenticator
import java.net.PasswordAuthentication
import java.net.URL

import org.eclipse.jgit.errors.UnsupportedCredentialItem
import org.eclipse.jgit.transport.CredentialItem
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.URIish

class GitTest : WordSpec({
    var originalCredentialsProvider: CredentialsProvider? = null
    var originalAuthenticator: Authenticator? = null

    beforeSpec {
        originalCredentialsProvider = CredentialsProvider.getDefault()
        originalAuthenticator = Authenticator.getDefault()

        Git() // Make sure that the initialization logic runs.
    }

    afterSpec {
        Authenticator.setDefault(originalAuthenticator)
        CredentialsProvider.setDefault(originalCredentialsProvider)
    }

    "The CredentialsProvider" should {
        "default to the AuthenticatorCredentialsProvider" {
            CredentialsProvider.getDefault() shouldBe AuthenticatorCredentialsProvider
        }

        "support user name and password credentials" {
            val userCredential = CredentialItem.Username()
            val passwordCredential = CredentialItem.Password()

            val credentialProvider = CredentialsProvider.getDefault()

            credentialProvider.supports(userCredential, passwordCredential) shouldBe true
        }

        "not support any other types of credentials" {
            val userCredential = CredentialItem.Username()
            val passwordCredential = CredentialItem.Password()
            val otherCredential = mockk<CredentialItem.StringType>()

            val credentialProvider = CredentialsProvider.getDefault()

            credentialProvider.supports(userCredential, passwordCredential, otherCredential) shouldBe false
        }

        "delegate to the Authenticator" {
            val user = "scott"
            val password = "tiger".toCharArray()
            val userCredential = mockk<CredentialItem.Username>()
            val passwordCredential = mockk<CredentialItem.Password>()
            every { userCredential.value = any() } just runs
            every { passwordCredential.value = any() } just runs

            val authenticator = object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    requestingHost shouldBe TestUri.host
                    requestingPort shouldBe TestUri.port
                    requestingProtocol shouldBe TestUri.scheme

                    return PasswordAuthentication(user, password)
                }
            }

            Authenticator.setDefault(authenticator)

            val credentialProvider = CredentialsProvider.getDefault()

            credentialProvider.get(TestUri, userCredential, passwordCredential) shouldBe true

            verify {
                userCredential.value = user
                passwordCredential.value = password
            }
        }

        "handle unknown credentials" {
            val userCredential = CredentialItem.Username()
            val passwordCredential = CredentialItem.Password()

            val authenticator = object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication? = null
            }

            Authenticator.setDefault(authenticator)

            val credentialProvider = CredentialsProvider.getDefault()

            credentialProvider.get(TestUri, userCredential, passwordCredential) shouldBe false
        }

        "throw for unsupported credential types" {
            val otherCredential = mockk<CredentialItem.StringType>(relaxed = true)

            val authenticator = object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication =
                    PasswordAuthentication("someUser", "somePassword".toCharArray())
            }

            Authenticator.setDefault(authenticator)

            val credentialProvider = CredentialsProvider.getDefault()

            shouldThrow<UnsupportedCredentialItem> {
                credentialProvider.get(TestUri, otherCredential)
            }
        }

        "should not be interactive" {
            val credentialProvider = CredentialsProvider.getDefault()

            credentialProvider.isInteractive shouldBe false
        }
    }
})

private val TestUri = URIish(URL("https://www.example.org:8080/foo"))
