/*
 * Copyright (C) 2020 Bosch.IO GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package org.ossreviewtoolkit.fossid

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

import okhttp3.OkHttpClient

import org.ossreviewtoolkit.fossid.api.Project
import org.ossreviewtoolkit.fossid.api.Scan
import org.ossreviewtoolkit.fossid.api.identification.identifiedFiles.IdentifiedFile
import org.ossreviewtoolkit.fossid.api.identification.ignored.IgnoredFile
import org.ossreviewtoolkit.fossid.api.identification.markedAsIdentified.MarkedAsIdentifiedFile
import org.ossreviewtoolkit.fossid.api.result.FossIdScanResult
import org.ossreviewtoolkit.fossid.api.status.DownloadStatus
import org.ossreviewtoolkit.fossid.api.status.ScanState
import org.ossreviewtoolkit.fossid.api.status.ScanStatus
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.utils.log

import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory

class FossIdRestClient(private val user: String, private val apiKey: String, serverUrl: String) {
    companion object {
        private const val SCAN_GROUP = "scans"
        private const val PROJECT_GROUP = "projects"
        @JvmStatic
        private val gitFetchDonePattern = """-> FETCH_HEAD(?: Already up to date.)*$""".toRegex()
    }

    private val service: FossIdRestService

    init {
        val client = OkHttpClient.Builder()
                .build()

        val retrofit = Retrofit.Builder()
                .client(client)
                .baseUrl(serverUrl)
                .addConverterFactory(JacksonConverterFactory.create())
                .build()

        service = retrofit.create(FossIdRestService::class.java)
    }

    private fun AbstractPostResponseBody<*>?.checkResponse(operation: String, withDataCheck: Boolean = true) {
        checkNotNull(this)
        require(error == null) {
            "Could not '$operation'. Additional information : $error"
        }
        if (withDataCheck) {
            requireNotNull(data) {
                "No Payload received for '$operation'. Additional information : $error"
            }
        }
    }

    fun getVersion(): String? {
        // extract the version version from the login page
        // &nbsp;&nbsp;&nbsp;cli.  3.1.16 (build 5634934d, RELEASE)
        // TODO replace with a nice 'REST' call if they provide this information in the future
        val regex = "^.*&nbsp;(cli. *[0-9\\. ]+\\(build[\\w, ]+\\)).*$".toRegex()

        val response = service.getLoginPage().execute().body()
        checkNotNull(response)

        response.use {
            response.charStream().buffered().apply {
                var line = this.readLine()
                while (line != null) {
                    val matcher = regex.matchEntire(line)
                    if (matcher != null) {
                        if (matcher.groupValues.size != 2) {
                            this@FossIdRestClient.log.warn { "Version cannot be extracted from FossId Server" }
                            return null
                        }
                        val version = matcher.groupValues[1]
                        this@FossIdRestClient.log.info { "Version from FossId Server is $version" }
                        return version
                    }
                    line = this.readLine()
                }
            }
        }

        log.warn { "Version from FossId Server cannot be found" }
        return null
    }

    fun getProject(projectCode: String): Project? {
        log.info { "Getting project '$projectCode'" }

        val body = PostRequestBody("get_information", PROJECT_GROUP, apiKey, user)
        body.data["project_code"] = projectCode

        val response = service.getProject(body).execute().body()
        checkNotNull(response)

        if (response.status == 0 && response.error == "Project does not exist") {
            log.info { "Project does not exist" }
            return null
        }

        require(response.error == null || response.data == null) {
            "Could not get project. Additional information : " + response.error
        }

        log.info { "Project '$projectCode' exists" }
        return response.data
    }

    fun listScansForProject(projectCode: String): List<Scan> {
        log.info { "Getting scans for project '$projectCode'" }

        val body = PostRequestBody("get_all_scans", PROJECT_GROUP, apiKey, user)
        body.data["project_code"] = projectCode

        val response = service.getScansForProject(body).execute().body()
        response.checkResponse("list scans for project")

        // the list scan operation returns different json depending on the amount of scans
        val scans = when (val data = response?.data) {
            is List<*> -> {
                val scan = jsonMapper.convertValue(data[0], Scan::class.java)
                listOf(scan)
            }
            is Map<*, *> -> {
                data.values.map { jsonMapper.convertValue(it, Scan::class.java) }
            }
            is Boolean -> {
                emptyList<Scan>()
            }
            else -> {
                error("Cannot process the returned scans")
            }
        }
        log.info { "${scans.size} scans found for project=$projectCode" }
        return scans
    }

    fun createProject(projectCode: String): String? {
        log.info { "Creating project '$projectCode'" }

        val body = PostRequestBody("create", PROJECT_GROUP, apiKey, user)
        body.data["project_code"] = projectCode
        body.data["project_name"] = projectCode
        body.data["comment"] = "Created by ORT"

        val response = service.createProject(body).execute().body()
        response.checkResponse("create project")

        log.info { "Project '$projectCode' created successfully" }
        return response?.data?.get("project_id")
    }

    fun createScan(projectCode: String, gitRepoUrl: String, gitBranch: String): String {
        log.info { "Creating scan for  '$gitRepoUrl'" }

        val formatter = DateTimeFormatter.ofPattern("'$projectCode'_yyyyMMdd_HHmmss")
        val scanCode = formatter.format(LocalDateTime.now())

        log.info { "Scan code is '$scanCode'" }

        val body = PostRequestBody("create", SCAN_GROUP, apiKey, user)

        body.data["git_repo_url"] = gitRepoUrl
        body.data["git_branch"] = gitBranch
        body.data["scan_code"] = scanCode
        body.data["scan_name"] = scanCode
        body.data["project_code"] = projectCode

        val response = service.createScan(body).execute().body()
        response.checkResponse("create scan")

        val scanId = response?.data?.get("scan_id")

        requireNotNull(scanId) {
            "Scan was not triggered..."
        }

        log.info { "Scan has been created with id=$scanId" }

        return scanCode
    }

    fun runScan(scanCode: String) {
        log.info { "Running scan '$scanCode'" }

        val body = PostRequestBody("run", SCAN_GROUP, apiKey, user)
        body.data["scan_code"] = scanCode
        body.data["auto_identification_detect_declaration"] = "1"
        body.data["auto_identification_detect_copyright"] = "1"

        val response = service.runScan(body).execute().body()
        response.checkResponse("trigger scan", false)

        log.info { "Scan has been launched" }
    }

    fun downloadFromGit(scanCode: String) {
        log.info { "Download data from git repository for '$scanCode'" }
        val body = PostRequestBody("download_content_from_git", SCAN_GROUP, apiKey, user)

        body.data["scan_code"] = scanCode

        val response = service.downloadFromGit(body).execute().body()
        response.checkResponse("download data from Git", false)

        log.info { "Data download has been launched" }
    }

    fun waitDownloadComplete(scanCode: String) {

            var done = false
            var waited = 0

            while (!done && waited < 50) {
                if (waited % 10 == 0) {
                    this@FossIdRestClient.log.info { "Check download status for '$scanCode'" }
                }

                val body = PostRequestBody("check_status_download_content_from_git", SCAN_GROUP, apiKey, user)
                body.data["scan_code"] = scanCode

                val response = service.checkDownloadStatus(body).execute().body()
                response.checkResponse("check download status")

                when (response?.data) {
                    DownloadStatus.FINISHED -> {
                        done = true
                    }
                    DownloadStatus.NOT_FINISHED, DownloadStatus.NOT_STARTED -> {
                        /*
                        There is a weird bug with the FossId server: sometimes the download is complete but
                        it stays in state "NOT FINISHED". Therefore we check the output of Git Fetch to
                        find out if the download is indeed done
                         */
                        val message = response.message
                        if (message != null && gitFetchDonePattern.containsMatchIn(message)) {
                            this@FossIdRestClient.log.warn {
                                "The download is not finished but Git Fetch has completed. Carrying on..."
                            }
                            done = true
                        } else {
                            Thread.sleep(1000)
                            waited++
                        }
                    }
                }
            }
            require(waited < 50) {
                "Timeout while waiting for the download to complete"
            }
            this@FossIdRestClient.log.info("Data download has been completed")
    }

    fun getScanStatus(scanCode: String): ScanStatus? {
        log.info { "Check scan='$scanCode'" }

        val body = PostRequestBody("check_status", SCAN_GROUP, apiKey, user)
        body.data["scan_code"] = scanCode

        val response = service.checkScanStatus(body).execute().body()
        response.checkResponse("get scan status")

        return response?.data
    }

    suspend fun waitScanComplete(scanCode: String, checkInterval: Long = 10000) {
        log.info { "Waiting for scan='$scanCode' to complete" }

        coroutineScope {
            var status: ScanState? = ScanState.NOT_STARTED

            while (status != ScanState.FINISHED) {
                val scanStatus = getScanStatus(scanCode)
                status = scanStatus?.state
                if (status == ScanState.FINISHED) {
                    this@FossIdRestClient.log.info { "Scan result: ${scanStatus?.comment}" }
                } else {
                    this@FossIdRestClient.log.info { "Scan status for '$scanCode' is '$status'. Waiting..." }
                    delay(checkInterval)
                }
            }
            this@FossIdRestClient.log.info { "Scan has been completed" }
        }
    }

    fun listScanResults(scanCode: String): List<FossIdScanResult> {
        log.info("Listing scan results for scan_code=$scanCode")
        val body = PostRequestBody("get_results", SCAN_GROUP, apiKey, user)
        body.data["scan_code"] = scanCode

        val response = service.listScanResults(body).execute().body()
        response.checkResponse("list scan results")

        return response?.data?.values?.toList() ?: emptyList()
    }

    fun listIdentifiedFiles(scanCode: String): List<IdentifiedFile> {
        log.info("Listing identified files for scan_code=$scanCode")
        val body = PostRequestBody("get_identified_files", SCAN_GROUP, apiKey, user)
        body.data["scan_code"] = scanCode

        val response = service.listIdentifiedFiles(body).execute().body()
        response.checkResponse("list identified files")

        return response?.data?.values?.toList() ?: emptyList()
    }

    fun listMarkedAsIdentifiedFiles(scanCode: String): List<MarkedAsIdentifiedFile> {
        log.info("Listing marked as identified files for scan_code=$scanCode")
        val body = PostRequestBody("get_marked_as_identified_files", SCAN_GROUP, apiKey, user)
        body.data["scan_code"] = scanCode

        val response = service.listMarkedAsIdentifiedFiles(body).execute().body()
        response.checkResponse("list marked as identified files")

        // the list marked files operation returns different json
        val markedAsIdentifiedFiles = when (val data = response?.data) {
            is Map<*, *> -> {
                data.values.map { jsonMapper.convertValue(it, MarkedAsIdentifiedFile::class.java) }
            }
            is Boolean -> {
                emptyList<MarkedAsIdentifiedFile>()
            }
            else -> {
                error("Cannot process the returned files")
            }
        }
        log.info { "${markedAsIdentifiedFiles.size} marked as identified files found for scan='$scanCode'" }
        return markedAsIdentifiedFiles
    }

    fun listIgnoredFiles(scanCode: String): List<IgnoredFile> {
        log.info("Listing ignored files for scan_code=$scanCode")
        val body = PostRequestBody("get_ignored_files", SCAN_GROUP, apiKey, user)
        body.data["scan_code"] = scanCode

        val response = service.listIgnoredFiles(body).execute().body()
        response.checkResponse("list ignored files")

        // the list ignored files operation returns different json
        val ignoredFiles = when (val data = response?.data) {
            is List<*> -> {
                data.map { jsonMapper.convertValue(it, IgnoredFile::class.java) }
            }
            is Boolean -> {
                emptyList<IgnoredFile>()
            }
            else -> {
                error("Cannot process the returned files")
            }
        }
        log.info { "${ignoredFiles.size} ignored files found for scan='$scanCode'" }
        return ignoredFiles
    }
}
