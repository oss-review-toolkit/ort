/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.fossid

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject

import org.ossreviewtoolkit.clients.fossid.EntityResponseBody
import org.ossreviewtoolkit.clients.fossid.FossIdRestService
import org.ossreviewtoolkit.clients.fossid.FossIdServiceWithVersion
import org.ossreviewtoolkit.clients.fossid.PolymorphicList
import org.ossreviewtoolkit.clients.fossid.PolymorphicResponseBody
import org.ossreviewtoolkit.clients.fossid.listIdentifiedFiles
import org.ossreviewtoolkit.clients.fossid.listIgnoredFiles
import org.ossreviewtoolkit.clients.fossid.listMarkedAsIdentifiedFiles
import org.ossreviewtoolkit.clients.fossid.listMatchedLines
import org.ossreviewtoolkit.clients.fossid.listPendingFiles
import org.ossreviewtoolkit.clients.fossid.listSnippets
import org.ossreviewtoolkit.clients.fossid.model.identification.markedAsIdentified.MarkedAsIdentifiedFile
import org.ossreviewtoolkit.clients.fossid.model.result.MatchType
import org.ossreviewtoolkit.clients.fossid.model.result.MatchedLines
import org.ossreviewtoolkit.clients.fossid.model.result.Snippet
import org.ossreviewtoolkit.clients.fossid.model.status.ScanStatus
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.config.SnippetChoices
import org.ossreviewtoolkit.model.config.snippet.Choice
import org.ossreviewtoolkit.model.config.snippet.Given
import org.ossreviewtoolkit.model.config.snippet.Provenance
import org.ossreviewtoolkit.model.config.snippet.SnippetChoice
import org.ossreviewtoolkit.model.config.snippet.SnippetChoiceReason

/** A sample file in the results. **/
private const val FILE_1 = "a.java"

/** A sample purl in the results. **/
private const val PURL_1 = "pkg:github/fakeuser/fakepackage1@1.0.0"

/** A sample purl in the results. **/
private const val PURL_2 = "pkg:github/fakeuser/fakepackage2@1.0.0"

/** A sample purl in the results. **/
private const val PURL_3 = "pkg:github/fakeuser/fakepackage3@1.0.0"

class FossIdSnippetChoiceTest : WordSpec({
    beforeSpec {
        mockkStatic("org.ossreviewtoolkit.clients.fossid.ExtensionsKt")
    }

    beforeTest {
        // Here a static function of the companion object is mocked therefore `mockkobject` needs to be used.
        // See https://lifesaver.codes/answer/cannot-mockkstatic-for-kotlin-companion-object-static-method-136
        mockkObject(FossIdRestService)
        mockkObject(VersionControlSystem)

        every { FossIdRestService.create(any()) } returns createServiceMock()
        every { VersionControlSystem.forUrl(any()) } returns createVersionControlSystemMock()
    }

    afterTest {
        unmockkObject(FossIdRestService)
        unmockkObject(VersionControlSystem)
    }

    "scanPackages()" should {
        "not report a snippet that has been chosen" {
            val projectCode = projectCode(PROJECT)
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false, fetchSnippetMatchedLines = true)
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, "${vcsInfo.revision}_other", scanCode)
            val pkgId = createIdentifier(index = 42)

            FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(scanCode, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo, "")
                .expectDownload(scanCode)
                .mockFiles(
                    scanCode,
                    pendingFiles = listOf(FILE_1),
                    snippets = listOf(
                        createSnippet(0, FILE_1, PURL_1),
                        createSnippet(1, FILE_1, PURL_2)
                    ),
                    matchedLines = mapOf(
                        0 to MatchedLines.create((10..20).toList(), (10..20).toList()),
                        1 to MatchedLines.create((20..30).toList(), (20..30).toList())
                    )
                )

            val snippetChoices = createSnippetChoices(
                vcsInfo.url,
                createSnippetChoice(TextLocation(FILE_1, 10, 20), PURL_1, "")
            )
            val fossId = createFossId(config)

            val summary = fossId.scan(createPackage(pkgId, vcsInfo), snippetChoices = snippetChoices).summary

            summary.snippetFindings shouldHaveSize 1
            summary.snippetFindings.first().apply {
                sourceLocation.path shouldBe FILE_1
                snippets shouldHaveSize 1
                snippets.first().apply {
                    purl shouldBe PURL_2
                }
            }
        }

        "not report a snippet when there is a chosen snippet for its location" {
            val projectCode = projectCode(PROJECT)
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false, fetchSnippetMatchedLines = true)
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, "${vcsInfo.revision}_other", scanCode)
            val pkgId = createIdentifier(index = 42)

            FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(scanCode, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo, "")
                .expectDownload(scanCode)
                .mockFiles(
                    scanCode,
                    pendingFiles = listOf(FILE_1),
                    snippets = listOf(
                        createSnippet(0, FILE_1, PURL_1),
                        createSnippet(1, FILE_1, PURL_2),
                        createSnippet(2, FILE_1, PURL_3)
                    ),
                    matchedLines = mapOf(
                        0 to MatchedLines.create((10..20).toList(), (10..20).toList()),
                        1 to MatchedLines.create((10..20).toList(), (10..20).toList()),
                        2 to MatchedLines.create((20..30).toList(), (20..30).toList())
                    )
                )

            val snippetChoices = createSnippetChoices(
                vcsInfo.url,
                createSnippetChoice(TextLocation(FILE_1, 10, 20), PURL_1, "")
            )
            val fossId = createFossId(config)

            val summary = fossId.scan(createPackage(pkgId, vcsInfo), snippetChoices = snippetChoices).summary

            summary.snippetFindings shouldHaveSize 1
            summary.snippetFindings.first().apply {
                sourceLocation.path shouldBe FILE_1
                snippets shouldHaveSize 1
                snippets.first().apply {
                    purl shouldBe PURL_3
                }
            }
        }

        "not report a snippet when there are false positives for its location" {
            val projectCode = projectCode(PROJECT)
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false, fetchSnippetMatchedLines = true)
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, "${vcsInfo.revision}_other", scanCode)
            val pkgId = createIdentifier(index = 42)

            FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(scanCode, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo, "")
                .expectDownload(scanCode)
                .mockFiles(
                    scanCode,
                    pendingFiles = listOf(FILE_1),
                    snippets = listOf(
                        createSnippet(0, FILE_1, PURL_1),
                        createSnippet(1, FILE_1, PURL_2),
                        createSnippet(2, FILE_1, PURL_3)
                    ),
                    matchedLines = mapOf(
                        0 to MatchedLines.create((10..20).toList(), (10..20).toList()),
                        1 to MatchedLines.create((10..20).toList(), (10..20).toList()),
                        2 to MatchedLines.create((20..30).toList(), (20..30).toList())
                    )
                )

            val snippetChoices = createSnippetChoices(
                vcsInfo.url,
                createSnippetChoice(TextLocation(FILE_1, 10, 20), comment = "")
            )
            val fossId = createFossId(config)

            val summary = fossId.scan(createPackage(pkgId, vcsInfo), snippetChoices = snippetChoices).summary

            summary.snippetFindings shouldHaveSize 1
            summary.snippetFindings.first().apply {
                sourceLocation.path shouldBe FILE_1
                snippets shouldHaveSize 1
                snippets.first().apply {
                    purl shouldBe PURL_3
                }
            }
            summary.issues.filter { it.severity > Severity.HINT } should beEmpty()
        }
    }
})

private fun createSnippetChoices(provenanceUrl: String, vararg snippetChoices: SnippetChoice) =
    listOf(SnippetChoices(Provenance(provenanceUrl), snippetChoices.toList()))

private fun createSnippetChoice(location: TextLocation, purl: String? = null, comment: String) =
    SnippetChoice(
        Given(
            location
        ),
        Choice(
            purl,
            if (purl == null) SnippetChoiceReason.NO_RELEVANT_FINDING else SnippetChoiceReason.ORIGINAL_FINDING,
            comment
        )
    )

fun FossIdServiceWithVersion.mockFiles(
    scanCode: String,
    markedFiles: List<MarkedAsIdentifiedFile> = emptyList(),
    pendingFiles: List<String> = emptyList(),
    snippets: List<Snippet> = emptyList(),
    matchedLines: Map<Int, MatchedLines> = emptyMap()
): FossIdServiceWithVersion {
    coEvery { listIdentifiedFiles(USER, API_KEY, scanCode) } returns
        PolymorphicResponseBody(
            status = 1, data = PolymorphicList(emptyList())
        )
    coEvery { listMarkedAsIdentifiedFiles(USER, API_KEY, scanCode) } returns
        PolymorphicResponseBody(
            status = 1, data = PolymorphicList(markedFiles)
        )
    coEvery { listIgnoredFiles(USER, API_KEY, scanCode) } returns
        PolymorphicResponseBody(status = 1, data = PolymorphicList(emptyList()))

    coEvery { listPendingFiles(USER, API_KEY, scanCode) } returns
        PolymorphicResponseBody(status = 1, data = PolymorphicList(pendingFiles))
    coEvery { listSnippets(USER, API_KEY, scanCode, any()) } returns
        PolymorphicResponseBody(status = 1, data = PolymorphicList(snippets))
    if (matchedLines.isNotEmpty()) {
        coEvery { listMatchedLines(USER, API_KEY, scanCode, any(), any()) } answers {
            val lines = matchedLines[arg(5)]
            EntityResponseBody(status = 1, data = lines)
        }
    }

    return this
}

internal fun createSnippet(index: Int, file: String, purl: String): Snippet =
    Snippet(
        index,
        "created$index",
        index,
        index,
        index,
        MatchType.PARTIAL,
        "reason$index",
        "author$index",
        "artifact$index",
        "version$index",
        purl,
        "MIT",
        null,
        "releaseDate$index",
        "mirror$index",
        file,
        "fileLicense$index",
        "url$index",
        "hits$index",
        index,
        "updated$index",
        "cpe$index",
        "$index",
        "matchField$index",
        "classification$index",
        "highlighting$index"
    )
