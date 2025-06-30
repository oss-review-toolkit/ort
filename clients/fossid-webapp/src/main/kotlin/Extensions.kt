/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import java.io.File
import java.nio.ByteBuffer

import kotlin.io.encoding.Base64

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

import okio.BufferedSink
import okio.buffer
import okio.sink

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.clients.fossid.model.CreateScanResponse
import org.ossreviewtoolkit.clients.fossid.model.RemoveUploadContentResponse
import org.ossreviewtoolkit.clients.fossid.model.identification.common.LicenseMatchType
import org.ossreviewtoolkit.clients.fossid.model.report.ReportType
import org.ossreviewtoolkit.clients.fossid.model.report.SelectionType
import org.ossreviewtoolkit.clients.fossid.model.result.MatchedLines
import org.ossreviewtoolkit.clients.fossid.model.result.Snippet
import org.ossreviewtoolkit.clients.fossid.model.rules.RuleScope
import org.ossreviewtoolkit.clients.fossid.model.rules.RuleType

internal const val SCAN_GROUP = "scans"
private const val FILES_AND_FOLDERS_GROUP = "files_and_folders"
private const val PROJECT_GROUP = "projects"
private val APPLICATION_OCTET_STREAM_MEDIA_TYPE = "application/octet-stream".toMediaType()
private const val UPLOAD_MAX_FILE_SIZE = 8 * 1024 * 1024 // Default max file size defined in the FossID Workbench agent.
private const val UPLOAD_CHUNK_SIZE = 5 * 1024 * 1024 // Default chunk size defined in the FossID Workbench agent.

/**
 * Verify that a request for the given [operation] was successful. [operation] is a free label describing the operation.
 * If [withDataCheck] is true, also the payload data is checked, otherwise that check is skipped.
 */
fun <B : EntityResponseBody<T>, T> B?.checkResponse(operation: String, withDataCheck: Boolean = true): B {
    // The null check is here to avoid the caller to wrap the call of this function in a null check.
    requireNotNull(this)

    require(error == null) {
        "Could not '$operation'. Additional information: $error"
    }

    if (withDataCheck) {
        requireNotNull(data) {
            "No Payload received for '$operation'."
        }
    }

    return this
}

/**
 * Extract the version from the login page.
 * Example: `<link rel='stylesheet' href='style/fossid.css?v=2021.2.2#7936'>`
 */
suspend fun FossIdRestService.getFossIdVersion(): String? {
    // TODO: replace with an API call when FossID provides a function (starting at version 2021.2).
    val regex = Regex("^.*fossid.css\\?v=([0-9.]+).*$")

    getLoginPage().charStream().buffered().useLines { lines ->
        lines.forEach { line ->
            val matcher = regex.matchEntire(line)
            if (matcher != null && matcher.groupValues.size == 2) return matcher.groupValues[1]
        }
    }

    return null
}

/**
 * Get the project for the given [projectCode].
 *
 * The HTTP request is sent with [user] and [apiKey] as credentials.
 */
suspend fun FossIdRestService.getProject(user: String, apiKey: String, projectCode: String) =
    getProject(
        PostRequestBody("get_information", PROJECT_GROUP, user, apiKey, mapOf("project_code" to projectCode))
    )

/**
 * Get all projects available in this instance.
 *
 * The HTTP request is sent with [user] and [apiKey] as credentials.
 */
suspend fun FossIdRestService.listProjects(user: String, apiKey: String) =
    listProjects(
        PostRequestBody("list_projects", PROJECT_GROUP, user, apiKey, emptyMap())
    )

/**
 * Get the scan for the given [scanCode].
 *
 * The HTTP request is sent with [user] and [apiKey] as credentials.
 */
suspend fun FossIdRestService.getScan(user: String, apiKey: String, scanCode: String) =
    getScan(
        PostRequestBody("get_information", SCAN_GROUP, user, apiKey, mapOf("scan_code" to scanCode))
    )

/**
 * List the scans for the given [projectCode].
 *
 * The HTTP request is sent with [user] and [apiKey] as credentials.
 */
suspend fun FossIdRestService.listScansForProject(user: String, apiKey: String, projectCode: String) =
    listScansForProject(
        PostRequestBody("get_all_scans", PROJECT_GROUP, user, apiKey, mapOf("project_code" to projectCode))
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
) = createProject(
    PostRequestBody(
        "create",
        PROJECT_GROUP,
        user,
        apiKey,
        mapOf(
            "project_code" to projectCode,
            "project_name" to projectName,
            "comment" to comment
        )
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
    gitRepoUrl: String? = null,
    gitBranch: String? = null,
    comment: String = ""
): PolymorphicDataResponseBody<CreateScanResponse> {
    val options = buildMap {
        put("project_code", projectCode)
        put("scan_code", scanCode)
        put("scan_name", scanCode)
        put("comment", comment)

        if (gitRepoUrl != null) put("git_repo_url", gitRepoUrl)

        if (gitBranch != null) put("git_branch", gitBranch)
    }

    return createScan(
        PostRequestBody(
            "create", SCAN_GROUP, user, apiKey, options
        )
    )
}

/**
 * Trigger a scan with the given [scanCode]. Additional [options] can be passed to FossID.
 *
 * The HTTP request is sent with [user] and [apiKey] as credentials.
 */
suspend fun FossIdRestService.runScan(
    user: String,
    apiKey: String,
    scanCode: String,
    options: Map<String, String> = emptyMap()
): EntityResponseBody<Nothing> =
    runScan(
        PostRequestBody(
            "run",
            SCAN_GROUP,
            user,
            apiKey,
            buildMap {
                put("scan_code", scanCode)
                putAll(options)
            }
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
            mapOf(
                "scan_code" to scanCode,
                "delete_identifications" to "1"
            )
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
            mapOf("scan_code" to scanCode)
        )
    )

/**
 * Get source code download status for the given [scanCode].
 *
 * The HTTP request is sent with [user] and [apiKey] as credentials.
 */
suspend fun FossIdRestService.checkDownloadStatus(user: String, apiKey: String, scanCode: String) =
    checkDownloadStatus(
        PostRequestBody(
            "check_status_download_content_from_git", SCAN_GROUP, user, apiKey, mapOf("scan_code" to scanCode)
        )
    )

/**
 * List the results for the given [scanCode].
 *
 * The HTTP request is sent with [user] and [apiKey] as credentials.
 */
suspend fun FossIdRestService.listScanResults(user: String, apiKey: String, scanCode: String) =
    listScanResults(
        PostRequestBody("get_results", SCAN_GROUP, user, apiKey, mapOf("scan_code" to scanCode))
    )

/**
 * List the snippets for the given file with [path] for the given [scanCode].
 *
 * The HTTP request is sent with [user] and [apiKey] as credentials.
 */
suspend fun FossIdRestService.listSnippets(
    user: String,
    apiKey: String,
    scanCode: String,
    path: String
): PolymorphicResponseBody<Snippet> {
    val base64Path = Base64.encode(path.toByteArray())
    return listSnippets(
        PostRequestBody(
            "get_fossid_results",
            FILES_AND_FOLDERS_GROUP,
            user,
            apiKey,
            mapOf("scan_code" to scanCode, "path" to base64Path)
        )
    )
}

/**
 * List matched lines for the given file with [path] and the given [scanCode], which the given [snippetId]. The
 * corresponding snippet must have a partial match type, otherwise an error is returned by FossID.
 *
 * The HTTP request is sent with [user] and [apiKey] as credentials.
 */
suspend fun FossIdRestService.listMatchedLines(
    user: String,
    apiKey: String,
    scanCode: String,
    path: String,
    snippetId: Int
): PolymorphicDataResponseBody<MatchedLines> {
    val base64Path = Base64.encode(path.toByteArray())
    return listMatchedLines(
        PostRequestBody(
            "get_matched_lines",
            FILES_AND_FOLDERS_GROUP,
            user,
            apiKey,
            mapOf("scan_code" to scanCode, "path" to base64Path, "client_result_id" to "$snippetId")
        )
    )
}

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
            mapOf("scan_code" to scanCode)
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
            "get_identified_files", SCAN_GROUP, user, apiKey, mapOf("scan_code" to scanCode)
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
            "get_ignored_files", SCAN_GROUP, user, apiKey, mapOf("scan_code" to scanCode)
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
            "get_pending_files", SCAN_GROUP, user, apiKey, mapOf("scan_code" to scanCode)
        )
    )

/**
 * List ignore rules for the given [scanCode].
 *
 * The HTTP request is sent with [user] and [apiKey] as credentials.
 */
suspend fun FossIdRestService.listIgnoreRules(user: String, apiKey: String, scanCode: String) =
    listIgnoreRules(
        PostRequestBody(
            "ignore_rules_show", SCAN_GROUP, user, apiKey, mapOf("scan_code" to scanCode)
        )
    )

/**
 * Create an 'ignore rule' for the given [scanCode].
 *
 * The HTTP request is sent with [user] and [apiKey] as credentials.
 */
suspend fun FossIdRestService.createIgnoreRule(
    user: String,
    apiKey: String,
    scanCode: String,
    type: RuleType,
    value: String,
    scope: RuleScope
) = createIgnoreRule(
    PostRequestBody(
        "ignore_rules_add",
        SCAN_GROUP,
        user,
        apiKey,
        mapOf(
            "scan_code" to scanCode,
            "type" to type.name.lowercase(),
            "value" to value,
            "apply_to" to scope.name.lowercase()
        )
    )
)

/**
 * Ask the FossID server to generate a [reportType] report containing [selectionType]. The report will be generated in
 * the [directory].
 */
suspend fun FossIdRestService.generateReport(
    user: String,
    apiKey: String,
    scanCode: String,
    reportType: ReportType,
    selectionType: SelectionType,
    directory: File
): Result<File> {
    val response = generateReport(
        PostRequestBody(
            "generate_report",
            SCAN_GROUP,
            user,
            apiKey,
            mapOf(
                "scan_code" to scanCode,
                "report_type" to reportType.toString(),
                "selection_type" to selectionType.name.lowercase()
            )
        )
    )

    return if (response.isSuccessful) {
        // The API is quirky here: If the report type is HTML, the result is returned as a standard HTML response,
        // without filename. For any other report types, the result is returned as an attachment with a filename in the
        // headers (even for report types generating HTML such as DYNAMIC).
        val fileName = if (reportType == ReportType.HTML_STATIC) {
            "fossid-$scanCode-report.html"
        } else {
            val contentDisposition = response.headers()["Content-disposition"]

            contentDisposition?.split(';')?.firstNotNullOfOrNull {
                it.trim().withoutPrefix("filename=")?.removeSurrounding("\"")
            } ?: return Result.failure<File>(IllegalStateException("Cannot determine name of the report")).also {
                logger.error {
                    "Cannot determine name of the report with raw headers '$contentDisposition'."
                }
            }
        }

        Result.success(
            directory.resolve(fileName).apply {
                sink().buffer().use { target ->
                    response.body()?.use { target.writeAll(it.source()) }
                }
            }
        )
    } else {
        Result.failure(
            IllegalStateException(
                """
                    Report generation failed with error code ${response.code()}: ${response.message()}.
                    Details: ${response.errorBody()?.string()}
                """.trimIndent()
            )
        )
    }
}

/**
 * Mark the given file with [path] as identified for the given [scanCode].
 *
 * The HTTP request is sent with [user] and [apiKey] as credentials.
 */
suspend fun FossIdRestService.markAsIdentified(
    user: String,
    apiKey: String,
    scanCode: String,
    path: String,
    isDirectory: Boolean
): EntityResponseBody<Nothing> {
    val base64Path = Base64.encode(path.toByteArray())
    val directoryFlag = if (isDirectory) "1" else "0"
    return markAsIdentified(
        PostRequestBody(
            "mark_as_identified",
            FILES_AND_FOLDERS_GROUP,
            user,
            apiKey,
            mapOf("scan_code" to scanCode, "path" to base64Path, "is_directory" to directoryFlag)
        )
    )
}

/**
 * Unmark the given file with [path] as identified for the given [scanCode].
 *
 * The HTTP request is sent with [user] and [apiKey] as credentials.
 */
suspend fun FossIdRestService.unmarkAsIdentified(
    user: String,
    apiKey: String,
    scanCode: String,
    path: String,
    isDirectory: Boolean
): EntityResponseBody<Nothing> {
    val base64Path = Base64.encode(path.toByteArray())
    val directoryFlag = if (isDirectory) "1" else "0"
    return unmarkAsIdentified(
        PostRequestBody(
            "unmark_as_identified",
            FILES_AND_FOLDERS_GROUP,
            user,
            apiKey,
            mapOf("scan_code" to scanCode, "path" to base64Path, "is_directory" to directoryFlag)
        )
    )
}

/**
 * Add license identification [licenseIdentifier] to file with [path] for the given [scanCode].
 *
 * The HTTP request is sent with [user] and [apiKey] as credentials.
 */
suspend fun FossIdRestService.addLicenseIdentification(
    user: String,
    apiKey: String,
    scanCode: String,
    path: String,
    licenseIdentifier: String,
    identificationOn: LicenseMatchType,
    isDirectory: Boolean
): EntityResponseBody<Nothing> {
    val base64Path = Base64.encode(path.toByteArray())
    val directoryFlag = if (isDirectory) "1" else "0"
    return addLicenseIdentification(
        PostRequestBody(
            "add_license_identification",
            FILES_AND_FOLDERS_GROUP,
            user,
            apiKey,
            mapOf(
                "scan_code" to scanCode,
                "path" to base64Path,
                "license_identifier" to licenseIdentifier,
                "identification_on" to identificationOn.name.lowercase(),
                "is_directory" to directoryFlag
            )
        )
    )
}

/**
 * Add component identification for component [componentName]/[componentVersion] to file with [path] for the given
 * [scanCode]. If [preserveExistingIdentifications] is true, identification is appended, otherwise it replaces existing
 * identifications.
 *
 * The HTTP request is sent with [user] and [apiKey] as credentials.
 */
@Suppress("LongParameterList")
suspend fun FossIdRestService.addComponentIdentification(
    user: String,
    apiKey: String,
    scanCode: String,
    path: String,
    componentName: String,
    componentVersion: String,
    isDirectory: Boolean,
    preserveExistingIdentifications: Boolean = true
): EntityResponseBody<Nothing> {
    val base64Path = Base64.encode(path.toByteArray())
    val directoryFlag = if (isDirectory) "1" else "0"
    val preserveExistingIdentificationsFlag = if (preserveExistingIdentifications) "1" else "0"
    return addComponentIdentification(
        PostRequestBody(
            "set_identification_component",
            FILES_AND_FOLDERS_GROUP,
            user,
            apiKey,
            mapOf(
                "scan_code" to scanCode,
                "path" to base64Path,
                "is_directory" to directoryFlag,
                "component_name" to componentName,
                "component_version" to componentVersion,
                "preserve_existing_identifications" to preserveExistingIdentificationsFlag
            )
        )
    )
}

/**
 * Add a [comment] to file with [path] for the given [scanCode].
 *
 * The HTTP request is sent with [user] and [apiKey] as credentials.
 */
suspend fun FossIdRestService.addFileComment(
    user: String,
    apiKey: String,
    scanCode: String,
    path: String,
    comment: String,
    isImportant: Boolean = false,
    includeInReport: Boolean = false
): EntityResponseBody<Nothing> {
    val base64Path = Base64.encode(path.toByteArray())
    val isImportantFlag = if (isImportant) "1" else "0"
    val includeInReportFlag = if (includeInReport) "1" else "0"
    return addFileComment(
        PostRequestBody(
            "add_file_comment",
            FILES_AND_FOLDERS_GROUP,
            user,
            apiKey,
            mapOf(
                "scan_code" to scanCode,
                "path" to base64Path,
                "comment" to comment,
                "is_important" to isImportantFlag,
                "include_in_report" to includeInReportFlag
            )
        )
    )
}

/**
 * Check uploaded files for archives and decompress them. If [fileName] is specified, only this file is extracted.
 * If [recursivelyExtractArchives] is specified, also extract archives inside the extracted files.
 * If [jarFileExtraction] is specified, also extract JAR files.
 *
 * The HTTP request is sent with [user] and [apiKey] as credentials.
 */
suspend fun FossIdRestService.extractArchives(
    user: String,
    apiKey: String,
    scanCode: String,
    fileName: String? = null,
    recursivelyExtractArchives: Boolean = false,
    extractToDirectory: Boolean = true,
    jarFileExtraction: Boolean = false
): EntityResponseBody<Boolean> {
    val recursivelyExtractArchivesFlag = if (recursivelyExtractArchives) "1" else "0"
    val extractToDirectoryFlag = if (extractToDirectory) "1" else "0"
    val jarFileExtractionFlag = if (jarFileExtraction) "1" else "0"
    val baseOptions = mapOf(
        "scan_code" to scanCode,
        "recursively_extract_archives" to recursivelyExtractArchivesFlag,
        "extract_to_directory" to extractToDirectoryFlag,
        "jar_file_extraction" to jarFileExtractionFlag
    )
    return extractArchives(
        PostRequestBody(
            "extract_archives",
            SCAN_GROUP,
            user,
            apiKey,
            if (fileName == null) {
                baseOptions
            } else {
                baseOptions + mapOf("filename" to fileName)
            }
        )
    )
}

/**
 * Remove uploaded content for the given [scanCode]. If [fileName] is specified, only this file is removed.
 *
 * The HTTP request is sent with [user] and [apiKey] as credentials.
 */
suspend fun FossIdRestService.removeUploadedContent(
    user: String,
    apiKey: String,
    scanCode: String,
    fileName: String? = null
): PolymorphicDataResponseBody<RemoveUploadContentResponse> {
    val baseOptions = mapOf("scan_code" to scanCode)
    return removeUploadedContent(
        PostRequestBody(
            "remove_uploaded_content",
            SCAN_GROUP,
            user,
            apiKey,
            if (fileName == null) {
                baseOptions
            } else {
                baseOptions + mapOf("filename" to fileName)
            }
        )
    )
}

/**
 * Upload a file to the given [scanCode] on the FossID server. If the file is bigger than [UPLOAD_MAX_FILE_SIZE] bytes
 * or if [forceChunkedUpload] is true, the file is uploaded in chunks of [chunkSize] bytes.
 *
 * When a chunk of the file is uploaded, the server expects a Transfer-Encoding header with the value "chunked". Please
 * note that this is not the transfer encoding defined by the RFC 9112 §7.1: The body of the request doesn't contain the
 * chunk size nor the chunk delimiter, but only the raw bytes of the file chunk (see
 * https://en.wikipedia.org/wiki/Chunked_transfer_encoding).
 *
 * The HTTP request is sent with [user] and [apiKey] as credentials.
 */
suspend fun FossIdRestService.uploadFile(
    user: String,
    apiKey: String,
    scanCode: String,
    file: File,
    chunkSize: Int = UPLOAD_CHUNK_SIZE,
    forceChunkedUpload: Boolean = false
): EntityResponseBody<Nothing> {
    require(file.isFile) { "The file '$file' does not exist or is not a regular file." }

    val scanCodeB64 = Base64.encode(scanCode.toByteArray())
    val fileName = Base64.encode(file.name.toByteArray())
    val basicAuthHeaderValue = Base64.encode("$user:$apiKey".toByteArray())
    return if (file.length() > UPLOAD_MAX_FILE_SIZE || forceChunkedUpload) {
        logger.info {
            "File '${file.absolutePath}' with size ${file.length()} will be uploaded in chunked mode."
        }

        val buffer = ByteBuffer.allocate(chunkSize)
        var chunkCount = 0

        file.inputStream().use {
            var read = it.channel.read(buffer)

            while (read != -1) {
                val array = ByteArray(read)
                buffer.flip().get(array)

                logger.info {
                    "Uploading chunk #${chunkCount++} of file ${file.absolutePath}..."
                }

                // Here, array.toRequestBody(contentType) should be used to create the request body, but when a request
                // body has a content length different of -1, OkHttp removes the chunked transfer encoding header and
                // set the content length header instead. Therefore, a special RequestBody has to be created instead.
                // See https://github.com/square/retrofit/issues/1315.
                val requestBody = object : RequestBody() {
                    override fun contentType() = APPLICATION_OCTET_STREAM_MEDIA_TYPE

                    override fun contentLength() = -1L

                    override fun writeTo(sink: BufferedSink) {
                        sink.write(array, 0, array.size)
                    }
                }

                val response = uploadFile(
                    scanCodeB64,
                    fileName,
                    "Basic $basicAuthHeaderValue",
                    "chunked",
                    requestBody
                )

                response.checkResponse("upload file chunk", withDataCheck = false)
                buffer.clear()
                read = it.channel.read(buffer)
            }
        }

        EntityResponseBody(status = 1)
    } else {
        logger.info {
            "File '${file.absolutePath}' with size ${file.length()} will NOT be uploaded in chunked mode."
        }

        uploadFile(
            scanCodeB64,
            fileName,
            "Basic $basicAuthHeaderValue",
            "",
            file.readBytes().toRequestBody(APPLICATION_OCTET_STREAM_MEDIA_TYPE)
        )
    }
}

/**
 * If this string starts with [prefix], return the string without the prefix, otherwise return [missingPrefixValue].
 */
fun String?.withoutPrefix(prefix: String, missingPrefixValue: () -> String? = { null }): String? =
    this?.removePrefix(prefix)?.takeIf { it != this } ?: missingPrefixValue()
