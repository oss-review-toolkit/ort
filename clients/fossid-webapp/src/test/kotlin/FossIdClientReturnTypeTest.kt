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

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.types.shouldBeTypeOf

import org.ossreviewtoolkit.clients.fossid.FossIdRestService
import org.ossreviewtoolkit.clients.fossid.checkResponse
import org.ossreviewtoolkit.clients.fossid.deleteScan
import org.ossreviewtoolkit.clients.fossid.listIdentifiedFiles
import org.ossreviewtoolkit.clients.fossid.listIgnoredFiles
import org.ossreviewtoolkit.clients.fossid.listMarkedAsIdentifiedFiles
import org.ossreviewtoolkit.clients.fossid.listPendingFiles
import org.ossreviewtoolkit.clients.fossid.listScanResults
import org.ossreviewtoolkit.clients.fossid.listScansForProject
import org.ossreviewtoolkit.clients.fossid.model.Scan
import org.ossreviewtoolkit.clients.fossid.model.identification.identifiedFiles.IdentifiedFile
import org.ossreviewtoolkit.clients.fossid.model.identification.ignored.IgnoredFile
import org.ossreviewtoolkit.clients.fossid.model.identification.markedAsIdentified.MarkedAsIdentifiedFile
import org.ossreviewtoolkit.clients.fossid.model.result.FossIdScanResult

private const val PROJECT_CODE_1 = "semver4j"
private const val PROJECT_CODE_2 = "semver4j_2"
private const val PROJECT_CODE_3 = "semver4j_3"
private const val SCAN_CODE_1 = "${PROJECT_CODE_1}_20201203_090342"
private const val SCAN_CODE_2 = "${PROJECT_CODE_2}_20201203_090342"

/**
 * The FossID server is not consistent.
 * It usually returns a List or a Map, but if there is no entry, it returns data:false.
 * This class tests the abstraction provided by the custom Jackson deserializer wired in
 * [FossIdRestService.JSON_MAPPER].
 */
class FossIdClientReturnTypeTest : StringSpec({
    val server = WireMockServer(
        WireMockConfiguration.options()
            .dynamicPort()
            .usingFilesUnderDirectory("src/test/assets/return-type")
    )
    lateinit var service: FossIdRestService

    beforeSpec {
        server.start()
        service = FossIdRestService.createService("http://localhost:${server.port()}")
    }

    afterSpec {
        server.stop()
    }

    beforeTest {
        server.resetAll()
    }

    "Scans for project can be listed when there is none" {
        service.listScansForProject("", "", PROJECT_CODE_1).shouldNotBeNull().run {
            checkResponse("list scans")
            data.shouldNotBeNull() should beEmpty()
        }
    }

    "Scans for project can be listed when there is exactly one" {
        service.listScansForProject("", "", PROJECT_CODE_3).shouldNotBeNull().run {
            checkResponse("list scans")
            data.shouldNotBeNull().run {
                size shouldBe 1
                first().shouldBeTypeOf<Scan>()
            }
        }
    }

    "Scans for project can be listed when there is some" {
        service.listScansForProject("", "", PROJECT_CODE_2).shouldNotBeNull().run {
            checkResponse("list scans")
            data.shouldNotBeNull().run {
                this shouldNot beEmpty()
                first().shouldBeTypeOf<Scan>()
            }
        }
    }

    "Scan results can be listed when there is none" {
        service.listScanResults("", "", SCAN_CODE_1).shouldNotBeNull().run {
            checkResponse("list scan results")
            data.shouldNotBeNull() should beEmpty()
        }
    }

    "Scan results can be listed when there is some" {
        service.listScanResults("", "", SCAN_CODE_2).shouldNotBeNull().run {
            checkResponse("list scan results")
            data.shouldNotBeNull().run {
                this shouldNot beEmpty()
                forEach {
                    it.shouldBeTypeOf<FossIdScanResult>()
                }
            }
        }
    }

    "Identified files can be listed when there is none" {
        service.listIdentifiedFiles("", "", SCAN_CODE_1).shouldNotBeNull().run {
            checkResponse("list identified files")
            data.shouldNotBeNull() should beEmpty()
        }
    }

    "Identified files can be listed when there is some" {
        service.listIdentifiedFiles("", "", SCAN_CODE_2).shouldNotBeNull().run {
            checkResponse("list identified files")
            data.shouldNotBeNull().run {
                this shouldNot beEmpty()
                forEach {
                    it.shouldBeTypeOf<IdentifiedFile>()
                }
            }
        }
    }

    "Marked as identified  files can be listed when there is none" {
        service.listMarkedAsIdentifiedFiles("", "", SCAN_CODE_1).shouldNotBeNull().run {
            checkResponse("list marked as identified files")
            data.shouldNotBeNull() should beEmpty()
        }
    }

    "Marked as identified  files can be listed when there is some" {
        service.listMarkedAsIdentifiedFiles("", "", SCAN_CODE_2).shouldNotBeNull().run {
            checkResponse("list marked as identified files")
            data.shouldNotBeNull().run {
                this shouldNot beEmpty()
                forEach {
                    it.shouldBeTypeOf<MarkedAsIdentifiedFile>()
                }
            }
        }
    }

    "Ignored files can be listed when there is none" {
        service.listIgnoredFiles("", "", SCAN_CODE_1).shouldNotBeNull().run {
            checkResponse("list ignored files")
            data.shouldNotBeNull() should beEmpty()
        }
    }

    "Ignored files can be listed when there is some" {
        service.listIgnoredFiles("", "", SCAN_CODE_2).shouldNotBeNull().run {
            checkResponse("list ignored files")
            data.shouldNotBeNull().run {
                this shouldNot beEmpty()
                forEach {
                    it.shouldBeTypeOf<IgnoredFile>()
                }
            }
        }
    }

    "Pending files can be listed when there is none" {
        service.listPendingFiles("", "", SCAN_CODE_1).shouldNotBeNull().run {
            checkResponse("list pending files")
            data.shouldNotBeNull() should beEmpty()
        }
    }

    "Pending files can be listed when there is some" {
        service.listPendingFiles("", "", SCAN_CODE_2).shouldNotBeNull().run {
            checkResponse("list pending files")
            data.shouldNotBeNull().run {
                this shouldNot beEmpty()
                forEach {
                    it.shouldBeTypeOf<String>()
                }
            }
        }
    }

    "When the scan to delete does not exist, no exception is thrown" {
        service.deleteScan("", "", SCAN_CODE_1).shouldNotBeNull().run {
            error shouldBe "Classes.TableRepository.row_not_found"
        }
    }
})
