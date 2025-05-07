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

import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.MessagingException
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart

import java.nio.charset.Charset
import java.util.Properties

import org.ossreviewtoolkit.model.config.SendMailConfiguration

/**
 * Notification module that provides a configured email client.
 */
class MailNotifier(private val config: SendMailConfiguration) {
    private val props = Properties().apply {
        put("mail.smtp.host", config.hostName)
        put("mail.smtp.port", config.port.toString())
        put("mail.smtp.starttls.enable", config.useSsl)
        put("mail.smtp.auth", "true")
    }

    private val session = Session.getInstance(
        props,
        object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(config.username, config.password)
        }
    )

    /**
     * Send an email with the given [subject], [message] and [charset] encoding to all the [receivers]. If [htmlEmail]
     * is set to false, a single-part plain-text email is sent. Otherwise, a multi-part HTML email with an additional
     * plain-text part as a fallback is sent. Throws a [MessagingException] if the email could not be sent.
     */
    @Suppress("unused") // This is intended to be used by notification script implementations.
    fun sendMail(
        subject: String,
        message: String,
        htmlEmail: Boolean = false,
        charset: Charset = Charsets.UTF_8,
        vararg receivers: String
    ) {
        val email = MimeMessage(session).apply {
            setFrom(InternetAddress(config.fromAddress))
            setRecipients(Message.RecipientType.TO, receivers.joinToString())
            setSubject(subject)

            if (htmlEmail) {
                val htmlBody = MimeBodyPart().apply {
                    setText(message, charset.name(), "html")
                }

                val textBody = MimeBodyPart().apply {
                    setText(message, charset.name(), "plain")
                }

                setContent(MimeMultipart(htmlBody, textBody))
            } else {
                setText(message, charset.name())
            }
        }

        Transport.send(email)
    }
}
