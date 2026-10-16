/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.scanners.scanoss

import com.scanoss.rest.ScanApi

import io.kotest.core.annotation.Condition
import io.kotest.core.annotation.EnabledIf
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

import kotlin.reflect.KClass

import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.PackageType
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.plugins.api.Secret
import org.ossreviewtoolkit.plugins.scanners.scanoss.ScanOssConfig.ScanFlag
import org.ossreviewtoolkit.scanner.ScanContext
import org.ossreviewtoolkit.utils.spdxexpression.toSpdx
import org.ossreviewtoolkit.utils.test.extractResource

private val ORT_PURLS_TO_IGNORE = listOf(
    "pkg:github/oss-review-toolkit/ort",
    "pkg:maven/org.ossreviewtoolkit/reporter",
    "pkg:golang/github.com/oss-review-toolkit/ort",
    "pkg:golang/github.com/heremaps/oss-review-toolkit",
    "pkg:github/heremaps/oss-review-toolkit",
    "pkg:codeberg/oss-review-toolkit/ort"
)

@EnabledIf(CloudCheck::class)
class ScanOssFunTest : StringSpec({
    val flags = listOf(ScanFlag.ENABLE_DOWNLOAD_URL, ScanFlag.ENABLE_GITHUB_FULL_PATH)

    val scanner = CloudCheck.getScanOssApiKey()?.let {
        ScanOssFactory.create(ScanApi.DEFAULT_BASE_URL2, Secret(it), flags = flags, ignorePurls = ORT_PURLS_TO_IGNORE)
    } ?: CloudCheck.getOsskbApiKey()?.let {
        ScanOssFactory.create(ScanApi.DEFAULT_BASE_URL, Secret(it), flags = flags, ignorePurls = ORT_PURLS_TO_IGNORE)
    } ?: ScanOssFactory.create(flags = flags, ignorePurls = ORT_PURLS_TO_IGNORE)

    val scanContext = ScanContext(labels = emptyMap(), packageType = PackageType.PACKAGE)

    "File matches contain the expected findings" {
        val unoconv = extractResource("/unoconv")

        val summary = scanner.scanPath(unoconv, scanContext)

        summary.licenseFindings should beEmpty()
        summary.copyrightFindings should beEmpty()

        summary.snippetFindings shouldBeSingleton { finding ->
            finding.snippets shouldBeSingleton { snippet ->
                snippet.score shouldBe 100.0f
                snippet.location.path shouldBe "unoconv"
                snippet.provenance.shouldBeInstanceOf<RepositoryProvenance>().vcsInfo.url shouldBe
                    "https://github.com/unoconv/unoconv.git"
                snippet.purl shouldBe "pkg:github/unoconv/unoconv"
                snippet.license shouldBe "GPL-2.0-only".toSpdx()
                snippet.additionalData shouldContainExactly buildMap {
                    put("component", "unoconv")
                    put("vendor", "unoconv")
                    put("version", "0.8.2")
                    put("latest", "0.8.2")
                    put("status", "pending")
                    put("file_hash", "0f55e083dcc72a11334eb1a77137e2c4")

                    // Copyrights (and vulnerabilities) are commercial features.
                    if (CloudCheck.getScanOssApiKey() != null) {
                        put("file_url", "https://api.scanoss.com/file_contents/0f55e083dcc72a11334eb1a77137e2c4")
                        put("copyright_details", "Copyright 2007-2010 Dag Wieers <dag@wieers.com>")
                    }

                    put("url_hash", "b53576ddfc63b441ef55998f2f97db8e")
                    put("release_date", "2017-12-07")
                    put("source_hash", "0f55e083dcc72a11334eb1a77137e2c4")
                }
            }
        }
    }

    "Snippet matches contain the expected findings" {
        val unoconv = extractResource("/unoconv-snippet").apply {
            appendText("\n# A comment to make the contents differ from the committed file.\n")
        }

        val md5 = HashAlgorithm.MD5.calculate(unoconv)

        val summary = scanner.scanPath(unoconv, scanContext)

        summary.licenseFindings should beEmpty()
        summary.copyrightFindings should beEmpty()

        summary.snippetFindings shouldBeSingleton {
            it.snippets shouldBeSingleton { snippet ->
                snippet.score shouldBe 93.0f
                snippet.location shouldBe TextLocation("unoconv-0.6/unoconv", 19, 186)
                snippet.provenance shouldBe RepositoryProvenance(
                    vcsInfo = VcsInfo(VcsType.GIT, "https://github.com/unoconv/unoconv.git", ""),
                    resolvedRevision = "."
                )
                snippet.purl shouldBe "pkg:github/unoconv/unoconv"
                snippet.license shouldBe "GPL-2.0-only".toSpdx()
                snippet.additionalData shouldContainExactly buildMap {
                    put("component", "unoconv")
                    put("vendor", "unoconv")
                    put("version", "0.6")
                    put("latest", "0.6")
                    put("status", "pending")
                    put("file_hash", "38e743a8566d3df4a2dc4432f8d6b091")

                    // Copyrights (and vulnerabilities) are commercial features.
                    if (CloudCheck.getScanOssApiKey() != null) {
                        put("file_url", "https://api.scanoss.com/file_contents/38e743a8566d3df4a2dc4432f8d6b091")
                        put("copyright_details", "Copyright 2007-2010 Dag Wieers <dag@wieers.com>")
                    }

                    put("url_hash", "2b5b8e4c1c62f2b3cba48ceabc1f3671")
                    put("release_date", "2012-09-10")
                    put("source_hash", md5)
                }
            }
        }
    }
})

internal object CloudCheck : Condition {
    fun getOsskbApiKey(): String? = System.getenv("OSSKB_API_KEY")?.takeUnless { it.isBlank() }
    fun getScanOssApiKey(): String? = System.getenv("SCANOSS_API_KEY")?.takeUnless { it.isBlank() }

    override fun evaluate(kclass: KClass<out Spec>): Boolean =
        System.getenv("CI") != "true" || getOsskbApiKey() != null || getScanOssApiKey() != null
}
