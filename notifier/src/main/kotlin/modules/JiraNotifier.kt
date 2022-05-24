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

import com.atlassian.jira.rest.client.api.JiraRestClient
import com.atlassian.jira.rest.client.api.domain.BasicIssue
import com.atlassian.jira.rest.client.api.domain.Comment
import com.atlassian.jira.rest.client.api.domain.Issue
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder
import com.atlassian.jira.rest.client.api.domain.input.TransitionInput
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory

import java.net.URI

import org.ossreviewtoolkit.model.config.JiraConfiguration
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.ort.log

class JiraNotifier(private val restClient: JiraRestClient) {
    constructor(config: JiraConfiguration) : this(
        AsynchronousJiraRestClientFactory()
            .createWithBasicHttpAuthentication(URI(config.host), config.username, config.password)
    )

    /**
     * Create a [comment] within the issue specified by the [issueKey].
     */
    @Suppress("UNUSED") // This is intended to be used via scripting.
    fun createComment(issueKey: String, comment: String) {
        val issue = restClient.issueClient.getIssue(issueKey).claim()

        restClient.issueClient.addComment(issue.commentsUri, Comment.valueOf(comment)).claim()
    }

    /**
     * Change the [assignee] of the give issue specified by the [issueKey].
     */
    fun changeAssignee(issueKey: String, assignee: String): Boolean {
        val input = IssueInputBuilder().setAssigneeName(assignee).build()

        return runCatching {
            restClient.issueClient.updateIssue(issueKey, input)
        }.onFailure {
            log.error {
                "Could not set the assignee to '$assignee' for issue '$issueKey': ${it.collectMessages()}"
            }
        }.isSuccess
    }

    /**
     * Change the [state] of the given issue specified by the [issueKey].
     */
    fun changeState(issueKey: String, state: String): Boolean {
        val issue = restClient.issueClient.getIssue(issueKey).claim()
        val possibleTransitions = restClient.issueClient.getTransitions(issue).claim()

        return runCatching {
            val transition = possibleTransitions.single { it.name == state }
            restClient.issueClient.transition(issue, TransitionInput(transition.id)).claim()
        }.onFailure {
            log.error {
                "The transition to state '$state' for the issue '$issueKey' is not possible: " +
                        it.collectMessages()
            }
        }.isSuccess
    }

    /**
     * Return the issue for the given [issueKey].
     */
    fun getIssue(issueKey: String): Issue? {
        return runCatching {
            restClient.issueClient.getIssue(issueKey).claim()
        }.onFailure {
            log.error {
                "Could not retrieve the issue with the key '$issueKey' due to: ${it.collectMessages()}"
            }
        }.getOrNull()
    }

    /**
     * Returns a [ProjectIssueBuilder] object, which can be used to do operations for the given [projectKey].
     */
    fun projectIssueBuilder(projectKey: String): ProjectIssueBuilder =
        ProjectIssueBuilder(projectKey, restClient)

    class ProjectIssueBuilder(private val projectKey: String, private val restClient: JiraRestClient) {
        private val issueTypes by lazy {
            restClient.projectClient.getProject(projectKey).claim().issueTypes.associateBy { it.name }
        }

        /**
         * Create an issue for the project with the usage of [summary], [description] and [issueType]. If an [assignee]
         * is given, the assignee for the issue is also set. The [avoidDuplicates] flag decides whether duplicate issues
         * will be created or not. If the flag is set to true (default), it will not create a new issue if there is
         * already exactly one issue with the same [summary], but it will add a comment to the issue with the given
         * [description]. If false, it will always create a new issue even if there are issues with the same [summary].
         * Return a [Result] that encapsulates the created issue on success, or the caught exception on failure.
         */
        fun createIssue(
            summary: String,
            description: String,
            issueType: String,
            assignee: String? = null,
            avoidDuplicates: Boolean = true
        ): Result<BasicIssue> {
            if (issueType !in issueTypes) {
                return Result.failure(
                    IllegalArgumentException(
                        "The issue type '$issueType' is not valid. Use a valid issue type as specified in your " +
                                "project '$projectKey'."
                    )
                )
            }

            val issueInput = IssueInputBuilder()
                .setProjectKey(projectKey)
                .setSummary(summary)
                .setDescription(description)
                .setIssueType(issueTypes[issueType])
                .apply { if (assignee != null) setAssigneeName(assignee) }
                .build()

            if (avoidDuplicates) {
                val searchResult = restClient.searchClient.searchJql(
                    "project = \"$projectKey\" AND summary ~ \"$summary\""
                ).claim()

                // TODO: Currently, only a comment is added to an issue if there is exactly one duplicate of it. If the
                //       search returns several issues, a failure is returned.
                //       An improvement has to be added here so that it can handle the case that the search returns more
                //       than one issue.
                if (searchResult.total == 1) {
                    val issue = searchResult.issues.first()
                    val comment = "$summary\n$description"

                    if (comment in issue.comments.mapNotNull { it.body }) {
                        log.debug {
                            "The comment for the issue '$summary' already exists, therefore no new one will be added."
                        }

                        return Result.success(issue)
                    }

                    return runCatching {
                        restClient.issueClient.addComment(issue.commentsUri, Comment.valueOf(comment)).claim()
                    }.map { issue }.onFailure {
                        log.error {
                            "The comment for the issue '${issue.key} could not be added: " +
                                    it.collectMessages()
                        }
                    }
                } else if (searchResult.total > 1) {
                    log.debug { "There are more than 1 duplicate issues of '$summary', which is not supported yet." }

                    return Result.failure(
                        IllegalArgumentException(
                            "There are more than 1 duplicate issues of '$summary', which is not supported yet."
                        )
                    )
                }
            }

            return runCatching {
                restClient.issueClient.createIssue(issueInput).claim().also {
                    log.info { "Issue ${it.key} created." }
                }
            }.onFailure {
                log.error {
                    "The issue for the project '$projectKey' could not be created: " +
                            it.collectMessages()
                }
            }
        }
    }
}
