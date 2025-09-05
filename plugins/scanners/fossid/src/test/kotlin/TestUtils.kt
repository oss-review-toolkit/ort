/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk

import java.util.concurrent.atomic.AtomicInteger

import org.ossreviewtoolkit.clients.fossid.EntityResponseBody
import org.ossreviewtoolkit.clients.fossid.FossIdRestService
import org.ossreviewtoolkit.clients.fossid.FossIdServiceWithVersion
import org.ossreviewtoolkit.clients.fossid.PolymorphicData
import org.ossreviewtoolkit.clients.fossid.PolymorphicDataResponseBody
import org.ossreviewtoolkit.clients.fossid.PolymorphicInt
import org.ossreviewtoolkit.clients.fossid.PolymorphicList
import org.ossreviewtoolkit.clients.fossid.PolymorphicResponseBody
import org.ossreviewtoolkit.clients.fossid.checkDownloadStatus
import org.ossreviewtoolkit.clients.fossid.createIgnoreRule
import org.ossreviewtoolkit.clients.fossid.createScan
import org.ossreviewtoolkit.clients.fossid.deleteScan
import org.ossreviewtoolkit.clients.fossid.downloadFromGit
import org.ossreviewtoolkit.clients.fossid.extractArchives
import org.ossreviewtoolkit.clients.fossid.getProject
import org.ossreviewtoolkit.clients.fossid.listIdentifiedFiles
import org.ossreviewtoolkit.clients.fossid.listIgnoreRules
import org.ossreviewtoolkit.clients.fossid.listIgnoredFiles
import org.ossreviewtoolkit.clients.fossid.listMarkedAsIdentifiedFiles
import org.ossreviewtoolkit.clients.fossid.listMatchedLines
import org.ossreviewtoolkit.clients.fossid.listPendingFiles
import org.ossreviewtoolkit.clients.fossid.listScansForProject
import org.ossreviewtoolkit.clients.fossid.listSnippets
import org.ossreviewtoolkit.clients.fossid.model.CreateScanResponse
import org.ossreviewtoolkit.clients.fossid.model.Scan
import org.ossreviewtoolkit.clients.fossid.model.identification.common.LicenseMatchType
import org.ossreviewtoolkit.clients.fossid.model.identification.identifiedFiles.IdentifiedFile
import org.ossreviewtoolkit.clients.fossid.model.identification.ignored.IgnoredFile
import org.ossreviewtoolkit.clients.fossid.model.identification.markedAsIdentified.Comment
import org.ossreviewtoolkit.clients.fossid.model.identification.markedAsIdentified.File
import org.ossreviewtoolkit.clients.fossid.model.identification.markedAsIdentified.License
import org.ossreviewtoolkit.clients.fossid.model.identification.markedAsIdentified.LicenseFile
import org.ossreviewtoolkit.clients.fossid.model.identification.markedAsIdentified.MarkedAsIdentifiedFile
import org.ossreviewtoolkit.clients.fossid.model.result.MatchType
import org.ossreviewtoolkit.clients.fossid.model.result.MatchedLines
import org.ossreviewtoolkit.clients.fossid.model.result.Snippet
import org.ossreviewtoolkit.clients.fossid.model.rules.IgnoreRule
import org.ossreviewtoolkit.clients.fossid.model.rules.RuleScope
import org.ossreviewtoolkit.clients.fossid.model.rules.RuleType
import org.ossreviewtoolkit.clients.fossid.model.status.DownloadStatus
import org.ossreviewtoolkit.clients.fossid.model.status.ScanStatus
import org.ossreviewtoolkit.clients.fossid.model.status.UnversionedScanDescription
import org.ossreviewtoolkit.clients.fossid.removeUploadedContent
import org.ossreviewtoolkit.clients.fossid.uploadFile
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageType
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.SnippetFinding
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.SnippetChoices
import org.ossreviewtoolkit.plugins.api.Secret
import org.ossreviewtoolkit.plugins.scanners.fossid.events.CloneRepositoryHandler
import org.ossreviewtoolkit.plugins.scanners.fossid.events.UploadArchiveHandler
import org.ossreviewtoolkit.scanner.ScanContext
import org.ossreviewtoolkit.scanner.provenance.NestedProvenance
import org.ossreviewtoolkit.utils.spdx.SpdxExpression

/** A test user ID. */
internal const val USER = "fossIdTestUser"

/** An API key used by tests. */
internal const val API_KEY = "fossId-API-key"

/** A test project name. */
internal const val PROJECT = "fossId-test-project"

/** A (resolved) test revision. */
private const val REVISION = "0123456789012345678901234567890123456789"

/** The version to be reported by the FossID server. */
internal const val FOSSID_VERSION = "2021.2.2"

/** A test scan ID that is returned by default when mocking the creation of a scan. */
internal const val SCAN_ID = 1

/** An [IgnoreRule], returned by default wrapped in a list when mocking the listing of exclusion rules. */
internal val IGNORE_RULE = IgnoreRule(1, RuleType.EXTENSION, ".docx", SCAN_ID, "2021-06-09 14:45:25")

/** The default scope used when creating ignore rule. */
internal val DEFAULT_IGNORE_RULE_SCOPE = RuleScope.SCAN

/**
 * Create a new [FossId] instance with the specified [config].
 */
internal fun createFossId(config: FossIdConfig): FossId = FossId(config = config)

/**
 * Create a standard [FossIdConfig] whose properties can be partly specified.
 */
internal fun createConfig(
    projectName: String? = PROJECT,
    waitForResult: Boolean = true,
    deltaScans: Boolean = true,
    deltaScanLimit: Int = Int.MAX_VALUE,
    fetchSnippetMatchedLines: Boolean = false,
    snippetsLimit: Int = Int.MAX_VALUE,
    isArchiveMode: Boolean = false
): FossIdConfig {
    val config = FossIdConfig(
        serverUrl = "https://www.example.org/fossid",
        user = Secret(USER),
        apiKey = Secret(API_KEY),
        projectName = projectName,
        namingScanPattern = null,
        waitForResult = waitForResult,
        keepFailedScans = false,
        deltaScans = deltaScans,
        deltaScanLimit = deltaScanLimit,
        detectLicenseDeclarations = false,
        detectCopyrightStatements = false,
        timeout = 60,
        fetchSnippetMatchedLines = fetchSnippetMatchedLines,
        snippetsLimit = snippetsLimit,
        sensitivity = 10,
        urlMappings = null,
        writeToStorage = false,
        logRequests = false,
        isArchiveMode = isArchiveMode,
        treatPendingIdentificationsAsError = false,
        deleteUploadedArchiveAfterScan = true
    )

    val namingProvider = createNamingProviderMock()
    val configSpy = spyk(config)
    every { configSpy.createNamingProvider() } returns namingProvider

    return configSpy
}

/**
 * Create a mock [FossIdNamingProvider] that returns deterministic names derived from the parameters provided to its
 * _createXXX()_ functions.
 */
private fun createNamingProviderMock(): FossIdNamingProvider {
    val counter = AtomicInteger()
    val provider = mockk<FossIdNamingProvider>()

    every { provider.createScanCode(any(), any(), any()) } answers {
        scanCode(firstArg(), secondArg(), index = counter.incrementAndGet())
    }

    return provider
}

/**
 * Create a mock for the [FossIdRestService]. The mock is prepared to return its version. (This is queried directly
 * in the constructor of [FossId].)
 */
internal fun createServiceMock(): FossIdServiceWithVersion {
    val service = mockk<FossIdServiceWithVersion>()

    coEvery { service.version } returns FOSSID_VERSION

    return service
}

/**
 * Create a mock for the [VersionControlSystem]. The mock is prepared to always return 'master' for the default branch
 * name.
 */
internal fun createVersionControlSystemMock(): VersionControlSystem {
    val vcs = mockk<VersionControlSystem>()

    coEvery { vcs.getDefaultBranchName(any()) } returns "master"

    return vcs
}

/**
 * Generate a synthetic scan code for the project with the given [name], [tag], and [index].
 */
internal fun scanCode(name: String, tag: FossId.DeltaTag? = null, index: Int = 1): String =
    "$name:${tag?.name}:scanCode$index"

/**
 * Create a mock [UnversionedScanDescription] that returns the given [state].
 */
private fun createScanDescription(state: ScanStatus): UnversionedScanDescription {
    val description = mockk<UnversionedScanDescription>()
    every { description.status } returns state
    every { description.comment } returns "status$state"
    return description
}

/**
 * Create a mock [Scan] as if created by the [CloneRepositoryHandler] with the given properties.
 */
internal fun createScan(
    url: String,
    revision: String,
    scanCode: String,
    scanId: Int = SCAN_ID,
    comment: String = "master",
    legacyComment: Boolean = false
): Scan {
    val scan = mockk<Scan>()
    every { scan.gitRepoUrl } returns url
    every { scan.gitBranch } returns revision
    every { scan.code } returns scanCode
    every { scan.id } returns scanId
    every { scan.isArchived } returns null
    if (legacyComment) {
        every { scan.comment } returns comment
    } else {
        every { scan.comment } returns createOrtScanComment(url, revision, comment).asJsonString()
    }

    return scan
}

/**
 * Create a mock [Scan] as if created by the [UploadArchiveHandler] with the given properties.
 */
internal fun createScanWithUploadedContent(
    url: String,
    revision: String,
    scanCode: String,
    scanId: Int = SCAN_ID,
    comment: String = "master"
): Scan {
    val scan = mockk<Scan>()
    every { scan.gitRepoUrl } returns null
    every { scan.gitBranch } returns null
    every { scan.code } returns scanCode
    every { scan.id } returns scanId
    every { scan.isArchived } returns null
    every { scan.comment } returns createOrtScanComment(url, revision, comment).asJsonString()

    return scan
}

/**
 * Create a [VcsInfo] object for a project with the given [name][projectName] and the optional parameters for [type],
 * [path], and [revision].
 */
internal fun createVcsInfo(
    projectName: String = PROJECT,
    type: VcsType = VcsType.GIT,
    path: String = "",
    revision: String = REVISION
): VcsInfo = VcsInfo(type = type, path = path, revision = revision, url = "https://github.com/test/$projectName.git")

/**
 * Create a test [Identifier] with properties derived from the given [index].
 */
internal fun createIdentifier(index: Int = 1): Identifier =
    Identifier(type = "test", namespace = "test-ns", name = "test$index", version = "1.0.$index")

/**
 * Create a test [Package] with the given [id] , [vcsInfo], and [authors].
 */
internal fun createPackage(id: Identifier, vcsInfo: VcsInfo, authors: Set<String> = emptySet()): Package =
    Package.EMPTY.copy(id = id, vcsProcessed = vcsInfo, authors = authors)

/**
 * Generate the path to a test file based on the given [index].
 */
private fun filePath(index: Int): String = "/path/to/file$index.kt"

/**
 * Create a [TextLocation] that references a test file without any line information.
 */
internal fun textLocation(fileIndex: Int): TextLocation =
    TextLocation(filePath(fileIndex), TextLocation.UNKNOWN_LINE, TextLocation.UNKNOWN_LINE)

/**
 * Create an [IdentifiedFile] based on the given [index].
 */
private fun createIdentifiedFile(index: Int): IdentifiedFile {
    val file = IdentifiedFile(
        comment = null,
        identificationId = index,
        identificationCopyright = "copyright$index",
        isDistributed = index,
        rowId = index,
        userName = "$USER$index",
        userSurname = null,
        userUsername = null
    )

    val license = org.ossreviewtoolkit.clients.fossid.model.identification.identifiedFiles.License(
        fileLicenseMatchType = LicenseMatchType.SNIPPET,
        id = index,
        identifier = "lic$index",
        isFoss = 0,
        isOsiApproved = 0,
        isSpdxStandard = 0,
        name = "name$index"
    )

    file.file = org.ossreviewtoolkit.clients.fossid.model.identification.identifiedFiles.File(
        id = "identified$index",
        licenseIdentifier = "licenseIdentifier1",
        licenseIncludeInReport = false,
        licenseIsCopyleft = false,
        licenseIsFoss = true,
        licenseIsSpdxStandard = false,
        licenseMatchType = null,
        licenseName = "license$index",
        licenses = mutableMapOf(index to license),
        md5 = null,
        path = filePath(index),
        sha1 = null,
        sha256 = null
    )

    return file
}

/**
 * Create a [MarkedAsIdentifiedFile] based on the given [index].
 */
private fun createMarkedIdentifiedFile(index: Int): MarkedAsIdentifiedFile {
    val file = MarkedAsIdentifiedFile(
        identificationId = index,
        identificationCopyright = "copyrightMarked$index",
        isDistributed = index,
        rowId = index,
        comment = null
    )

    val license = License(index, LicenseMatchType.FILE, index, index, index, index, "created$index", "updated$index")

    license.file = LicenseFile(
        licenseIdentifier = "licenseMarkedIdentifier$index",
        licenseIncludeInReport = true,
        licenseIsCopyleft = false,
        licenseIsFoss = true,
        licenseIsSpdxStandard = true,
        licenseName = "test$index"
    )

    file.file = File(
        id = "marked$index",
        md5 = null,
        path = filePath(index),
        sha1 = null,
        sha256 = null,
        size = index,
        licenses = mutableMapOf(index to license)
    )

    return file
}

/**
 * Create a [MarkedAsIdentifiedFile] with the give [license] and [path].
 */
internal fun createMarkAsIdentifiedFile(
    license: String,
    path: String,
    comment: String? = null
): MarkedAsIdentifiedFile {
    val fileLicense = License(
        id = 1,
        type = LicenseMatchType.FILE,
        userId = 1,
        componentId = 1,
        identificationId = 1,
        created = "created",
        updated = "updated",
        licenseId = 1
    ).also {
        it.file = LicenseFile(
            licenseIdentifier = license,
            licenseIncludeInReport = null,
            licenseIsCopyleft = null,
            licenseIsFoss = null,
            licenseIsSpdxStandard = null,
            licenseName = null
        )
    }

    return MarkedAsIdentifiedFile(
        comment = "comment",
        comments = if (comment == null) emptyMap() else mapOf(1 to Comment(1, 1, comment)),
        identificationId = 1,
        identificationCopyright = "copyright",
        isDistributed = 1,
        rowId = 1
    ).also {
        it.file = File(
            id = "fileId",
            md5 = "fileMd5",
            path = path,
            sha1 = "fileSha1",
            sha256 = "fileSha256",
            size = 0,
            licenses = if (comment != null) null else mutableMapOf(1 to fileLicense)
        )
    }
}

/**
 * Create an [IgnoredFile] based on the given [index].
 */
private fun createIgnoredFile(index: Int): IgnoredFile =
    IgnoredFile(id = index, path = filePath(index), reason = "ignoreReason$index", matchType = "match$index")

/**
 * Generate a string representing a pending file based on the given [index].
 */
internal fun createPendingFile(index: Int): String = "/pending/file/$index"

/**
 * Generate a FossID snippet based on the given [index].
 */
private fun createSnippet(index: Int): Snippet =
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
        null,
        "MIT",
        null,
        "releaseDate$index",
        "mirror$index",
        "file$index",
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

/**
 * Generate a ORT snippet finding based on the given [index].
 */
internal fun createSnippetFindings(index: Int): SnippetFinding =
    SnippetFinding(
        TextLocation("/pending/file/$index", TextLocation.UNKNOWN_LINE),
        (1..5).map { snippetIndex ->
            org.ossreviewtoolkit.model.Snippet(
                snippetIndex.toFloat(),
                TextLocation("file$snippetIndex", TextLocation.UNKNOWN_LINE),
                ArtifactProvenance(RemoteArtifact("url$snippetIndex", Hash.NONE)),
                "pkg:generic/author$snippetIndex/artifact$snippetIndex@version$snippetIndex",
                SpdxExpression.parse("MIT"),
                mapOf(
                    FossId.SNIPPET_DATA_ID to "$snippetIndex",
                    FossId.SNIPPET_DATA_RELEASE_DATE to "releaseDate$snippetIndex",
                    FossId.SNIPPET_DATA_MATCH_TYPE to MatchType.PARTIAL.toString(),
                    FossId.SNIPPET_DATA_MATCH_TYPE to MatchType.PARTIAL.toString()
                )
            )
        }.toSet()
    )

/**
 * Prepare this service mock to answer a request for a project with the given [projectCode]. Return a response with
 * the given [status] and [error].
 */
internal fun FossIdServiceWithVersion.expectProjectRequest(
    projectCode: String,
    status: Int = 200,
    error: String? = null
): FossIdServiceWithVersion {
    coEvery { getProject(USER, API_KEY, projectCode) } returns
        EntityResponseBody(status = status, error = error, data = PolymorphicData(mockk()))
    return this
}

/**
 * Prepare this service mock to answer requests for the status of the scan with the given [scanCode]. The service
 * returns responses with the given [states] in succeeding invocations.
 */
internal fun FossIdServiceWithVersion.expectCheckScanStatus(
    scanCode: String,
    vararg states: ScanStatus
): FossIdServiceWithVersion {
    val statusResponses = states.map { EntityResponseBody(status = 1, data = createScanDescription(it)) }
    coEvery { checkScanStatus(USER, API_KEY, scanCode) } returnsMany statusResponses
    return this
}

/**
 * Prepare this service mock to return the list of [scans] for the given [projectCode].
 */
internal fun FossIdServiceWithVersion.expectListScans(
    projectCode: String,
    scans: List<Scan>
): FossIdServiceWithVersion {
    coEvery { listScansForProject(USER, API_KEY, projectCode) } returns
        PolymorphicResponseBody(status = 1, data = PolymorphicList(scans))
    return this
}

/**
 * Prepare this service mock to return the list of [rules] for the given [scanCode].
 */
internal fun FossIdServiceWithVersion.expectListIgnoreRules(
    scanCode: String,
    rules: List<IgnoreRule>
): FossIdServiceWithVersion {
    coEvery { listIgnoreRules(USER, API_KEY, scanCode) } returns
        PolymorphicResponseBody(status = 1, data = PolymorphicList(rules))
    return this
}

/**
 * Prepare this service mock to return the list of [rules] for the given [scanCode].
 */
internal fun FossIdServiceWithVersion.expectCreateIgnoreRule(
    scanCode: String,
    type: RuleType,
    value: String,
    scope: RuleScope = RuleScope.SCAN,
    error: Boolean = false
): FossIdServiceWithVersion {
    if (error) {
        coEvery { createIgnoreRule(USER, API_KEY, scanCode, type, value, scope) } returns
            EntityResponseBody("create ignore rules", error = "Rule already exists.")
    } else {
        coEvery { createIgnoreRule(USER, API_KEY, scanCode, type, value, scope) } returns EntityResponseBody()
    }

    return this
}

/**
 * Prepare this service mock to expect a request to create an 'ignore rule' for the given [scanCode], [ruleType],
 * [value] and [scope].
 */
internal fun FossIdServiceWithVersion.expectCreateIgnoreRule(
    scanCode: String,
    ruleType: RuleType,
    value: String,
    scope: RuleScope
): FossIdServiceWithVersion {
    coEvery {
        createIgnoreRule(USER, API_KEY, scanCode, ruleType, value, scope)
    } returns EntityResponseBody(status = 1)
    return this
}

/**
 * Prepare this service mock to expect a request to remove uploaded content for the given [scanCode].
 */
internal fun FossIdServiceWithVersion.expectRemoveUploadedContent(scanCode: String): FossIdServiceWithVersion {
    coEvery {
        removeUploadedContent(USER, API_KEY, scanCode)
    } returns EntityResponseBody(status = 1)
    return this
}

/**
 * Prepare this service mock to expect a request to upload a file for the given [scanCode]. The file nane is not
 * required as the FossID scanner generates a unique name for each file.
 */
internal fun FossIdServiceWithVersion.expectUploadFile(scanCode: String): FossIdServiceWithVersion {
    coEvery {
        uploadFile(USER, API_KEY, scanCode, any())
    } returns EntityResponseBody()
    return this
}

/**
 * Prepare this service mock to expect a request to extract archives for the given [scanCode]. The file nane is not
 * required as the FossID scanner generates a unique name for each file.
 */
internal fun FossIdServiceWithVersion.expectExtractArchives(scanCode: String): FossIdServiceWithVersion {
    coEvery {
        extractArchives(USER, API_KEY, scanCode, any())
    } returns EntityResponseBody(data = true)
    return this
}

/**
 * Prepare this service mock to expect a download trigger for the given [scanCode] and later on to report that the
 * download has finished.
 */
internal fun FossIdServiceWithVersion.expectDownload(scanCode: String): FossIdServiceWithVersion {
    coEvery { downloadFromGit(USER, API_KEY, scanCode) } returns
        EntityResponseBody(status = 1)
    coEvery { checkDownloadStatus(USER, API_KEY, scanCode) } returns
        EntityResponseBody(status = 1, data = PolymorphicData(DownloadStatus.FINISHED))
    return this
}

/**
 * Prepare this service mock to expect a request to create a scan for the given [projectCode], [scanCode], and
 * [vcsInfo] and [projectRevision]. With the [isArchiveMode] flag, scans can be mocked as if created by the
 * [UploadArchiveHandler].
 */
internal fun FossIdServiceWithVersion.expectCreateScan(
    projectCode: String,
    scanCode: String,
    vcsInfo: VcsInfo,
    projectRevision: String = "master",
    isArchiveMode: Boolean = false
): FossIdServiceWithVersion {
    val comment = createOrtScanComment(vcsInfo.url, vcsInfo.revision, projectRevision).asJsonString()

    if (isArchiveMode) {
        coEvery {
            createScan(USER, API_KEY, projectCode, scanCode, null, null, comment)
        } returns PolymorphicDataResponseBody(
            status = 1, data = PolymorphicData(CreateScanResponse(SCAN_ID.toString()))
        )
    } else {
        coEvery {
            createScan(USER, API_KEY, projectCode, scanCode, vcsInfo.url, vcsInfo.revision, comment)
        } returns PolymorphicDataResponseBody(
            status = 1, data = PolymorphicData(CreateScanResponse(SCAN_ID.toString()))
        )
    }

    return this
}

/**
 * Prepare this service mock to expect a request to delete the scan with the given [scanCode].
 */
internal fun FossIdServiceWithVersion.expectDeleteScan(scanCode: String): FossIdServiceWithVersion {
    coEvery {
        deleteScan(USER, API_KEY, scanCode)
    } returns EntityResponseBody(status = 1, data = PolymorphicInt(0))
    return this
}

/**
 * Prepare this service mock to answer queries for the different file types associated with the given [scanCode].
 * Based on the passed in ranges, test files are created.
 */
internal fun FossIdServiceWithVersion.mockFiles(
    scanCode: String,
    identifiedRange: IntRange = IntRange.EMPTY,
    markedRange: IntRange = IntRange.EMPTY,
    ignoredRange: IntRange = IntRange.EMPTY,
    pendingRange: IntRange = IntRange.EMPTY,
    snippetRange: IntRange = IntRange.EMPTY,
    matchedLinesFlag: Boolean = false
): FossIdServiceWithVersion {
    val identifiedFiles = identifiedRange.map(::createIdentifiedFile)
    val markedFiles = markedRange.map(::createMarkedIdentifiedFile)
    val ignoredFiles = ignoredRange.map(::createIgnoredFile)
    val pendingFiles = pendingRange.map(::createPendingFile)
    val snippets = snippetRange.map(::createSnippet)
    val matchedLines = MatchedLines(PolymorphicList(listOf(1, 2, 3, 21, 22, 36)), PolymorphicList(listOf(11, 12)))

    coEvery { listIdentifiedFiles(USER, API_KEY, scanCode) } returns
        PolymorphicResponseBody(
            status = 1, data = PolymorphicList(identifiedFiles)
        )
    coEvery { listMarkedAsIdentifiedFiles(USER, API_KEY, scanCode) } returns
        PolymorphicResponseBody(
            status = 1, data = PolymorphicList(markedFiles)
        )
    coEvery { listIgnoredFiles(USER, API_KEY, scanCode) } returns
        PolymorphicResponseBody(status = 1, data = PolymorphicList(ignoredFiles))
    coEvery { listPendingFiles(USER, API_KEY, scanCode) } returns
        PolymorphicResponseBody(status = 1, data = PolymorphicList(pendingFiles))
    coEvery { listSnippets(USER, API_KEY, scanCode, any()) } returns
        PolymorphicResponseBody(status = 1, data = PolymorphicList(snippets))
    if (matchedLinesFlag) {
        coEvery { listMatchedLines(USER, API_KEY, scanCode, any(), any()) } returns
            EntityResponseBody(status = 1, data = PolymorphicData(matchedLines))
    }

    return this
}

/**
 * Trigger a FossID scan of the given [package][pkg].
 */
internal fun FossId.scan(
    pkg: Package,
    labels: Map<String, String> = emptyMap(),
    excludes: Excludes = Excludes(),
    snippetChoices: List<SnippetChoices> = emptyList()
): ScanResult =
    scanPackage(
        nestedProvenance = NestedProvenance(
            root = RepositoryProvenance(
                vcsInfo = pkg.vcsProcessed,
                resolvedRevision = pkg.vcsProcessed.revision
            ),
            subRepositories = emptyMap()
        ),
        context = ScanContext(
            labels = labels,
            packageType = PackageType.PACKAGE,
            excludes = excludes,
            coveredPackages = listOf(pkg),
            snippetChoices = snippetChoices
        )
    )
