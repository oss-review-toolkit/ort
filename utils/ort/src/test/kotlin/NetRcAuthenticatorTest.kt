/*
 * Copyright (C) 2020 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import io.kotest.core.TestConfiguration
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.maps.beEmpty
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.net.PasswordAuthentication

class NetRcAuthenticatorTest : WordSpec({
    "NetRcAuthenticator" should {
        "correctly parse single-line contents" {
            val authentication = getNetrcAuthentication(
                "machine github.com login foo password bar",
                "github.com"
            )

            authentication shouldNotBeNull {
                userName shouldBe "foo"
                password shouldBe "bar".toCharArray()
            }
        }

        "correctly parse multi-line contents" {
            val authentication = getNetrcAuthentication(
                """
                machine gitlab.com
                login foo
                password bar
                """.trimIndent(),
                "gitlab.com"
            )

            authentication shouldNotBeNull {
                userName shouldBe "foo"
                password shouldBe "bar".toCharArray()
            }
        }

        "recognize the default machine" {
            val authentication = getNetrcAuthentication(
                """
                machine github.com
                login git
                password hub
                default login foo password bar
                """.trimIndent(),
                "default"
            )

            authentication shouldNotBeNull {
                userName shouldBe "foo"
                password shouldBe "bar".toCharArray()
            }
        }

        "ignore superfluous statements" {
            val authentication = getNetrcAuthentication(
                """
                machine "# A funky way to add comments."
                login funkyLogin
                password funkyPassword
                machine bitbucket.com
                # This is a port.
                port 433
                password bar
                login foo
                """.trimIndent(),
                "bitbucket.com"
            )

            authentication shouldNotBeNull {
                userName shouldBe "foo"
                password shouldBe "bar".toCharArray()
            }
        }

        "ignore superfluous whitespace" {
            val authentication = getNetrcAuthentication(
                "machine github.com\t login \tfoo password bar \n",
                "github.com"
            )

            authentication shouldNotBeNull {
                userName shouldBe "foo"
                password shouldBe "bar".toCharArray()
            }
        }

        "ignore incomplete entries" {
            val credentials = parseNetrc("machine bitbucket.com login foo machine github.com password bar")

            credentials should beEmpty()
        }

        "return null for an unknown machine" {
            val authentication = getNetrcAuthentication("", "github.com")

            authentication should beNull()
        }
    }
})

/**
 * Invoke the authenticator under test for a .netrc file with the given [contents] and query it for the given
 * [machine].
 */
private fun TestConfiguration.getNetrcAuthentication(contents: String, machine: String): PasswordAuthentication? {
    val netrcFile = tempfile().apply { writeText(contents) }

    val authenticator = NetRcAuthenticator(listOf(netrcFile))

    return authenticator.requestPasswordAuthenticationInstance(
        machine,
        null,
        -1,
        null,
        null,
        null,
        null,
        null
    )
}
