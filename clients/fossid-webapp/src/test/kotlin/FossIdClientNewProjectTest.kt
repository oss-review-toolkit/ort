/*
 * Copyright (C) 2020-2021 Bosch.IO GmbH
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

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import org.ossreviewtoolkit.clients.fossid.FossIdRestService
import org.ossreviewtoolkit.clients.fossid.checkDownloadStatus
import org.ossreviewtoolkit.clients.fossid.checkResponse
import org.ossreviewtoolkit.clients.fossid.checkScanStatus
import org.ossreviewtoolkit.clients.fossid.createProject
import org.ossreviewtoolkit.clients.fossid.createScan
import org.ossreviewtoolkit.clients.fossid.deleteScan
import org.ossreviewtoolkit.clients.fossid.downloadFromGit
import org.ossreviewtoolkit.clients.fossid.getProject
import org.ossreviewtoolkit.clients.fossid.listIdentifiedFiles
import org.ossreviewtoolkit.clients.fossid.listIgnoredFiles
import org.ossreviewtoolkit.clients.fossid.listMarkedAsIdentifiedFiles
import org.ossreviewtoolkit.clients.fossid.listPendingFiles
import org.ossreviewtoolkit.clients.fossid.listScanResults
import org.ossreviewtoolkit.clients.fossid.listScansForProject
import org.ossreviewtoolkit.clients.fossid.model.status.DownloadStatus
import org.ossreviewtoolkit.clients.fossid.model.status.ScanStatus
import org.ossreviewtoolkit.clients.fossid.runScan
import org.ossreviewtoolkit.utils.test.shouldNotBeNull

private const val PROJECT_CODE = "semver4j"
private const val SCAN_CODE = "${PROJECT_CODE}_20201203_090342"

/**
 * This client test creates a new project, triggers the download and the scan and gets the scan results.
 */
class FossIdClientNewProjectTest : StringSpec({
    val wiremock = WireMockServer(
        WireMockConfiguration.options()
            .dynamicPort()
            .usingFilesUnderDirectory("src/test/assets/new-project")
    )
    lateinit var service: FossIdRestService

    beforeSpec {
        wiremock.start()
        WireMock.configureFor(wiremock.port())
        service = FossIdRestService.create("http://localhost:${wiremock.port()}")
    }

    afterSpec {
        wiremock.stop()
    }

    beforeTest {
        wiremock.resetAll()
    }

    "Version can be extracted from index" {
        service.getLoginPage() shouldNotBeNull {
            string() shouldContain "2020.1.2"
        }
    }

    "Projects can be listed when there is none" {
        service.getProject("", "", PROJECT_CODE) shouldNotBeNull {
            status shouldBe 0
            data should beNull()
        }
    }

    "Project can be created" {
        service.createProject("", "", PROJECT_CODE, PROJECT_CODE) shouldNotBeNull {
            checkResponse("create project")
            data shouldNotBeNull {
                shouldContain("project_id", "405")
            }
        }
    }

    "Scans for project can be listed when there is no scan" {
        service.listScansForProject("", "", PROJECT_CODE) shouldNotBeNull {
            checkResponse("list scans")
            data shouldNotBeNull {
                isEmpty() shouldBe true
            }
        }
    }

    "Scan can be created" {
        service.createScan(
            "", "",
            PROJECT_CODE,
            SCAN_CODE,
            "https://github.com/gundy/semver4j.git",
            "671aa533f7e33c773bf620b9f466650c3b9ab26e"
        ) shouldNotBeNull {
            data shouldNotBeNull {
                shouldContain("scan_id", "4920")
            }
        }
    }

    "Download from Git can be triggered" {
        service.downloadFromGit("", "", SCAN_CODE) shouldNotBeNull {
            checkResponse("download data from Git", false)
        }
    }

    "Download status can be queried" {
        service.checkDownloadStatus("", "", SCAN_CODE) shouldNotBeNull {
            checkResponse("check download status")
            data shouldBe DownloadStatus.FINISHED
        }
    }

    "A scan can be run" {
        service.runScan("", "", SCAN_CODE) shouldNotBeNull {
            checkResponse("trigger scan", false)
        }
    }

    "A scan can be deleted" {
        service.deleteScan("", "", SCAN_CODE) shouldNotBeNull {
            checkResponse("delete scan")

            data shouldBe 2976
            message shouldContain "has been deleted"
        }
    }

    "Scan status can be queried" {
        service.checkScanStatus("", "", SCAN_CODE) shouldNotBeNull {
            checkResponse("get scan status")

            data shouldNotBeNull {
                state shouldBe ScanStatus.FINISHED
            }
        }
    }

    "Scan results can be listed" {
        service.listScanResults("", "", SCAN_CODE) shouldNotBeNull {
            checkResponse("list scan results")
            data shouldNotBeNull {
                size shouldBe 58
                last().localPath shouldBe "pom.xml"
            }
        }
    }

    "Identified files can be listed" {
        service.listIdentifiedFiles("", "", SCAN_CODE) shouldNotBeNull {
            checkResponse("list identified files")
            data shouldNotBeNull {
                size shouldBe 40
                last().should {
                    it.file.shouldNotBeNull {
                        path shouldBe "LICENSE.md"
                        licenseIdentifier shouldBe "MIT"
                        licenseIsFoss shouldBe true
                        licenseIsCopyleft shouldBe true
                    }

                    it.identificationCopyright shouldBe "• David Gundersen (2016)\n"
                }
            }
        }
    }

    "Marked files can be listed when there are none" {
        service.listMarkedAsIdentifiedFiles("", "", SCAN_CODE) shouldNotBeNull {
            checkResponse("list marked as identified files")
            data shouldNotBeNull {
                this should beEmpty()
            }
        }
    }

    "Ignored files can be listed" {
        service.listIgnoredFiles("", "", SCAN_CODE) shouldNotBeNull {
            checkResponse("list ignored files")
            data shouldNotBeNull {
                size shouldBe 32
                first() should { ignoredFile ->
                    ignoredFile.path shouldBe ".git/hooks/fsmonitor-watchman.sample"
                    ignoredFile.reason shouldBe "Directory rule (.git)"
                }
            }
        }
    }

    "Pending files can be listed" {
        service.listPendingFiles("", "", SCAN_CODE) shouldNotBeNull {
            checkResponse("list pending files")
            data shouldNotBeNull {
                size shouldBe 2
                first() shouldBe "src/extra_file.txt"
            }
        }
    }
})
