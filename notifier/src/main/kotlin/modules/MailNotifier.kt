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

package org.ossreviewtoolkit.notifier.modules

import java.nio.charset.Charset

import org.apache.commons.mail.DefaultAuthenticator
import org.apache.commons.mail.HtmlEmail

import org.ossreviewtoolkit.model.config.SendMailConfiguration

/**
 * Notification module that provides a configured email client.
 */
class MailNotifier(private val config: SendMailConfiguration) {
    /**
     * Send an HTML email with the given [subject], [message] and [charset] encoding to all the [receivers]. If
     * [htmlEmail] is set to false, the mail is still sent as HTML, but the [message] is interpreted as plain-text.
     */
    @Suppress("UNUSED") // This is intended to be used by notification script implementations.
    fun sendMail(
        subject: String,
        message: String,
        htmlEmail: Boolean = false,
        charset: Charset = Charsets.UTF_8,
        vararg receivers: String
    ) {
        val email = HtmlEmail().apply {
            hostName = config.hostName
            setSmtpPort(config.port)
            setAuthenticator(DefaultAuthenticator(config.username, config.password))
            isSSLOnConnect = config.useSsl
            setFrom(config.fromAddress)

            setSubject(subject)
            setMsg(message)
            setCharset(charset.name())
            addTo(*receivers)
        }

        if (htmlEmail) email.setHtmlMsg(message) else email.setTextMsg(message)

        email.send()
    }
}
