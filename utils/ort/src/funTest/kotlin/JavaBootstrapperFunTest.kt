/*
 * Copyright (C) 2024 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.utils.ort

import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockkObject

import okhttp3.Cache

import org.ossreviewtoolkit.clients.foojay.DiscoService
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.mebibytes

class JavaBootstrapperFunTest : StringSpec({
    "The Java version running the test should be detected as a JDK" {
        JavaBootstrapper.isRunningOnJdk(Environment.JAVA_VERSION) shouldBe true
    }

    "A JDK for Temurin 21 can be found" {
        val tempCache = Cache(tempdir(), 1.mebibytes)

        val tempCacheClient = OkHttpClientHelper.buildClient {
            cache(tempCache)
        }

        mockkObject(JavaBootstrapper) {
            every { JavaBootstrapper.discoService } returns DiscoService.create(client = tempCacheClient)

            JavaBootstrapper.findJdkPackage("TEMURIN", "21") shouldBeSuccess {
                it.distribution shouldBe "temurin"
                it.jdkVersion shouldBe 21
                Os.Name.fromString(it.operatingSystem) shouldBe Os.Name.current
                Os.Arch.fromString(it.architecture) shouldBe Os.Arch.current
            }
        }
    }
})
