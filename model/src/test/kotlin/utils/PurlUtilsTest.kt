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

package org.ossreviewtoolkit.model.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

class PurlUtilsTest : StringSpec({
    "toPackageUrl() should parse a valid PURL" {
        val purl = "pkg:maven/org.apache.commons/io@1.3.4"
        val parsed = purl.toPackageUrl()

        parsed.shouldNotBeNull()
        parsed.type shouldBe "maven"
        parsed.namespace shouldBe "org.apache.commons"
        parsed.name shouldBe "io"
        parsed.version shouldBe "1.3.4"
    }

    "toPackageUrl() should return null for invalid input" {
        null.toPackageUrl() should beNull()
        "".toPackageUrl() should beNull()
        "not-a-purl".toPackageUrl() should beNull()
    }

    "getPurlType() should return the correct PurlType" {
        val purl = "pkg:maven/org.example/foo@1.0".toPackageUrl()

        purl.shouldNotBeNull()
        purl.getPurlType() shouldBe PurlType.MAVEN
    }

    "getPurlType() should return null for unknown types" {
        val purl = "pkg:unknown/foo@1.0".toPackageUrl()

        purl.shouldNotBeNull()
        purl.getPurlType() should beNull()
    }
})
