/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.GreenMailUtil
import com.icegreen.greenmail.util.ServerSetup

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.config.SendMailConfiguration

class MailNotifierTest : WordSpec({
    val greenMail = GreenMail(ServerSetup.SMTP.dynamicPort()).apply {
        setUser("no-reply@oss-review-toolkit.org", "no-reply@oss-review-toolkit.org", "pwd")
    }

    lateinit var config: SendMailConfiguration
    lateinit var notifier: MailNotifier

    beforeSpec {
        greenMail.start()

        config = SendMailConfiguration(
            hostName = "localhost",
            port = greenMail.smtp.serverSetup.port,
            username = "no-reply@oss-review-toolkit.org",
            password = "pwd",
            useSsl = false,
            fromAddress = "no-reply@oss-review-toolkit.org"
        )
        notifier = MailNotifier(config)
    }

    afterSpec {
        greenMail.stop()
    }

    "sendMail()" should {
        "succeed for plain-text messages" {
            notifier.sendMail(
                subject = "ORT notifier subject",
                message = "<strong>ORT notifier message</strong>",
                htmlEmail = false,
                receivers = arrayOf("no-reply@oss-review-toolkit.org")
            )

            greenMail.waitForIncomingEmail(1000, 1) shouldBe true
            greenMail.receivedMessages shouldHaveSize 1
            with(greenMail.receivedMessages.first()) {
                val headerLines = GreenMailUtil.getHeaders(this).lines()
                headerLines shouldContain "From: no-reply@oss-review-toolkit.org"
                headerLines shouldContain "To: no-reply@oss-review-toolkit.org"
                headerLines shouldContain "Subject: ORT notifier subject"
                headerLines shouldContain "Content-Type: text/plain; charset=UTF-8"

                val bodyLines = GreenMailUtil.getBody(this).lines()
                bodyLines shouldContain "<strong>ORT notifier message</strong>"
            }
        }

        "succeed for HTML messages" {
            notifier.sendMail(
                subject = "ORT notifier subject",
                message = "<strong>ORT notifier message</strong>",
                htmlEmail = true,
                receivers = arrayOf("no-reply@oss-review-toolkit.org")
            )

            greenMail.waitForIncomingEmail(1000, 1) shouldBe true
            greenMail.receivedMessages shouldHaveSize 2
            with(greenMail.receivedMessages.last()) {
                val headerLines = GreenMailUtil.getHeaders(this).lines()
                headerLines shouldContain "From: no-reply@oss-review-toolkit.org"
                headerLines shouldContain "To: no-reply@oss-review-toolkit.org"
                headerLines shouldContain "Subject: ORT notifier subject"

                val bodyLines = GreenMailUtil.getBody(this).lines()
                bodyLines shouldContain "Content-Type: text/plain; charset=UTF-8"
                bodyLines shouldContain "<strong>ORT notifier message</strong>"
                bodyLines shouldContain "Content-Type: text/html; charset=UTF-8"
                bodyLines shouldContain "<strong>ORT notifier message</strong>"
            }
        }
    }
})
