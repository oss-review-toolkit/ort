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
import org.ossreviewtoolkit.utils.spdxexpression.toSpdx
import org.ossreviewtoolkit.utils.test.extractResource

@EnabledIf(CloudCheck::class)
class ScanOssFunTest : StringSpec({
    val scanner = CloudCheck.getOsskbApiKey()?.let { ScanOssFactory.create(ScanApi.DEFAULT_BASE_URL, Secret(it)) } ?:
        CloudCheck.getScanOssApiKey()?.let { ScanOssFactory.create(ScanApi.DEFAULT_BASE_URL2, Secret(it)) } ?:
        ScanOssFactory.create()

    val scanContext = ScanContext(labels = emptyMap(), packageType = PackageType.PACKAGE)

    "Pending file matches create snippet findings" {
        val unoconv = extractResource("/unoconv")

        val summary = scanner.scanPath(unoconv, scanContext)

        summary.licenseFindings should beEmpty()
        summary.copyrightFindings should beEmpty()

        summary.snippetFindings.shouldBeSingleton {
            it.snippets.shouldBeSingleton { snippet ->
                snippet.score shouldBe 100.0f

                // TODO: The below shold point to https://github.com/unoconv/unoconv.git instead of ORT.
                snippet.location shouldBe TextLocation(
                    "plugins/scanners/scanoss/src/funTest/resources/unoconv",
                    TextLocation.UNKNOWN_LINE
                )
                snippet.provenance shouldBe RepositoryProvenance(
                    vcsInfo = VcsInfo(VcsType.GIT, "https://github.com/oss-review-toolkit/ort.git", ""),
                    resolvedRevision = "."
                )
                snippet.purl shouldBe "pkg:github/oss-review-toolkit/ort"

                // TODO: This simply is the project's declared license, which is wriong in case of individual files
                //       in a repository being licensed differently, also see https://reuse.software/.
                snippet.license shouldBe "Apache-2.0".toSpdx()

                snippet.additionalData shouldContainExactly if (CloudCheck.getScanOssApiKey() != null) {
                    mapOf(
                        "component" to "ort",
                        "vendor" to "oss-review-toolkit",
                        "version" to "84.1.0",
                        "latest" to "85.0.0",
                        "file_hash" to "0f55e083dcc72a11334eb1a77137e2c4",
                        "file_url" to "https://api.scanoss.com/file_contents/0f55e083dcc72a11334eb1a77137e2c4",
                        "url_hash" to "4a95cb23b73bb80cfaeab9feb4a286bc",
                        "release_date" to "2026-04-23",
                        "source_hash" to "0f55e083dcc72a11334eb1a77137e2c4",
                        "related_purls" to "pkg:golang/github.com/oss-review-toolkit/ort,pkg:maven/org.ossreviewtoolkit/advisor"
                    )
                } else {
                    mapOf(
                        "component" to "ort",
                        "vendor" to "oss-review-toolkit",
                        "version" to "84.1.0",
                        "latest" to "85.0.0",
                        "file_hash" to "0f55e083dcc72a11334eb1a77137e2c4",
                        "url_hash" to "4a95cb23b73bb80cfaeab9feb4a286bc",
                        "release_date" to "2026-04-23",
                        "source_hash" to "0f55e083dcc72a11334eb1a77137e2c4",
                        "related_purls" to "pkg:golang/github.com/oss-review-toolkit/ort,pkg:maven/org.ossreviewtoolkit/advisor"
                    )
                }
            }
        }
    }

    "Pending snippet matches create findings".config(enabled = false) {
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
                        "component" to "unoconv",
                        "vendor" to "unoconv",
                        "version" to "0.6",
                        "latest" to "0.6",
                        "file_hash" to "38e743a8566d3df4a2dc4432f8d6b091",
                        "file_url" to "https://api.scanoss.com/file_contents/38e743a8566d3df4a2dc4432f8d6b091",
                        "url_hash" to "2b5b8e4c1c62f2b3cba48ceabc1f3671",
                        "release_date" to "2012-09-10",
                        "source_hash" to "21f8df5092922255fd8b42be5e6b59a7"
                    )
                } else {
                    mapOf(
                        "component" to "unoconv",
                        "vendor" to "unoconv",
                        "version" to "0.6",
                        "latest" to "0.6",
                        "file_hash" to "38e743a8566d3df4a2dc4432f8d6b091",
                        "url_hash" to "2b5b8e4c1c62f2b3cba48ceabc1f3671",
                        "release_date" to "2012-09-10",
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
