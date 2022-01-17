/*
 * Copyright (C) 2020 Bosch.IO GmbH
 * Copyright (C) 2021 HERE Europe B.V.
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

package org.ossreviewtoolkit.scanner.storages

import com.vdurmont.semver4j.Semver

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

import java.time.Instant

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.scanner.ScanResultsStorage
import org.ossreviewtoolkit.scanner.ScannerCriteria
import org.ossreviewtoolkit.scanner.experimental.ScanStorageException

private val ID = Identifier(type = "Gradle", namespace = "testNS", name = "test", version = "1.0.9")

private val PACKAGE = Package(
    id = ID, binaryArtifact = RemoteArtifact.EMPTY, sourceArtifact = RemoteArtifact.EMPTY,
    vcs = VcsInfo.EMPTY, description = "testPackage", homepageUrl = "https://test-package.org/",
    declaredLicenses = sortedSetOf()
)

private val CRITERIA = ScannerCriteria(
    "testScanner", Semver("0.0.1"), Semver("2.0.0"),
    ScannerCriteria.exactConfigMatcher("testConfig")
)

/**
 * Create a mock [ScanResultsStorage] that reports the specified [name].
 */
private fun storageMock(name: String): ScanResultsStorage {
    val storage = mockk<ScanResultsStorage>()
    every { storage.name } returns name
    return storage
}

/**
 * Generate a test [LicenseFinding] object based on the given [index].
 */
private fun licenseFinding(index: Int): LicenseFinding =
    LicenseFinding("testLicense$index", TextLocation("file$index", index + 1, index + 2))

/**
 * Create a [ScanResult] with [resultCount] findings.
 */
private fun createScanResult(resultCount: Int): ScanResult {
    val licenseFindings = List(resultCount) { licenseFinding(it) }
    val summary = ScanSummary(
        startTime = Instant.now(),
        endTime = Instant.now(),
        packageVerificationCode = "test$resultCount",
        licenseFindings = licenseFindings.toSortedSet(),
        copyrightFindings = sortedSetOf()
    )
    val provenance = ArtifactProvenance(sourceArtifact = RemoteArtifact.EMPTY)
    val scanner = ScannerDetails("scanner$resultCount", "v$resultCount", "testConfig")
    return ScanResult(provenance, scanner, summary)
}

class CompositeStorageTest : WordSpec({
    "CompositeStorage" should {
        "construct a composed name" {
            val reader1 = storageMock("reader1")
            val reader2 = storageMock("reader2")
            val writer1 = storageMock("writer1")
            val writer2 = storageMock("writer2")
            val storage = CompositeStorage(listOf(reader1, reader2), listOf(writer1, writer2))

            val compositeName = storage.name

            compositeName shouldBe "composite[readers:[reader1, reader2], writers:[writer1, writer2]]"
        }

        "return an empty result if no readers are configured when asked for an identifier" {
            val storage = CompositeStorage(emptyList(), listOf(mockk()))

            storage.read(ID).shouldBeSuccess {
                it should beEmpty()
            }
        }

        "return the first non-empty, success result from a reader when asked for an identifier" {
            val result = Result.success(listOf(createScanResult(1)))
            val readerErr = storageMock("r1")
            val readerEmptyContainer = storageMock("r2")
            val readerResult = storageMock("r3")
            val readerUnused = storageMock("r4")
            every { readerErr.read(ID) } returns Result.failure(ScanStorageException("an error"))
            every { readerEmptyContainer.read(ID) } returns Result.success(emptyList())
            every { readerResult.read(ID) } returns result

            val storage = CompositeStorage(
                listOf(readerErr, readerEmptyContainer, readerResult, readerUnused),
                emptyList()
            )
            val readResult = storage.read(ID)

            readResult shouldBe result
        }

        "detect a non-empty scan result even if the container contains empty results" {
            val result = Result.success(listOf(createScanResult(0), createScanResult(1)))
            val reader = storageMock("reader")
            every { reader.read(ID) } returns result

            val storage = CompositeStorage(listOf(reader), emptyList())
            val readResult = storage.read(ID)

            readResult shouldBe result
        }

        "return an empty result if no readers are configured when asked for a package" {
            val storage = CompositeStorage(emptyList(), listOf(mockk()))

            storage.read(PACKAGE, CRITERIA).shouldBeSuccess {
                it should beEmpty()
            }
        }

        "return the first non-empty, success result from a reader when asked for a package" {
            val result = Result.success(listOf(createScanResult(1)))
            val readerErr = storageMock("r1")
            val readerEmptyContainer = storageMock("r2")
            val readerResult = storageMock("r3")
            val readerUnused = storageMock("r4")
            every { readerErr.read(PACKAGE, CRITERIA) } returns Result.failure(ScanStorageException("an error"))
            every { readerEmptyContainer.read(PACKAGE, CRITERIA) } returns Result.success(emptyList())
            every { readerResult.read(PACKAGE, CRITERIA) } returns result

            val storage = CompositeStorage(
                listOf(readerErr, readerEmptyContainer, readerResult, readerUnused),
                emptyList()
            )
            val readResult = storage.read(PACKAGE, CRITERIA)

            readResult shouldBe result
        }

        "return a failure result if all readers produce failures" {
            val reader1 = storageMock("r1")
            val reader2 = storageMock("r2")
            every { reader1.read(ID) } returns Result.failure(ScanStorageException("error1"))
            every { reader2.read(ID) } returns Result.failure(ScanStorageException("error2"))
            val storage = CompositeStorage(listOf(reader1, reader2), emptyList())

            storage.read(ID).shouldBeFailure {
                it.message shouldBe "error1, error2"
            }
        }

        "return a success result on write if no writers are configured" {
            val storage = CompositeStorage(listOf(storageMock("reader")), emptyList())

            val result = storage.add(ID, createScanResult(2))

            result.shouldBeSuccess()
        }

        "delegate to all writers to store a scan result" {
            val result = createScanResult(3)
            val writer1 = storageMock("w1")
            val writer2 = storageMock("w2")
            every { writer1.add(ID, result) } returns Result.success(Unit)
            every { writer2.add(ID, result) } returns Result.success(Unit)
            val storage = CompositeStorage(emptyList(), listOf(writer1, writer2))

            val storageResult = storage.add(ID, result)

            storageResult.shouldBeSuccess()
            verify {
                writer1.add(ID, result)
                writer2.add(ID, result)
            }
        }

        "return a failure result if any of the writers returns a failure" {
            val result = createScanResult(2)
            val writer1 = storageMock("w1")
            val writer2 = storageMock("w2")
            every { writer1.add(ID, result) } returns Result.failure(ScanStorageException("failed"))
            every { writer2.add(ID, result) } returns Result.success(Unit)
            val storage = CompositeStorage(emptyList(), listOf(writer1, writer2))

            storage.add(ID, result).shouldBeFailure {
                it.message shouldBe "failed"
            }
        }

        "return a failure result with the combined message of all failing writers" {
            val result = createScanResult(1)
            val writer1 = storageMock("w1")
            val writer2 = storageMock("w2")
            every { writer1.add(ID, result) } returns Result.failure(ScanStorageException("boom1"))
            every { writer2.add(ID, result) } returns Result.failure(ScanStorageException("boom2"))
            val storage = CompositeStorage(emptyList(), listOf(writer1, writer2))

            storage.add(ID, result).shouldBeFailure {
                it.message shouldBe "boom1, boom2"
            }
        }
    }
})
