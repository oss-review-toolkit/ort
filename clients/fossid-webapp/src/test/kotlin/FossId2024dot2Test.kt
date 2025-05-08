/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

private const val PROJECT_CODE_2024 = "semver4j_2024"

/**
 * This client test tests the calls that have been changed for FossID 2024.2.
 */
class FossId2024dot2Test : StringSpec({
    val server = WireMockServer(
        WireMockConfiguration.options()
            .dynamicPort()
            .usingFilesUnderClasspath("2024.2")
    )
    lateinit var service: FossIdServiceWithVersion

    beforeSpec {
        server.start()

        mockkObject(FossIdServiceWithVersion)
        coEvery { FossIdServiceWithVersion.create(any()) } answers {
            VersionedFossIdService2021dot2(firstArg(), "2024.2.1")
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

    "Version can be parsed of login page (2024.2)" {
        service.version shouldBe "2024.2.1"
        service should beInstanceOf<VersionedFossIdService2021dot2>()
    }

    "Get project response can be parsed (2024.2)" {
        // Recreate the version as the service caches it.
        service = FossIdServiceWithVersion.create(service)

        service.getProject("", "", PROJECT_CODE_2024) shouldNotBeNull {
            checkResponse("get project")

            data?.value.shouldNotBeNull().isArchived shouldBe false
        }
    }
})
