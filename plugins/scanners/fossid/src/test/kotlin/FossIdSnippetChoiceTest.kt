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
import io.kotest.inspectors.forAtLeastOne
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject

import org.ossreviewtoolkit.clients.fossid.EntityResponseBody
import org.ossreviewtoolkit.clients.fossid.FossIdRestService
import org.ossreviewtoolkit.clients.fossid.FossIdServiceWithVersion
import org.ossreviewtoolkit.clients.fossid.PolymorphicData
import org.ossreviewtoolkit.clients.fossid.PolymorphicList
import org.ossreviewtoolkit.clients.fossid.PolymorphicResponseBody
import org.ossreviewtoolkit.clients.fossid.addComponentIdentification
import org.ossreviewtoolkit.clients.fossid.addFileComment
import org.ossreviewtoolkit.clients.fossid.listIdentifiedFiles
import org.ossreviewtoolkit.clients.fossid.listIgnoredFiles
import org.ossreviewtoolkit.clients.fossid.listMarkedAsIdentifiedFiles
import org.ossreviewtoolkit.clients.fossid.listMatchedLines
import org.ossreviewtoolkit.clients.fossid.listPendingFiles
import org.ossreviewtoolkit.clients.fossid.listSnippets
import org.ossreviewtoolkit.clients.fossid.markAsIdentified
import org.ossreviewtoolkit.clients.fossid.model.identification.markedAsIdentified.MarkedAsIdentifiedFile
import org.ossreviewtoolkit.clients.fossid.model.result.MatchType
import org.ossreviewtoolkit.clients.fossid.model.result.MatchedLines
import org.ossreviewtoolkit.clients.fossid.model.result.Snippet
import org.ossreviewtoolkit.clients.fossid.model.status.ScanStatus
import org.ossreviewtoolkit.clients.fossid.unmarkAsIdentified
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.config.SnippetChoices
import org.ossreviewtoolkit.model.config.snippet.Choice
import org.ossreviewtoolkit.model.config.snippet.Given
import org.ossreviewtoolkit.model.config.snippet.Provenance
import org.ossreviewtoolkit.model.config.snippet.SnippetChoice
import org.ossreviewtoolkit.model.config.snippet.SnippetChoiceReason
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.utils.ort.ORT_NAME
import org.ossreviewtoolkit.utils.spdx.toSpdx

/** Sample files in the results. **/
private const val FILE_1 = "a.java"
private const val FILE_2 = "b.java"

/** A sample purl in the results. **/
private const val PURL_1 = "pkg:github/fakeuser/fakepackage1@1.0.0"

/** A sample purl in the results. **/
private const val PURL_2 = "pkg:github/fakeuser/fakepackage2@1.0.0"

/** A sample purl in the results. **/
private const val PURL_3 = "pkg:github/fakeuser/fakepackage3@1.0.0"

@Suppress("LargeClass")
class FossIdSnippetChoiceTest : WordSpec({
    beforeSpec {
        mockkStatic("org.ossreviewtoolkit.clients.fossid.ExtensionsKt")
    }

    beforeTest {
        // Here a static function of the companion object is mocked therefore `mockkobject` needs to be used.
        // See https://lifesaver.codes/answer/cannot-mockkstatic-for-kotlin-companion-object-static-method-136
        mockkObject(FossIdRestService)
        mockkObject(VersionControlSystem)

        coEvery { FossIdRestService.create(any()) } returns createServiceMock()
        every { VersionControlSystem.forUrl(any()) } returns createVersionControlSystemMock()
    }

    afterTest {
        unmockkObject(FossIdRestService)
        unmockkObject(VersionControlSystem)
    }

    "scanPackages()" should {
        "not report a snippet that has been chosen" {
            val projectCode = PROJECT
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
            val projectCode = PROJECT
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
            val projectCode = PROJECT
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

        "mark a file with all snippets chosen as identified" {
            val projectCode = PROJECT
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false, fetchSnippetMatchedLines = true)
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, "${vcsInfo.revision}_other", scanCode)
            val pkgId = createIdentifier(index = 42)

            val service = FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(scanCode, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo, "")
                .expectDownload(scanCode)
                .mockFiles(
                    scanCode,
                    pendingFiles = listOf(FILE_1),
                    snippets = listOf(
                        createSnippet(0, FILE_1, PURL_1)
                    ),
                    matchedLines = mapOf(
                        0 to MatchedLines.create((10..20).toList(), (10..20).toList())
                    )
                )
                .expectMarkAsIdentified(scanCode, FILE_1)

            val choiceLocation = TextLocation(FILE_1, 10, 20)
            val snippetChoices = createSnippetChoices(
                vcsInfo.url,
                createSnippetChoice(choiceLocation, PURL_1, comment = "")
            )
            val fossId = createFossId(config)

            val summary = fossId.scan(createPackage(pkgId, vcsInfo), snippetChoices = snippetChoices).summary

            summary.snippetFindings should beEmpty()
            coVerify {
                service.markAsIdentified(USER, API_KEY, scanCode, FILE_1, any())
                service.addComponentIdentification(
                    user = USER,
                    apiKey = API_KEY,
                    scanCode = scanCode,
                    path = FILE_1,
                    componentName = "fakepackage1",
                    componentVersion = "1.0.0",
                    isDirectory = false,
                    preserveExistingIdentifications = true
                )

                val payload = OrtCommentPayload(mapOf("MIT" to listOf(choiceLocation)), 1, 0)
                val comment = jsonMapper.writeValueAsString(OrtComment(payload))

                service.addFileComment(
                    user = USER,
                    apiKey = API_KEY,
                    scanCode = scanCode,
                    path = FILE_1,
                    comment = comment,
                    isImportant = false,
                    includeInReport = false
                )
            }

            summary.issues.forAtLeastOne {
                it.message shouldBe "This scan has 0 file(s) pending identification in FossID. " +
                    "Please review and resolve them at: https://www.example.org/fossid/index.html?action=scanview&sid=1"
            }
        }

        "mark a file with only non relevant snippets for a given snippet location as identified" {
            val projectCode = PROJECT
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false, fetchSnippetMatchedLines = true)
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, "${vcsInfo.revision}_other", scanCode)
            val pkgId = createIdentifier(index = 42)

            val service = FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(scanCode, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo, "")
                .expectDownload(scanCode)
                .mockFiles(
                    scanCode,
                    pendingFiles = listOf(FILE_1),
                    snippets = listOf(
                        createSnippet(0, FILE_1, PURL_1)
                    ),
                    matchedLines = mapOf(
                        0 to MatchedLines.create((10..20).toList(), (10..20).toList())
                    )
                )
                .expectMarkAsIdentified(scanCode, FILE_1)

            val snippetChoices = createSnippetChoices(
                vcsInfo.url,
                createSnippetChoice(TextLocation(FILE_1, 10, 20), comment = "")
            )
            val fossId = createFossId(config)

            val summary = fossId.scan(createPackage(pkgId, vcsInfo), snippetChoices = snippetChoices).summary

            summary.snippetFindings should beEmpty()
            coVerify {
                service.markAsIdentified(USER, API_KEY, scanCode, FILE_1, any())
            }

            summary.issues.filter { it.severity > Severity.HINT } should beEmpty()

            summary.issues.forAtLeastOne {
                it.message shouldBe "This scan has 0 file(s) pending identification in FossID. " +
                    "Please review and resolve them at: https://www.example.org/fossid/index.html?action=scanview&sid=1"
            }
        }

        "not mark a file with some non relevant snippets for a given snippet location as identified" {
            val projectCode = PROJECT
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false, fetchSnippetMatchedLines = true)
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, "${vcsInfo.revision}_other", scanCode)
            val pkgId = createIdentifier(index = 42)

            val service = FossIdRestService.create(config.serverUrl)
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
                createSnippetChoice(TextLocation(FILE_1, 10, 20), comment = "")
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

            coVerify(inverse = true) {
                service.markAsIdentified(USER, API_KEY, scanCode, FILE_1, any())
            }

            summary.issues.filter { it.severity > Severity.HINT } should beEmpty()

            summary.issues.forAtLeastOne {
                it.message shouldBe "This scan has 1 file(s) pending identification in FossID. " +
                    "Please review and resolve them at: https://www.example.org/fossid/index.html?action=scanview&sid=1"
            }
        }

        "mark a file with only chosen and non relevant snippets for a given snippet location as identified" {
            val projectCode = PROJECT
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false, fetchSnippetMatchedLines = true)
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, "${vcsInfo.revision}_other", scanCode)
            val pkgId = createIdentifier(index = 42)

            val service = FossIdRestService.create(config.serverUrl)
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
                .expectMarkAsIdentified(scanCode, FILE_1)

            val snippetChoices = createSnippetChoices(
                vcsInfo.url,
                createSnippetChoice(TextLocation(FILE_1, 10, 20), PURL_1, ""),
                createSnippetChoice(TextLocation(FILE_1, 20, 30), comment = "")
            )
            val fossId = createFossId(config)

            val summary = fossId.scan(createPackage(pkgId, vcsInfo), snippetChoices = snippetChoices).summary

            summary.snippetFindings should beEmpty()
            coVerify(exactly = 1) {
                service.markAsIdentified(USER, API_KEY, scanCode, FILE_1, any())
            }

            summary.issues.filter { it.severity > Severity.HINT } should beEmpty()
        }

        "add the license of a chosen snippet to the license findings" {
            val projectCode = PROJECT
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

            val choiceLocation = TextLocation(FILE_1, 10, 20)
            val snippetChoices = createSnippetChoices(
                vcsInfo.url,
                createSnippetChoice(TextLocation(FILE_1, 10, 20), PURL_1, "")
            )
            val fossId = createFossId(config)

            val summary = fossId.scan(createPackage(pkgId, vcsInfo), snippetChoices = snippetChoices).summary

            summary.licenseFindings shouldHaveSize 1
            summary.licenseFindings.first().apply {
                license shouldBe "MIT".toSpdx()
                location shouldBe choiceLocation
            }
        }

        "create an issue if the chosen snippet is not in the snippet results" {
            val projectCode = PROJECT
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

            val choiceLocation = TextLocation("missing.java", 10, 20)
            val snippetChoices = createSnippetChoices(
                vcsInfo.url,
                createSnippetChoice(choiceLocation, PURL_1, "")
            )
            val fossId = createFossId(config)

            val summary = fossId.scan(createPackage(pkgId, vcsInfo), snippetChoices = snippetChoices).summary

            summary.issues.forAtLeastOne {
                it.message shouldBe "The configuration contains a snippet choice for the snippet $PURL_1 at " +
                    "${choiceLocation.prettyPrint()}, but the FossID result contains no such snippet."
            }
        }

        "add the license of already marked as identified file with a snippet choice to the license findings" {
            val projectCode = PROJECT
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false, fetchSnippetMatchedLines = true)
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, "${vcsInfo.revision}_other", scanCode)
            val pkgId = createIdentifier(index = 42)

            val choiceLocation = TextLocation(FILE_1, 10, 20)
            val payload = OrtCommentPayload(mapOf("MIT" to listOf(choiceLocation)), 1, 0)
            val comment = jsonMapper.writeValueAsString(mapOf(ORT_NAME to payload))
            val markedAsIdentifiedFile = createMarkAsIdentifiedFile("MIT", FILE_1, comment)
            FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(scanCode, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo, "")
                .expectDownload(scanCode)
                .mockFiles(
                    scanCode,
                    markedFiles = listOf(markedAsIdentifiedFile)
                )

            val snippetChoices = createSnippetChoices(
                vcsInfo.url,
                createSnippetChoice(choiceLocation, PURL_1, "")
            )
            val fossId = createFossId(config)

            val summary = fossId.scan(createPackage(pkgId, vcsInfo), snippetChoices = snippetChoices).summary

            summary.licenseFindings shouldHaveSize 1
            summary.licenseFindings.first().apply {
                license shouldBe "MIT".toSpdx()
                location shouldBe choiceLocation
            }

            summary.issues.filter { it.severity > Severity.HINT } should beEmpty()
            summary.snippetFindings should beEmpty()
        }

        "add the license of marked as identified files that have been manually marked in the UI (legacy behavior)" {
            val projectCode = PROJECT
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false, fetchSnippetMatchedLines = true)
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, "${vcsInfo.revision}_other", scanCode)
            val pkgId = createIdentifier(index = 42)

            val markedAsIdentifiedFile = createMarkAsIdentifiedFile("MIT", FILE_1)
            FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(scanCode, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo, "")
                .expectDownload(scanCode)
                .mockFiles(
                    scanCode,
                    markedFiles = listOf(markedAsIdentifiedFile)
                )

            val fossId = createFossId(config)

            val summary = fossId.scan(createPackage(pkgId, vcsInfo), snippetChoices = emptyList()).summary

            summary.licenseFindings shouldHaveSize 1
            summary.licenseFindings.first().apply {
                license shouldBe "MIT".toSpdx()
                val defaultLocation = TextLocation(FILE_1, -1, -1)
                location shouldBe defaultLocation
            }

            summary.issues.filter { it.severity > Severity.HINT } should beEmpty()
            summary.snippetFindings should beEmpty()
        }

        "put a marked as identified file back to pending if it has no snippet choice" {
            val projectCode = PROJECT
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false, fetchSnippetMatchedLines = true)
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, "${vcsInfo.revision}_other", scanCode)
            val pkgId = createIdentifier(index = 42)

            val choiceLocation = TextLocation(FILE_1, 10, 20)
            val payload = OrtCommentPayload(mapOf("MIT" to listOf(choiceLocation)), 1, 0)
            val comment = jsonMapper.writeValueAsString(mapOf(ORT_NAME to payload))
            val markedAsIdentifiedFile = createMarkAsIdentifiedFile("MIT", FILE_1, comment)
            val service = FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(scanCode, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo, "")
                .expectDownload(scanCode)
                .mockFiles(
                    scanCode,
                    markedFiles = listOf(markedAsIdentifiedFile),
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
                .expectUnmarkAsIdentified(scanCode, FILE_1)

            val fossId = createFossId(config)

            val summary = fossId.scan(createPackage(pkgId, vcsInfo), snippetChoices = emptyList()).summary

            summary.issues.forAtLeastOne {
                it.message shouldBe "This scan has 1 file(s) pending identification in FossID. " +
                    "Please review and resolve them at: https://www.example.org/fossid/index.html?action=scanview&sid=1"
            }

            summary.issues.filter { it.severity > Severity.HINT } should beEmpty()
            summary.snippetFindings shouldHaveSize 2
            summary.snippetFindings.first().apply {
                sourceLocation.path shouldBe FILE_1
                snippets shouldHaveSize 2
                snippets.map { it.purl } shouldBe listOf(PURL_1, PURL_2)
            }

            summary.snippetFindings.last().apply {
                sourceLocation.path shouldBe FILE_1
                snippets shouldHaveSize 1
                snippets.map { it.purl } shouldBe listOf(PURL_3)
            }

            coVerify {
                service.unmarkAsIdentified(USER, API_KEY, scanCode, FILE_1, any())
            }
        }

        "put a marked as identified file back to pending if some of its snippet choices have been deleted" {
            val projectCode = PROJECT
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false, fetchSnippetMatchedLines = true)
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, "${vcsInfo.revision}_other", scanCode)
            val pkgId = createIdentifier(index = 42)

            val choiceLocation = TextLocation(FILE_1, 10, 20)
            val payload = OrtCommentPayload(mapOf("MIT" to listOf(choiceLocation)), 2, 0)
            val comment = jsonMapper.writeValueAsString(mapOf(ORT_NAME to payload))
            val markedAsIdentifiedFile = createMarkAsIdentifiedFile("MIT", FILE_1, comment)
            val service = FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(scanCode, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo, "")
                .expectDownload(scanCode)
                .mockFiles(
                    scanCode,
                    markedFiles = listOf(markedAsIdentifiedFile),
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
                .expectUnmarkAsIdentified(scanCode, FILE_1)

            val snippetChoices = createSnippetChoices(
                vcsInfo.url,
                createSnippetChoice(choiceLocation, PURL_1, "")
            )
            val fossId = createFossId(config)

            val summary = fossId.scan(createPackage(pkgId, vcsInfo), snippetChoices = snippetChoices).summary

            summary.issues.forAtLeastOne {
                it.message shouldBe "This scan has 1 file(s) pending identification in FossID. " +
                    "Please review and resolve them at: https://www.example.org/fossid/index.html?action=scanview&sid=1"
            }

            summary.issues.filter { it.severity > Severity.HINT } should beEmpty()
            // Two snippets have been chosen according to the count in the comment. However, the .ort.yml file contains
            // only one snippet choice: Therefore, the file should be 'pending' again and the remaining snippet should
            // be returned.
            summary.snippetFindings shouldHaveSize 1
            summary.snippetFindings.last().apply {
                sourceLocation.path shouldBe FILE_1
                snippets shouldHaveSize 1
                snippets.map { it.purl } shouldBe listOf(PURL_3)
            }

            coVerify {
                service.unmarkAsIdentified(USER, API_KEY, scanCode, FILE_1, any())
            }
        }

        "respect the snippet limit when listing snippets (single snippet per finding)" {
            val projectCode = PROJECT
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false, fetchSnippetMatchedLines = true, snippetsLimit = 2)
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
                    pendingFiles = listOf(FILE_1, FILE_2),
                    snippets = listOf(
                        createSnippet(0, FILE_1, PURL_1),
                        createSnippet(1, FILE_1, PURL_2)
                    ),
                    matchedLines = mapOf(
                        0 to MatchedLines.create((10..20).toList(), (10..20).toList()),
                        1 to MatchedLines.create((20..30).toList(), (20..30).toList())
                    )
                )

            val fossId = createFossId(config)

            val summary = fossId.scan(createPackage(pkgId, vcsInfo)).summary

            summary.snippetFindings shouldHaveSize 2
            summary.snippetFindings.forEach {
                it.sourceLocation.path shouldBe FILE_1
            }

            summary.snippetFindings.flatMap { it.snippets } shouldHaveSize 2

            summary.issues.forAtLeastOne {
                it.message shouldBe "The snippets limit of 2 has been reached. To see the possible remaining " +
                    "snippets, please perform a snippet choice for the snippets presents in the snippet report an " +
                    "rerun the scan."
            }
        }

        "respect the snippet limit when listing snippets (multiple snippets per finding)" {
            val projectCode = PROJECT
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false, fetchSnippetMatchedLines = true, snippetsLimit = 2)
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
                    pendingFiles = listOf(FILE_1, FILE_2),
                    snippets = listOf(
                        createSnippet(0, FILE_1, PURL_1),
                        createSnippet(1, FILE_1, PURL_2)
                    ),
                    matchedLines = mapOf(
                        0 to MatchedLines.create((10..20).toList(), (10..20).toList()),
                        1 to MatchedLines.create((10..20).toList(), (20..30).toList())
                    )
                )

            val fossId = createFossId(config)

            val summary = fossId.scan(createPackage(pkgId, vcsInfo)).summary

            summary.snippetFindings shouldHaveSize 1
            summary.snippetFindings.forEach {
                it.sourceLocation.path shouldBe FILE_1
            }

            summary.snippetFindings.flatMap { it.snippets } shouldHaveSize 2

            summary.issues.forAtLeastOne {
                it.message shouldBe "The snippets limit of 2 has been reached. To see the possible remaining " +
                    "snippets, please perform a snippet choice for the snippets presents in the snippet report an " +
                    "rerun the scan."
            }
        }

        "list all the snippets of a file even if if goes over the snippets limit" {
            val projectCode = PROJECT
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false, fetchSnippetMatchedLines = true, snippetsLimit = 2)
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
                        1 to MatchedLines.create((20..30).toList(), (20..30).toList()),
                        2 to MatchedLines.create((20..30).toList(), (20..30).toList())
                    )
                )

            val fossId = createFossId(config)

            val summary = fossId.scan(createPackage(pkgId, vcsInfo)).summary

            summary.snippetFindings shouldHaveSize 2
            summary.snippetFindings.flatMap { it.snippets } shouldHaveSize 3

            summary.issues.forAtLeastOne {
                it.message shouldBe "The snippets limit of 2 has been reached. To see the possible remaining " +
                    "snippets, please perform a snippet choice for the snippets presents in the snippet report an " +
                    "rerun the scan."
            }
        }

        "not count the chosen snippet when enforcing the snippet limits" {
            val projectCode = PROJECT
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false, fetchSnippetMatchedLines = true, snippetsLimit = 2)
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
                    pendingFiles = listOf(FILE_1, FILE_2),
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

            summary.snippetFindings shouldHaveSize 2

            summary.snippetFindings.first().apply { // one snippet is remaining for file 1
                sourceLocation.path shouldBe FILE_1
            }

            summary.snippetFindings.last().apply { // one snippet for file 2, then the limit is reached
                sourceLocation.path shouldBe FILE_2
            }

            summary.issues.forAtLeastOne {
                it.message shouldBe "The snippets limit of 2 has been reached. To see the possible remaining " +
                    "snippets, please perform a snippet choice for the snippets presents in the snippet report an " +
                    "rerun the scan."
            }
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
            EntityResponseBody(status = 1, data = PolymorphicData(lines))
        }
    }

    return this
}

/**
 * Prepare this service mock to expect a mark as identified call for the given [scanCode] and [path].
 */
fun FossIdServiceWithVersion.expectMarkAsIdentified(scanCode: String, path: String): FossIdServiceWithVersion {
    coEvery {
        markAsIdentified(USER, API_KEY, scanCode, path, any())
    } returns EntityResponseBody(status = 1)

    coEvery {
        addComponentIdentification(
            USER, API_KEY, scanCode, path, any(), any(), any(), preserveExistingIdentifications = true
        )
    } returns EntityResponseBody(status = 1)

    coEvery {
        addFileComment(USER, API_KEY, scanCode, path, any(), isImportant = false, includeInReport = false)
    } returns EntityResponseBody(status = 1)

    return this
}

/**
 * Prepare this service mock to expect an unmark as identified call for the given [scanCode] and [path].
 */
fun FossIdServiceWithVersion.expectUnmarkAsIdentified(scanCode: String, path: String): FossIdServiceWithVersion {
    coEvery { unmarkAsIdentified(USER, API_KEY, scanCode, path, any()) } returns
        EntityResponseBody(status = 1)
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
