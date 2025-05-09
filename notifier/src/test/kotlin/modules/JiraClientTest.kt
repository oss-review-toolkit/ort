/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import com.atlassian.jira.rest.client.api.JiraRestClient
import com.atlassian.jira.rest.client.auth.AnonymousAuthenticationHandler
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import java.net.URI

/**
 * This test uses as much as possible the [JiraRestClient] of the JIRA REST API, using only Wiremock to simulate the
 * response. Contrary to the [JiraNotifier] test which is mocking the client, this test is using the actual client,
 * allowing to test if the JavaEE integration is working properly.
 */
class JiraClientTest : WordSpec({
    val server = WireMockServer(
        WireMockConfiguration.options()
            .dynamicPort()
            .usingFilesUnderClasspath("clientTest")
    )

    lateinit var client: JiraRestClient

    beforeSpec {
        server.start()
        client = AsynchronousJiraRestClientFactory().createWithAuthenticationHandler(
            URI.create("http://localhost:${server.port()}"),
            AnonymousAuthenticationHandler()
        )
    }

    afterSpec {
        server.stop()
    }

    beforeTest {
        server.resetAll()
    }

    "JIRA Rest client" should {
        "query an issue" {
            val issue = client.issueClient.getIssue("ISSUE-1").claim()

            issue shouldNotBeNull {
                summary shouldBe "summary"
                id shouldBe 10000001L
            }
        }
    }
})
