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

import com.atlassian.jira.rest.client.api.IssueRestClient
import com.atlassian.jira.rest.client.api.JiraRestClient
import com.atlassian.jira.rest.client.api.OptionalIterable
import com.atlassian.jira.rest.client.api.ProjectRestClient
import com.atlassian.jira.rest.client.api.SearchRestClient
import com.atlassian.jira.rest.client.api.domain.BasicIssue
import com.atlassian.jira.rest.client.api.domain.Comment
import com.atlassian.jira.rest.client.api.domain.Issue
import com.atlassian.jira.rest.client.api.domain.IssueType
import com.atlassian.jira.rest.client.api.domain.Project
import com.atlassian.jira.rest.client.api.domain.SearchResult
import com.atlassian.jira.rest.client.api.domain.Transition

import io.atlassian.util.concurrent.Promise

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

import java.net.URI

class JiraNotifierTest : WordSpec({
    "createIssue" should {
        "succeed for valid input" {
            val project = createProject()
            val issueClient = mockk<IssueRestClient>()
            val projectClient = mockk<ProjectRestClient>()
            val restClient = mockk<JiraRestClient>()

            every { restClient.issueClient } returns issueClient
            every { restClient.projectClient } returns projectClient
            every { issueClient.createIssue(any()) } returns createPromise(BasicIssue(URI(""), "$PROJECT_KEY-1", 1L))
            every { projectClient.getProject(any<String>()) } returns createPromise(project)

            val notifier = JiraNotifier(restClient)

            val projectIssueBuilder = notifier.projectIssueBuilder(project.key)
            val resultIssue = projectIssueBuilder.createIssue(
                summary = "Ticket summary",
                description = "The description of the ticket.",
                issueType = "Bug",
                avoidDuplicates = false
            )

            resultIssue.shouldBeSuccess {
                it.id shouldBe 1L
                it.key shouldBe "${project.key}-${it.id}"
                it.self shouldBe URI("")
            }
        }

        "fail if more than one duplicate issues exist" {
            val project = createProject()
            val issueClient = mockk<IssueRestClient>()
            val projectClient = mockk<ProjectRestClient>()
            val searchClient = mockk<SearchRestClient>()
            val restClient = mockk<JiraRestClient>()

            val summary = "Ticket summary"
            val query = "project = \"${project.key}\" AND summary ~ \"$summary\""

            val existingIssues = listOf(createIssue(id = 1, summary = summary), createIssue(id = 2, summary = summary))

            every { restClient.issueClient } returns issueClient
            every { restClient.projectClient } returns projectClient
            every { restClient.searchClient } returns searchClient
            every { issueClient.createIssue(any()) } returns createPromise(BasicIssue(URI(""), "$PROJECT_KEY-1", 1L))
            every { projectClient.getProject(any<String>()) } returns createPromise(project)
            every { searchClient.searchJql(query) } returns createPromise(SearchResult(0, 2, 2, existingIssues))

            val notifier = JiraNotifier(restClient)

            val projectIssueBuilder = notifier.projectIssueBuilder(project.key)
            val resultIssue = projectIssueBuilder.createIssue(
                summary = summary,
                description = "The description of the ticket.",
                issueType = "Bug",
                avoidDuplicates = true
            )

            resultIssue.shouldBeFailure {
                it.message shouldContain "more than 1 duplicate issues"
            }

            verify(exactly = 0) {
                issueClient.createIssue(any())
                issueClient.addComment(any(), any())
            }
        }

        "add a comment if one duplicate issue exists" {
            val project = createProject()
            val issueClient = mockk<IssueRestClient>()
            val projectClient = mockk<ProjectRestClient>()
            val searchClient = mockk<SearchRestClient>()
            val restClient = mockk<JiraRestClient>()

            val summary = "Ticket summary"
            val query = "project = \"${project.key}\" AND summary ~ \"$summary\""

            val existingIssues = listOf(createIssue(id = 1, summary = summary))

            every { restClient.issueClient } returns issueClient
            every { restClient.projectClient } returns projectClient
            every { restClient.searchClient } returns searchClient
            every { issueClient.createIssue(any()) } returns createPromise(BasicIssue(URI(""), "$PROJECT_KEY-1", 1L))
            every { issueClient.addComment(any(), any()) } returns createVoidPromise()
            every { projectClient.getProject(any<String>()) } returns createPromise(project)
            every { searchClient.searchJql(query) } returns createPromise(SearchResult(0, 1, 1, existingIssues))

            val notifier = JiraNotifier(restClient)

            val projectIssueBuilder = notifier.projectIssueBuilder(project.key)

            projectIssueBuilder.createIssue(
                summary = summary,
                description = "The description of the ticket.",
                issueType = "Bug",
                avoidDuplicates = true
            )

            verify(exactly = 1) {
                issueClient.addComment(any(), any())
            }
        }

        "not add a comment if one duplicate issue exists that already has an identical comment" {
            val project = createProject()
            val issueClient = mockk<IssueRestClient>()
            val projectClient = mockk<ProjectRestClient>()
            val searchClient = mockk<SearchRestClient>()
            val restClient = mockk<JiraRestClient>()

            val summary = "Ticket summary"
            val description = "The description of the ticket."
            val query = "project = \"${project.key}\" AND summary ~ \"$summary\""

            val existingIssues =
                listOf(createIssue(id = 1, summary = summary, comments = listOf("$summary\n$description")))

            every { restClient.issueClient } returns issueClient
            every { restClient.projectClient } returns projectClient
            every { restClient.searchClient } returns searchClient
            every { issueClient.createIssue(any()) } returns createPromise(BasicIssue(URI(""), "$PROJECT_KEY-1", 1L))
            every { projectClient.getProject(any<String>()) } returns createPromise(project)
            every { searchClient.searchJql(query) } returns createPromise(SearchResult(0, 1, 1, existingIssues))

            val notifier = JiraNotifier(restClient)

            val projectIssueBuilder = notifier.projectIssueBuilder(project.key)

            projectIssueBuilder.createIssue(
                summary = summary,
                description = description,
                issueType = "Bug",
                avoidDuplicates = true
            )

            verify(exactly = 0) {
                issueClient.addComment(any(), any())
            }
        }

        "fail if the issue type is invalid" {
            val project = createProject()
            val issueClient = mockk<IssueRestClient>()
            val projectClient = mockk<ProjectRestClient>()
            val restClient = mockk<JiraRestClient>()

            every { restClient.issueClient } returns issueClient
            every { restClient.projectClient } returns projectClient
            every { projectClient.getProject(any<String>()) } returns createPromise(project)

            val notifier = JiraNotifier(restClient)

            val issueType = "Invalid"

            val projectIssueBuilder = notifier.projectIssueBuilder(project.key)
            val result = projectIssueBuilder.createIssue(
                summary = "Ticket summary",
                description = "The description of the ticket.",
                issueType = issueType,
                avoidDuplicates = false
            )

            result.shouldBeFailure {
                it.message shouldContain "'$issueType' is not valid"
            }
        }
    }

    "changeState" should {
        "succeed for valid input" {
            val issueClient = mockk<IssueRestClient>()
            val restClient = mockk<JiraRestClient>()

            val state = "Bug"
            val transitions = listOf(createTransition(id = 1, state = state))

            every { restClient.issueClient } returns issueClient
            every { issueClient.getIssue(any()) } returns createPromise(createIssue(id = 1, summary = "Ticket summary"))
            every { issueClient.getTransitions(any<Issue>()) } returns createPromise(transitions)

            val notifier = JiraNotifier(restClient)
            notifier.changeState("$PROJECT_KEY-1", state)

            verify(exactly = 1) {
                issueClient.transition(any<Issue>(), any())
            }
        }

        "fail if attempting an invalid transition" {
            val issueClient = mockk<IssueRestClient>()
            val restClient = mockk<JiraRestClient>()

            val validState = "Bug"
            val invalidState = "Task"
            val transitions = listOf(createTransition(id = 1, state = validState))

            every { restClient.issueClient } returns issueClient
            every { issueClient.getIssue(any()) } returns createPromise(createIssue(id = 1, summary = "Ticket summary"))
            every { issueClient.getTransitions(any<Issue>()) } returns createPromise(transitions)

            val notifier = JiraNotifier(restClient)
            notifier.changeState("$PROJECT_KEY-1", invalidState) shouldBe false
        }
    }
})

private const val PROJECT_KEY = "PROJECT"

private val ISSUE_TYPES = listOf(
    createIssueType("Bug"),
    createIssueType("New Feature"),
    createIssueType("Task"),
    createIssueType("Improvement")
)

private fun createIssueType(name: String) =
    IssueType(URI(""), 0L, name, false, "", URI(""))

private fun <T> createPromise(value: T): Promise<T> =
    mockk {
        every { claim() } returns value
    }

private fun createVoidPromise(): Promise<Void> =
    mockk {
        every { claim() } returns null
    }

private fun createProject(): Project =
    mockk {
        every { key } returns PROJECT_KEY
        every { issueTypes } returns OptionalIterable(ISSUE_TYPES)
    }

private fun createComment(message: String): Comment =
    mockk {
        every { body } returns message
    }

private fun createIssue(id: Long, summary: String = "", comments: List<String> = emptyList()): Issue =
    mockk {
        every { this@mockk.id } returns id
        every { key } returns "$PROJECT_KEY-$id"
        every { this@mockk.summary } returns summary
        every { this@mockk.comments } returns comments.map { createComment(it) }
        every { commentsUri } returns URI("")
    }

private fun createTransition(id: Int, state: String): Transition =
    mockk {
        every { this@mockk.id } returns id
        every { name } returns state
    }
