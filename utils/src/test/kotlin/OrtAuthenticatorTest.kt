/*
 * Copyright (C) 2020 Bosch.IO GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package org.ossreviewtoolkit.utils

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class OrtAuthenticatorTest : WordSpec({
    "getNetrcAuthentication()" should {
        "correctly parse single-line contents" {
            val authentication = getNetrcAuthentication("""
                machine github.com login foo password bar
            """.trimIndent(), "github.com")

            authentication shouldNotBe null
            authentication!!.userName shouldBe "foo"
            authentication.password shouldBe "bar".toCharArray()
        }

        "correctly parse multi-line contents" {
            val authentication = getNetrcAuthentication("""
                machine gitlab.com
                login foo
                password bar
            """.trimIndent(), "gitlab.com")

            authentication shouldNotBe null
            authentication!!.userName shouldBe "foo"
            authentication.password shouldBe "bar".toCharArray()
        }

        "recognize the default machine" {
            val authentication = getNetrcAuthentication("""
                machine github.com
                login git
                password hub
                default login foo password bar
            """.trimIndent(), "gitlab.com")

            authentication shouldNotBe null
            authentication!!.userName shouldBe "foo"
            authentication.password shouldBe "bar".toCharArray()
        }

        "ignore superfluous statements" {
            val authentication = getNetrcAuthentication("""
                machine "# A funky way to add comments."
                login funkyLogin
                password funkyPassword
                machine bitbucket.com
                # This is a port.
                port 433
                password bar
                login foo
            """.trimIndent(), "bitbucket.com")

            authentication shouldNotBe null
            authentication!!.userName shouldBe "foo"
            authentication.password shouldBe "bar".toCharArray()
        }

        "ignore irrelavant machines" {
            val authentication = getNetrcAuthentication("""
                machine bitbucket.com login foo password bar
            """.trimIndent(), "github.com")

            authentication shouldBe null
        }
    }
})
