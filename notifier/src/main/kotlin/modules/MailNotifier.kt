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

package org.ossreviewtoolkit.notifier.modules

import java.nio.charset.Charset

import org.apache.commons.mail.DefaultAuthenticator
import org.apache.commons.mail.HtmlEmail

import org.ossreviewtoolkit.model.config.SendMailConfiguration

/**
 * Notification module that provides a configured email client.
 */
class MailNotifier(config: SendMailConfiguration) {
    private val client: HtmlEmail = HtmlEmail()

    init {
        client.hostName = config.hostName
        client.setSmtpPort(config.port)
        client.setAuthenticator(DefaultAuthenticator(config.username, config.password))
        client.isSSLOnConnect = config.useSsl
        client.setFrom(config.fromAddress)
    }

    @Suppress("UNUSED") // This is intended to be used by notification script implementations.
    fun sendMail(
        subject: String,
        message: String,
        htmlEmail: Boolean = false,
        charset: Charset = Charsets.UTF_8,
        vararg receivers: String
    ) {
        client.subject = subject
        client.setMsg(message)
        client.setCharset(charset.name())
        client.addTo(*receivers)

        if (htmlEmail) client.setHtmlMsg(message) else client.setTextMsg(message)

        client.send()
    }
}
