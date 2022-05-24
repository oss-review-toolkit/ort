/*
 * Copyright (C) 2020-2021 Bosch.IO GmbH
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

import java.net.Authenticator
import java.net.PasswordAuthentication

import org.ossreviewtoolkit.utils.common.Os

/**
 * A simple non-caching authenticator that reads credentials from given environment variables with the names
 * [ORT_HTTP_USERNAME_ENV_NAME] and [ORT_HTTP_PASSWORD_ENV_NAME].
 */
class EnvVarAuthenticator(env: Map<String, String> = Os.env) : Authenticator() {
    companion object {
        const val ORT_HTTP_USERNAME_ENV_NAME = "ORT_HTTP_USERNAME"
        const val ORT_HTTP_PASSWORD_ENV_NAME = "ORT_HTTP_PASSWORD"
    }

    private val usernameFromEnv = env[ORT_HTTP_USERNAME_ENV_NAME]
    private val passwordFromEnv = env[ORT_HTTP_PASSWORD_ENV_NAME]

    private val auth = if (usernameFromEnv != null && passwordFromEnv != null) {
        PasswordAuthentication(usernameFromEnv, passwordFromEnv.toCharArray())
    } else {
        null
    }

    override fun getPasswordAuthentication(): PasswordAuthentication? =
        auth ?: super.getPasswordAuthentication()
}
