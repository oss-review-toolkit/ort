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

import kotlin.reflect.KClass

import org.ossreviewtoolkit.model.PackageType
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.plugins.api.Secret
import org.ossreviewtoolkit.scanner.ScanContext
import org.ossreviewtoolkit.utils.spdx.toSpdx
import org.ossreviewtoolkit.utils.test.extractResource

@EnabledIf(CloudCheck::class)
class ScanOssFunTest : StringSpec({
    val scanner = CloudCheck.getOsskbApiKey()?.let { ScanOssFactory.create(ScanApi.DEFAULT_BASE_URL, Secret(it)) } ?:
        CloudCheck.getScanOssApiKey()?.let { ScanOssFactory.create(ScanApi.DEFAULT_BASE_URL2, Secret(it)) } ?:
        ScanOssFactory.create()

    val scanContext = ScanContext(labels = emptyMap(), packageType = PackageType.PACKAGE)

    "File matches contain the expected findings" {
        val unoconv = extractResource("/unoconv")

        val summary = scanner.scanPath(unoconv, scanContext)

        summary.licenseFindings.shouldBeSingleton {
            it.license shouldBe "GPL-2.0-only".toSpdx()
            it.score shouldBe 100.0f
        }

        summary.snippetFindings should beEmpty()

        // Copyrights (and vulnerabilities) are commercial features.
        if (CloudCheck.getScanOssApiKey() != null) {
            summary.copyrightFindings.shouldBeSingleton {
                it.statement shouldBe "Copyright 2007-2010 Dag Wieers <dag@wieers.com>"
            }
        }
    }

    "Snippet matches contain the expected findings" {
        val unoconv = extractResource("/unoconv-snippet")

        val summary = scanner.scanPath(unoconv, scanContext)

        summary.licenseFindings should beEmpty()
        summary.copyrightFindings should beEmpty()

        summary.snippetFindings.shouldBeSingleton {
            it.snippets.shouldBeSingleton { snippet ->
                snippet.score shouldBe 95.0f
                snippet.location shouldBe TextLocation("unoconv-0.6/unoconv", 19, 186)
                snippet.provenance shouldBe RepositoryProvenance(
                    vcsInfo = VcsInfo(VcsType.GIT, "https://github.com/unoconv/unoconv.git", ""),
                    resolvedRevision = "."
                )
                snippet.purl shouldBe "pkg:github/unoconv/unoconv"
                snippet.license shouldBe "GPL-2.0-only".toSpdx()
                snippet.additionalData shouldContainExactly if (CloudCheck.getScanOssApiKey() != null) {
                    mapOf(
                        "file_hash" to "38e743a8566d3df4a2dc4432f8d6b091",
                        "file_url" to "https://api.scanoss.com/file_contents/38e743a8566d3df4a2dc4432f8d6b091",
                        "source_hash" to "21f8df5092922255fd8b42be5e6b59a7"
                    )
                } else {
                    mapOf(
                        "file_hash" to "38e743a8566d3df4a2dc4432f8d6b091",
                        "source_hash" to "21f8df5092922255fd8b42be5e6b59a7"
                    )
                }
            }
        }
    }
})

internal object CloudCheck : Condition {
    fun getOsskbApiKey(): String? = System.getenv("OSSKB_API_KEY")
    fun getScanOssApiKey(): String? = System.getenv("SCANOSS_API_KEY")

    override fun evaluate(kclass: KClass<out Spec>): Boolean =
        System.getenv("CI") != "true" || getOsskbApiKey() != null || getScanOssApiKey() != null
}
