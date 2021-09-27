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

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.configureFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.beOfType

import java.net.URI

import org.ossreviewtoolkit.model.Failure
import org.ossreviewtoolkit.model.Success
import org.ossreviewtoolkit.model.config.JiraConfiguration

class JiraNotifierTest : WordSpec({
    val wiremock = WireMockServer(
        WireMockConfiguration.options()
            .dynamicPort()
            .usingFilesUnderDirectory(TEST_FILES_ROOT)
    )

    beforeSpec {
        wiremock.start()
        configureFor(wiremock.port())
    }

    afterSpec {
        wiremock.stop()
    }

    beforeTest {
        wiremock.resetAll()
    }

    "JiraNotifier" should {
        "create a Jira ticket" {
            val projectKey = "TEST"
            val notifier = JiraNotifier(
                JiraConfiguration("http://localhost:${wiremock.port()}", "testuser", "testpassword")
            )

            stubFor(
                get(urlPathEqualTo("/rest/api/latest/project/$projectKey"))
                    .willReturn(
                        aResponse().withStatus(200)
                            .withBodyFile("$TEST_FILES_DIRECTORY/response_get_project.json")
                    )
            )
            stubFor(
                post(urlPathEqualTo("/rest/api/latest/issue"))
                    .withHeader("Content-Type", equalTo("application/json"))
                    .willReturn(
                        aResponse().withStatus(201)
                            .withBodyFile("$TEST_FILES_DIRECTORY/response_create_issue.json")
                    )
            )

            val projectIssueBuilder = notifier.projectIssueBuilder(projectKey)
            val resultIssue = projectIssueBuilder.createIssue(
                "Ticket summary",
                "The description of the ticket.",
                "Bug",
                null,
                false
            )

            resultIssue should beOfType(Success::class)
            (resultIssue as Success).result.let { issue ->
                issue.id shouldBe 2457237
                issue.key shouldBe "PROJECT-1"
                issue.self shouldBe URI("https://jira.oss-review-toolkit.org/rest/api/2/issue/2457237")
            }
        }

        "not create an issue when there are more then one duplicate" {
            val projectKey = "TEST"
            val notifier = JiraNotifier(
                JiraConfiguration("http://localhost:${wiremock.port()}", "testuser", "testpassword")
            )

            stubFor(
                get(urlPathEqualTo("/rest/api/latest/project/$projectKey"))
                    .willReturn(
                        aResponse().withStatus(200)
                            .withBodyFile("$TEST_FILES_DIRECTORY/response_get_project.json")
                    )
            )
            stubFor(
                get(urlPathEqualTo("/rest/api/latest/search"))
                    .withQueryParam("jql", matching("project.*summary.*"))
                    .willReturn(
                        aResponse().withStatus(201)
                            .withBodyFile("$TEST_FILES_DIRECTORY/response_search_jql.json")
                    )
            )

            val projectIssueBuilder = notifier.projectIssueBuilder(projectKey)
            val resultIssue = projectIssueBuilder.createIssue(
                "Ticket summary",
                "The description of the ticket.",
                "Bug"
            )

            resultIssue should beOfType(Failure::class)
            (resultIssue as Failure).error shouldContain("more then 1 duplicate issues")
        }

        "return a Failure if issue type is invalid" {
            val projectKey = "TEST"
            val issueType = "unknownType"
            val notifier = JiraNotifier(
                JiraConfiguration("http://localhost:${wiremock.port()}", "testuser", "testpassword")
            )

            stubFor(
                get(urlPathEqualTo("/rest/api/latest/project/$projectKey"))
                    .willReturn(
                        aResponse().withStatus(200)
                                .withBodyFile("$TEST_FILES_DIRECTORY/response_get_project.json")
                    )
            )

            val projectIssueBuilder = notifier.projectIssueBuilder(projectKey)
            val result = projectIssueBuilder.createIssue(
                "Ticket summary",
                "The description of the ticket.",
                issueType,
                null,
                false
            )

            result should beOfType(Failure::class)
            (result as Failure).error shouldContain("'$issueType' is not valid")
        }
    }
})

private const val TEST_FILES_ROOT = "src/test/assets"
private const val TEST_FILES_DIRECTORY = "jira"
