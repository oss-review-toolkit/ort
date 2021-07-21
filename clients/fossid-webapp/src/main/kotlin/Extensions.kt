/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

@file:Suppress("TooManyFunctions")

package org.ossreviewtoolkit.clients.fossid

private const val SCAN_GROUP = "scans"
private const val PROJECT_GROUP = "projects"

/**
 * Verify that a request for the given [operation] was successful. [operation] is a free label describing the operation.
 * If [withDataCheck] is true, also the payload data is checked, otherwise that check is skipped.
 */
fun <B : EntityResponseBody<T>, T> B?.checkResponse(operation: String, withDataCheck: Boolean = true): B {
    // The null check is here to avoid the caller to wrap the call of this function in a null check.
    requireNotNull(this)

    require(error == null) {
        "Could not '$operation'. Additional information : $error"
    }

    if (withDataCheck) {
        requireNotNull(data) {
            "No Payload received for '$operation'. Additional information: $error"
        }
    }

    return this
}

/**
 * Get the project for the given [projectCode].
 *
 * The HTTP request is sent with [user] and [apiKey] as credentials.
 */
suspend fun FossIdRestService.getProject(user: String, apiKey: String, projectCode: String) =
    getProject(
        PostRequestBody("get_information", PROJECT_GROUP, user, apiKey, "project_code" to projectCode)
    )

/**
 * List the scans for the given [projectCode].
 *
 * The HTTP request is sent with [user] and [apiKey] as credentials.
 */
suspend fun FossIdRestService.listScansForProject(user: String, apiKey: String, projectCode: String) =
    listScansForProject(
        PostRequestBody("get_all_scans", PROJECT_GROUP, user, apiKey, "project_code" to projectCode)
    )

/**
 * Create a new project with the given [projectCode], [projectName] and optional [comment].
 *
 * The HTTP request is sent with [user] and [apiKey] as credentials.
 */
suspend fun FossIdRestService.createProject(
    user: String,
    apiKey: String,
    projectCode: String,
    projectName: String,
    comment: String = "Created by ORT"
) =
    createProject(
        PostRequestBody(
            "create",
            PROJECT_GROUP,
            user,
            apiKey,
            "project_code" to projectCode,
            "project_name" to projectName,
            "comment" to comment
        )
    )

/**
 * Create a new scan of [gitRepoUrl]/[gitBranch] for the given [projectCode].
 *
 * The HTTP request is sent with [user] and [apiKey] as credentials.
 */
suspend fun FossIdRestService.createScan(
    user: String,
    apiKey: String,
    projectCode: String,
    scanCode: String,
    gitRepoUrl: String,
    gitBranch: String
): MapResponseBody<String> =
    createScan(
        PostRequestBody(
            "create",
            SCAN_GROUP,
            user,
            apiKey,
            "project_code" to projectCode,
            "scan_code" to scanCode,
            "scan_name" to scanCode,
            "git_repo_url" to gitRepoUrl,
            "git_branch" to gitBranch
        )
    )

/**
 * Trigger a scan with the given [scanCode]. Additional [options] can be passed to FossID.
 *
 * The HTTP request is sent with [user] and [apiKey] as credentials.
 */
suspend fun FossIdRestService.runScan(
    user: String, apiKey: String, scanCode: String, vararg options: Pair<String, String>
) =
    runScan(
        PostRequestBody(
            "run",
            SCAN_GROUP,
            user,
            apiKey,
            "scan_code" to scanCode,
            "auto_identification_detect_declaration" to "1",
            "auto_identification_detect_copyright" to "1",
            *options
        )
    )

/**
 * Delete a scan with the given [scanCode].
 *
 * The HTTP request is sent with [user] and [apiKey] as credentials.
 */
suspend fun FossIdRestService.deleteScan(user: String, apiKey: String, scanCode: String) =
    deleteScan(
        PostRequestBody(
            "delete",
            SCAN_GROUP,
            user,
            apiKey,
            "scan_code" to scanCode,
            "delete_identifications" to "1"
        )
    )

/**
 * Trigger download of the source code for the given [scanCode].
 *
 * The HTTP request is sent with [user] and [apiKey] as credentials.
 */
suspend fun FossIdRestService.downloadFromGit(user: String, apiKey: String, scanCode: String) =
    downloadFromGit(
        PostRequestBody(
            "download_content_from_git",
            SCAN_GROUP,
            user,
            apiKey,
            "scan_code" to scanCode
        )
    )

/**
 * Get the scan status for the given [scanCode].
 *
 * The HTTP request is sent with [user] and [apiKey] as credentials.
 */
suspend fun FossIdRestService.checkScanStatus(user: String, apiKey: String, scanCode: String) =
    checkScanStatus(
        PostRequestBody("check_status", SCAN_GROUP, user, apiKey, "scan_code" to scanCode)
    )

/**
 * Get source code download status for the given [scanCode].
 *
 * The HTTP request is sent with [user] and [apiKey] as credentials.
 */
suspend fun FossIdRestService.checkDownloadStatus(user: String, apiKey: String, scanCode: String) =
    checkDownloadStatus(
        PostRequestBody(
            "check_status_download_content_from_git", SCAN_GROUP, user, apiKey, "scan_code" to scanCode
        )
    )

/**
 * List the results for the given [scanCode].
 *
 * The HTTP request is sent with [user] and [apiKey] as credentials.
 */
suspend fun FossIdRestService.listScanResults(user: String, apiKey: String, scanCode: String) =
    listScanResults(
        PostRequestBody("get_results", SCAN_GROUP, user, apiKey, "scan_code" to scanCode)
    )

/**
 * List the files that have been manually marked as identified for the given [scanCode].
 *
 * The HTTP request is sent with [user] and [apiKey] as credentials.
 */
suspend fun FossIdRestService.listMarkedAsIdentifiedFiles(user: String, apiKey: String, scanCode: String) =
    listMarkedAsIdentifiedFiles(
        PostRequestBody(
            "get_marked_as_identified_files",
            SCAN_GROUP,
            user,
            apiKey,
            "scan_code" to scanCode
        )
    )

/**
 * List the files that have been identified for the given [scanCode].
 *
 * The HTTP request is sent with [user] and [apiKey] as credentials.
 */
suspend fun FossIdRestService.listIdentifiedFiles(user: String, apiKey: String, scanCode: String) =
    listIdentifiedFiles(
        PostRequestBody(
            "get_identified_files", SCAN_GROUP, user, apiKey, "scan_code" to scanCode
        )
    )

/**
 * List the files that have been ignored for the given [scanCode].
 *
 * The HTTP request is sent with [user] and [apiKey] as credentials.
 */
suspend fun FossIdRestService.listIgnoredFiles(user: String, apiKey: String, scanCode: String) =
    listIgnoredFiles(
        PostRequestBody(
            "get_ignored_files", SCAN_GROUP, user, apiKey, "scan_code" to scanCode
        )
    )

/**
 * List the files that are in "pending identification" for the given [scanCode].
 *
 * The HTTP request is sent with [user] and [apiKey] as credentials.
 */
suspend fun FossIdRestService.listPendingFiles(user: String, apiKey: String, scanCode: String) =
    listPendingFiles(
        PostRequestBody(
            "get_pending_files", SCAN_GROUP, user, apiKey, "scan_code" to scanCode
        )
    )
