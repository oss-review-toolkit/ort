/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.clients.fossid

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.types.shouldBeTypeOf

import org.ossreviewtoolkit.clients.fossid.model.Scan
import org.ossreviewtoolkit.clients.fossid.model.identification.common.LicenseMatchType
import org.ossreviewtoolkit.clients.fossid.model.identification.identifiedFiles.IdentifiedFile
import org.ossreviewtoolkit.clients.fossid.model.identification.ignored.IgnoredFile
import org.ossreviewtoolkit.clients.fossid.model.identification.markedAsIdentified.MarkedAsIdentifiedFile
import org.ossreviewtoolkit.clients.fossid.model.result.FossIdScanResult
import org.ossreviewtoolkit.clients.fossid.model.result.Snippet

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
        service = FossIdRestService.create("http://localhost:${server.port()}")
    }

    afterSpec {
        server.stop()
    }

    beforeTest {
        server.resetAll()
    }

    "Single scan can be queried" {
        service.getScan("", "", SCAN_CODE_2).shouldNotBeNull {
            checkResponse("get scan")
            data.shouldBeTypeOf<Scan>()
        }
    }

    "Scans for project can be listed when there is none" {
        service.listScansForProject("", "", PROJECT_CODE_1).shouldNotBeNull {
            checkResponse("list scans")
            data.shouldNotBeNull() should beEmpty()
        }
    }

    "Scans for project can be listed when there is exactly one" {
        service.listScansForProject("", "", PROJECT_CODE_3).shouldNotBeNull {
            checkResponse("list scans")
            data.shouldNotBeNull {
                size shouldBe 1
                first().shouldBeTypeOf<Scan>()
            }
        }
    }

    "Scans for project can be listed when there is some" {
        service.listScansForProject("", "", PROJECT_CODE_2).shouldNotBeNull {
            checkResponse("list scans")
            data.shouldNotBeNull {
                this shouldNot beEmpty()
                first().shouldBeTypeOf<Scan>()
            }
        }
    }

    "Scan results can be listed when there is none" {
        service.listScanResults("", "", SCAN_CODE_1).shouldNotBeNull {
            checkResponse("list scan results")
            data.shouldNotBeNull() should beEmpty()
        }
    }

    "Scan results can be listed when there is some" {
        service.listScanResults("", "", SCAN_CODE_2).shouldNotBeNull {
            checkResponse("list scan results")
            data.shouldNotBeNull {
                this shouldNot beEmpty()
                forEach {
                    it.shouldBeTypeOf<FossIdScanResult>()
                }
            }
        }
    }

    "Snippets can be listed when there is some" {
        service.listSnippets(
            "",
            "",
            SCAN_CODE_2,
            "src/main/java/com/vdurmont/semver4j/Requirement.java"
        ).shouldNotBeNull {
            checkResponse("list snippets")
            data.shouldNotBeNull {
                this shouldNot beEmpty()
                forEach {
                    it.shouldBeTypeOf<Snippet>()
                }
            }
        }
    }

    "Ignored snippets can be mapped" {
        service.listSnippets(
            "",
            "",
            SCAN_CODE_1,
            "src/main/java/com/vdurmont/semver4j/Requirement.java"
        ).shouldNotBeNull {
            checkResponse("list snippets")
            data.shouldNotBeNull {
                this shouldNot beEmpty()
                forEach {
                    it.shouldBeTypeOf<Snippet>()
                }
            }
        }
    }

    "Matched lines can be listed" {
        service.listMatchedLines(
            "",
            "",
            SCAN_CODE_2,
            "src/main/java/com/vdurmont/semver4j/Requirement.java",
            119
        ).shouldNotBeNull {
            checkResponse("list matched lines")
            data.shouldNotBeNull {
                localFile shouldNot beEmpty()
                mirrorFile shouldNot beEmpty()
            }
        }
    }

    "Identified files can be listed when there is none" {
        service.listIdentifiedFiles("", "", SCAN_CODE_1).shouldNotBeNull {
            checkResponse("list identified files")
            data.shouldNotBeNull() should beEmpty()
        }
    }

    "Identified files can be listed when there is some" {
        service.listIdentifiedFiles("", "", SCAN_CODE_2).shouldNotBeNull {
            checkResponse("list identified files")
            data.shouldNotBeNull {
                this shouldNot beEmpty()
                forEach {
                    it.shouldBeTypeOf<IdentifiedFile>()
                }
            }
        }
    }

    "Marked as identified  files can be listed when there is none" {
        service.listMarkedAsIdentifiedFiles("", "", SCAN_CODE_1).shouldNotBeNull {
            checkResponse("list marked as identified files")
            data.shouldNotBeNull() should beEmpty()
        }
    }

    "Marked as identified  files can be listed when there is some" {
        service.listMarkedAsIdentifiedFiles("", "", SCAN_CODE_2).shouldNotBeNull {
            checkResponse("list marked as identified files")
            data.shouldNotBeNull {
                this shouldNot beEmpty()
                forEach {
                    it.shouldBeTypeOf<MarkedAsIdentifiedFile>()
                }
            }
        }
    }

    "Ignored files can be listed when there is none" {
        service.listIgnoredFiles("", "", SCAN_CODE_1).shouldNotBeNull {
            checkResponse("list ignored files")
            data.shouldNotBeNull() should beEmpty()
        }
    }

    "Ignored files can be listed when there is some" {
        service.listIgnoredFiles("", "", SCAN_CODE_2).shouldNotBeNull {
            checkResponse("list ignored files")
            data.shouldNotBeNull {
                this shouldNot beEmpty()
                forEach {
                    it.shouldBeTypeOf<IgnoredFile>()
                }
            }
        }
    }

    "Pending files can be listed when there is none" {
        service.listPendingFiles("", "", SCAN_CODE_1).shouldNotBeNull {
            checkResponse("list pending files")
            data.shouldNotBeNull() should beEmpty()
        }
    }

    "Pending files can be listed when there is some" {
        service.listPendingFiles("", "", SCAN_CODE_2).shouldNotBeNull {
            checkResponse("list pending files")
            data.shouldNotBeNull {
                this shouldNot beEmpty()
                forEach {
                    it.shouldBeTypeOf<String>()
                }
            }
        }
    }

    "When the scan to delete does not exist, no exception is thrown" {
        service.deleteScan("", "", SCAN_CODE_1).shouldNotBeNull {
            error shouldBe "Classes.TableRepository.row_not_found"
        }
    }

    "A file can be marked as identified" {
        service.markAsIdentified(
            "",
            "",
            SCAN_CODE_1,
            "src/main/java/com/vdurmont/semver4j/Range.java",
            false
        ).shouldNotBeNull {
            checkResponse("mark file as identified")
        }
    }

    "A file can be unmarked as identified" {
        service.unmarkAsIdentified(
            "",
            "",
            SCAN_CODE_1,
            "src/main/java/com/vdurmont/semver4j/Range.java",
            false
        ).shouldNotBeNull {
            checkResponse("unmark file as identified")
        }
    }

    "A license identification can be added to a file" {
        service.addLicenseIdentification(
            "",
            "",
            SCAN_CODE_1,
            "src/main/java/com/vdurmont/semver4j/Range.java",
            "Apache-2.0",
            LicenseMatchType.SNIPPET,
            false
        ).shouldNotBeNull {
            checkResponse("add license identification")
        }
    }

    "A component identification can be added to a file" {
        service.addComponentIdentification(
            "",
            "",
            SCAN_CODE_1,
            "src/main/java/com/vdurmont/semver4j/Range.java",
            "semver4j",
            "3.0.0",
            isDirectory = false,
            preserveExistingIdentifications = false
        ).shouldNotBeNull {
            checkResponse("add component identification")
        }
    }

    "A comment can be added to a file" {
        service.addFileComment(
            "",
            "",
            SCAN_CODE_1,
            "src/main/java/com/vdurmont/semver4j/Range.java",
            "TestORT"
        ).shouldNotBeNull {
            checkResponse("add file comment")
        }
    }
})
