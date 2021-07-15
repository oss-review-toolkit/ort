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
import com.atlassian.jira.rest.client.api.RestClientException
import com.atlassian.jira.rest.client.api.domain.BasicIssue
import com.atlassian.jira.rest.client.api.domain.Comment
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory

import java.net.URI

import org.ossreviewtoolkit.model.Failure
import org.ossreviewtoolkit.model.Result
import org.ossreviewtoolkit.model.Success
import org.ossreviewtoolkit.model.config.JiraConfiguration
import org.ossreviewtoolkit.utils.collectMessagesAsString
import org.ossreviewtoolkit.utils.log

class JiraNotifier(
    val config: JiraConfiguration,
    private val restClient: JiraRestClient = AsynchronousJiraRestClientFactory()
        .createWithBasicHttpAuthentication(URI(config.host), config.username, config.password)
) {
    /**
     * Returns a [ProjectIssueBuilder] object, which can be used to do operations for the given [projectKey].
     */
    fun projectIssueBuilder(projectKey: String): ProjectIssueBuilder =
        ProjectIssueBuilder(projectKey, restClient)

    class ProjectIssueBuilder(
        private val projectKey: String,
        private val restClient: JiraRestClient
        ) {
        private val issueTypes = restClient.projectClient.getProject(projectKey).claim()
            .issueTypes.associateBy { it.name }

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
                return Failure("The issue type '$issueType' is not valid. Use a valid issue type, specified in " +
                        "your project '$projectKey'")
            }

            val issueInputBuilder = IssueInputBuilder()
                .setProjectKey(projectKey)
                .setSummary(summary)
                .setDescription(description)
                .setIssueType(issueTypes[issueType])
            assignee?.let { issueInputBuilder.setAssigneeName(assignee) }
            val issueInput = issueInputBuilder.build()

            if (avoidDuplicates) {
                val searchResult = restClient.searchClient.searchJql(
                    "project = \"$projectKey\" AND summary ~ \"$summary\""
                ).claim()

                // TODO: Currently, only a comment is added to an issue if there is exactly one duplicate of it. If the
                //       search returns several issues, a failure is returned.
                //       An improvement has to be added here so that it can handle the case that the search returns more
                //       than one issue.
                if (searchResult.total == 1) {
                    val issue = searchResult.issues.iterator().next()

                    return try {
                        restClient.issueClient.addComment(
                            issue.commentsUri,
                            Comment.valueOf("Duplicate of: $summary \n $description")
                        ).claim()

                        Success(issue)
                    } catch (e: RestClientException) {
                        log.error { "The comment for the issue '${issue.key} could not be added: " +
                                "${e.collectMessagesAsString()}" }

                        Failure(e.collectMessagesAsString())
                    }
                } else if (searchResult.total > 1) {
                    log.debug { "There are more then 1 duplicate issues of '$summary', which is supported yet." }

                    return Failure("There are more then 1 duplicate issues of '$summary', which is supported yet.")
                }
            }

            return try {
                val resultIssue = restClient.issueClient.createIssue(issueInput).claim()

                log.info { "Issue ${resultIssue.key} created." }

                Success(resultIssue)
            } catch (e: RestClientException) {
                log.error { "The issue for the project '$projectKey' could not be created: " +
                        "${e.collectMessagesAsString()}" }

                Failure(e.collectMessagesAsString())
            }
        }
    }
}
