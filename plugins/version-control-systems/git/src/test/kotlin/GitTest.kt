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

package org.ossreviewtoolkit.plugins.versioncontrolsystems.git

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beEmpty

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify

import java.net.Authenticator
import java.net.PasswordAuthentication
import java.net.URI

import org.eclipse.jgit.errors.UnsupportedCredentialItem
import org.eclipse.jgit.transport.CredentialItem
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.URIish

import org.ossreviewtoolkit.plugins.api.PluginConfig
import org.ossreviewtoolkit.utils.ort.requestPasswordAuthentication

class GitTest : WordSpec({
    // Make sure that the initialization logic runs.
    val git = GitFactory().create(PluginConfig.EMPTY)

    var originalCredentialsProvider: CredentialsProvider? = null
    var originalAuthenticator: Authenticator? = null

    beforeSpec {
        originalCredentialsProvider = CredentialsProvider.getDefault()
        originalAuthenticator = Authenticator.getDefault()
    }

    afterSpec {
        Authenticator.setDefault(originalAuthenticator)
        CredentialsProvider.setDefault(originalCredentialsProvider)
    }

    afterTest {
        unmockkAll()
    }

    "Git" should {
        "be able to get the version" {
            val version = git.getVersion()

            version shouldNot beEmpty()
        }

        "detect URLs to remote repositories" {
            git.isApplicableUrl("https://bitbucket.org/yevster/spdxtraxample.git") shouldBe true
            git.isApplicableUrl("https://hg.sr.ht/~duangle/paniq_legacy") shouldBe false
        }
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
            mockAuthentication(PasswordAuthentication(user, password))

            val credentialProvider = CredentialsProvider.getDefault()

            credentialProvider.get(testUri, userCredential, passwordCredential) shouldBe true

            verify {
                userCredential.value = user
                passwordCredential.value = password
            }
        }

        "handle an invalid URL" {
            val user = "scott"
            val password = "tiger".toCharArray()
            val userCredential = mockk<CredentialItem.Username>()
            val passwordCredential = mockk<CredentialItem.Password>()
            val invalidUri = URIish("foo")

            every { userCredential.value = any() } just runs
            every { passwordCredential.value = any() } just runs
            mockAuthentication(null)
            every {
                requestPasswordAuthentication("", invalidUri.port, "", null)
            } returns PasswordAuthentication(user, password)

            val credentialProvider = CredentialsProvider.getDefault()

            credentialProvider.get(invalidUri, userCredential, passwordCredential) shouldBe true

            verify {
                userCredential.value = user
                passwordCredential.value = password
            }
        }

        "handle unknown credentials" {
            val userCredential = CredentialItem.Username()
            val passwordCredential = CredentialItem.Password()

            mockAuthentication(null)

            val credentialProvider = CredentialsProvider.getDefault()

            credentialProvider.get(testUri, userCredential, passwordCredential) shouldBe false
        }

        "throw for unsupported credential types" {
            val otherCredential = mockk<CredentialItem.StringType>(relaxed = true)

            mockAuthentication(PasswordAuthentication("someUser", "somePassword".toCharArray()))

            val credentialProvider = CredentialsProvider.getDefault()

            shouldThrow<UnsupportedCredentialItem> {
                credentialProvider.get(testUri, otherCredential)
            }
        }

        "should not be interactive" {
            val credentialProvider = CredentialsProvider.getDefault()

            credentialProvider.isInteractive shouldBe false
        }
    }
})

private val testUri = URIish(URI("https://www.example.org:8080/foo").toURL())
private val testUriAsUrl = URI.create(testUri.toString()).toURL()

/**
 * Mocks the utility function to query password authentication for the test URI. Return the [result] provided.
 */
private fun mockAuthentication(result: PasswordAuthentication?) {
    mockkStatic("org.ossreviewtoolkit.utils.ort.AuthenticationUtilsKt")

    every {
        requestPasswordAuthentication(testUri.host, testUri.port, testUri.scheme, testUriAsUrl)
    } returns result
}
