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

package org.ossreviewtoolkit.model.config

data class SendMailConfiguration(
    /**
     * The address of the outgoing SMTP server that will be used to send the message.
     */
    val hostName: String = "localhost",

    /**
     * The port used for the SMTP server.
     */
    val port: Int = 465,

    /**
     * The username to authenticate with the SMTP server.
     */
    val username: String = "",

    /**
     * The password to authenticate with the SMTP server.
     */
    val password: String = "",

    /**
     * Configuration if SSL/TLS encryption should be enabled with the SMTP server.
     */
    val useSsl: Boolean = true,

    /**
     * The 'from' field of the outgoing email.
     */
    val fromAddress: String = "no-reply@oss-review-toolkit.org"
)
