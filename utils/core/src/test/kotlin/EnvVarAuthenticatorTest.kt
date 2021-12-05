/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

package org.ossreviewtoolkit.utils.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.utils.core.EnvVarAuthenticator.Companion.ORT_HTTP_PASSWORD_ENV_NAME
import org.ossreviewtoolkit.utils.core.EnvVarAuthenticator.Companion.ORT_HTTP_USERNAME_ENV_NAME
import org.ossreviewtoolkit.utils.test.shouldNotBeNull

class EnvVarAuthenticatorTest : StringSpec({
    "Setting the username alone does not return authentication" {
        val auth = requestPasswordAuthentication(ORT_HTTP_USERNAME_ENV_NAME to "username")

        auth should beNull()
    }

    "Setting the password alone does not return authentication" {
        val auth = requestPasswordAuthentication(ORT_HTTP_PASSWORD_ENV_NAME to "password")

        auth should beNull()
    }

    "Setting the username and password returns authentication" {
        val auth = requestPasswordAuthentication(
            ORT_HTTP_USERNAME_ENV_NAME to "username",
            ORT_HTTP_PASSWORD_ENV_NAME to "password"
        )

        auth shouldNotBeNull {
            userName shouldBe "username"
            password shouldBe "password".toCharArray()
        }
    }
})

private fun requestPasswordAuthentication(vararg env: Pair<String, String>) =
    EnvVarAuthenticator(env.toMap()).requestPasswordAuthenticationInstance(
        /* host = */ null,
        /* addr = */ null,
        /* port = */ 0,
        /* protocol = */ null,
        /* prompt = */ null,
        /* scheme = */ null,
        /* url = */ null,
        /* reqType = */ null
    )
