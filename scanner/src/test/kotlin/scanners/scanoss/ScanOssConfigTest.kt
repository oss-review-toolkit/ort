/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.scanner.scanners.scanoss

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.beEmpty

import org.ossreviewtoolkit.model.config.ScannerConfiguration

class ScanOssConfigTest : StringSpec({
    "Default values are used" {
        with(ScanOssConfig.create(ScannerConfiguration())) {
            apiUrl shouldBe ScanOssConfig.DEFAULT_API_URL
            apiKey should beEmpty()
            ignoredFileSuffixes shouldBe ScanOssConfig.DEFAULT_IGNORED_FILE_SUFFIXES
        }
    }

    "Default values can be overridden" {
        val scanOssOptions = mapOf(
            ScanOssConfig.API_URL_PROPERTY to "url",
            ScanOssConfig.API_KEY_PROPERTY to "key",
            ScanOssConfig.IGNORED_FILE_SUFFIXES_PROPERTY to ".ext, -suffix"
        )

        val config = ScannerConfiguration(options = mapOf("ScanOss" to scanOssOptions))

        with(ScanOssConfig.create(config)) {
            apiUrl shouldBe "url"
            apiKey shouldBe "key"
            ignoredFileSuffixes shouldBe listOf(".ext", "-suffix")
        }
    }
})
