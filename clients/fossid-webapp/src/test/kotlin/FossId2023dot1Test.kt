/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockkObject

private const val PROJECT_CODE = "semver4j"
private const val SCAN_CODE_2021_2 = "${PROJECT_CODE}_20201203_090342"

/**
 * This client test tests the calls that have been changed for FossID 2023.1.
 * This version of FossID changed the return type of the deleteScan function: it was before an integer, and it is now an
 * map of strings to strings. Creating a special [FossIdServiceWithVersion] implementation for this call is an overkill
 * as ORT does not even use the return value. Therefore, this change is handled by the Jackson deserializer.
 */
class FossId2023dot1Test : StringSpec({
    val server = WireMockServer(
        WireMockConfiguration.options()
            .dynamicPort()
            .usingFilesUnderClasspath("2023.1")
    )
    lateinit var service: FossIdServiceWithVersion

    beforeSpec {
        server.start()

        mockkObject(FossIdServiceWithVersion.Companion)
        coEvery { FossIdServiceWithVersion.Companion.create(any()) } answers {
            VersionedFossIdService2021dot2(firstArg(), "2023.2.0")
        }

        service = FossIdRestService.create("http://localhost:${server.port()}")
    }

    afterSpec {
        server.stop()
        clearAllMocks()
    }

    beforeTest {
        server.resetAll()
    }

    "Version can be parsed of login page (2023.1)" {
        service.version shouldBe "2023.2.0"
        service should beInstanceOf<VersionedFossIdService2021dot2>()
    }

    "Delete scan response can be parsed (2023.1)" {
        // Recreate the version as the service caches it.
        service = FossIdServiceWithVersion.create(service)

        service.deleteScan("", "", SCAN_CODE_2021_2) shouldNotBeNull {
            checkResponse("delete scan")

            data.shouldNotBeNull().value shouldBe 522415
        }
    }
})
