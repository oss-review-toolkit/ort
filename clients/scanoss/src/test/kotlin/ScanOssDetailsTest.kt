/*
 * Copyright (C) 2022 Bosch.IO GmbH
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
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveKey
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import java.io.File

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody

import org.ossreviewtoolkit.clients.scanoss.ScanOssService
import org.ossreviewtoolkit.clients.scanoss.model.Source
import org.ossreviewtoolkit.utils.test.shouldNotBeNull

private const val SCANOSS_DETAILS_RESPONSE_FILENAME = "osskb.c"

class ScanOssDetailsTest : StringSpec({
    val server = WireMockServer(
        WireMockConfiguration.options()
            .dynamicPort()
            .usingFilesUnderDirectory("src/test/assets/details")
    )
    lateinit var service: ScanOssService

    val sampleFile = File("src/test/assets/scan/file.wfp").let { file ->
        MultipartBody.Part.createFormData(
            "file",
            file.name,
            file.asRequestBody("application/octet-stream".toMediaType())
        )
    }

    beforeSpec {
        server.start()
        service = ScanOssService.create("http://localhost:${server.port()}")
    }

    afterSpec {
        server.stop()
    }

    beforeTest {
        server.resetAll()
    }

    "The response details can be parsed" {
        val result = service.scan(sampleFile)
        result shouldHaveKey SCANOSS_DETAILS_RESPONSE_FILENAME
        result[SCANOSS_DETAILS_RESPONSE_FILENAME] shouldNotBeNull {
            this shouldNot beEmpty()
            first() shouldNotBeNull {
                dependencies should beEmpty()
                copyrights shouldHaveSize 2
                copyrights.first() shouldNotBeNull {
                    source shouldBe Source.FILE_HEADER
                }
                vulnerabilities shouldHaveSize 1
                vulnerabilities.first() shouldNotBeNull {
                    id shouldBe "GHSA-27p5-7cw6-m45h"
                }
                quality shouldHaveSize 1
                quality.first() shouldNotBeNull {
                    score shouldBe "2/5"
                }
                cryptography should beEmpty()
            }
        }
    }
})
