/*
 * Copyright (C) 2025 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

class ParsePurlTest : StringSpec({
    "parse a simple PURL" {
        val purl = "pkg:hex/cowboy@2.9.0"
        val parsed = parsePurl(purl)

        parsed.shouldNotBeNull()
        parsed.type shouldBe "hex"
        parsed.name shouldBe "cowboy"
        parsed.version shouldBe "2.9.0"
        parsed.namespace shouldBe ""
        parsed.qualifiers shouldBe emptyMap()
        parsed.subpath shouldBe ""
    }

    "parse PURL with namespace" {
        val purl = "pkg:maven/org.apache.commons/commons-lang3@3.12.0"
        val parsed = parsePurl(purl)

        parsed.shouldNotBeNull()
        parsed.type shouldBe "maven"
        parsed.namespace shouldBe "org.apache.commons"
        parsed.name shouldBe "commons-lang3"
        parsed.version shouldBe "3.12.0"
    }

    "parse PURL with qualifiers" {
        val purl = "pkg:deb/debian/curl@7.50.3-1?arch=i386&distro=jessie"
        val parsed = parsePurl(purl)

        parsed.shouldNotBeNull()
        parsed.type shouldBe "deb"
        parsed.name shouldBe "curl"
        parsed.version shouldBe "7.50.3-1"
        parsed.qualifiers shouldBe mapOf("arch" to "i386", "distro" to "jessie")
    }

    "parse PURL with subpath" {
        val purl = "pkg:golang/google.golang.org/genproto#googleapis/api/annotations"
        val parsed = parsePurl(purl)

        parsed.shouldNotBeNull()
        parsed.type shouldBe "golang"
        parsed.name shouldBe "genproto"
        parsed.subpath shouldBe "googleapis/api/annotations"
    }

    "parse PURL with qualifiers and subpath" {
        val purl = "pkg:npm/foo@1.0.0?repository_url=https://example.com#dist/foo.js"
        val parsed = parsePurl(purl)

        parsed.shouldNotBeNull()
        parsed.type shouldBe "npm"
        parsed.name shouldBe "foo"
        parsed.version shouldBe "1.0.0"
        parsed.qualifiers["repository_url"] shouldBe "https://example.com"
        parsed.subpath shouldBe "dist/foo.js"
    }

    "handle URL-encoded components" {
        val purl = "pkg:hex/my%20app@1.0.0"
        val parsed = parsePurl(purl)

        parsed.shouldNotBeNull()
        parsed.name shouldBe "my app"
    }

    "handle checksum version format" {
        val purl = "pkg:hex/cowboy@sha256:abc123def456"
        val parsed = parsePurl(purl)

        parsed.shouldNotBeNull()
        parsed.version shouldBe "sha256:abc123def456"
    }

    "return null for invalid PURLs" {
        parsePurl(null) should beNull()
        parsePurl("") should beNull()
        parsePurl("not-a-purl") should beNull()
        parsePurl("pkg:") should beNull()
        parsePurl("pkg:/name@version") should beNull()
        parsePurl("pkg:type/@version") should beNull()
    }

    "handle PURL without version" {
        val purl = "pkg:hex/cowboy"
        val parsed = parsePurl(purl)

        parsed.shouldNotBeNull()
        parsed.type shouldBe "hex"
        parsed.name shouldBe "cowboy"
        parsed.version shouldBe ""
    }

    "handle qualifiers without equals sign" {
        val purl = "pkg:hex/cowboy@2.9.0?invalid-qualifier"
        val parsed = parsePurl(purl)

        parsed.shouldNotBeNull()
        parsed.qualifiers["invalid-qualifier"] shouldBe ""
    }

    "handle empty qualifier values" {
        val purl = "pkg:hex/cowboy@2.9.0?key="
        val parsed = parsePurl(purl)

        parsed.shouldNotBeNull()
        parsed.qualifiers["key"] shouldBe ""
    }
})
