/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.analyzer.managers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.WordSpec
import io.kotest.core.test.TestCase
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.contain
import io.kotest.matchers.types.beTheSameInstanceAs

import java.io.File

import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.utils.common.calculateHash
import org.ossreviewtoolkit.utils.common.toHexString
import org.ossreviewtoolkit.utils.spdx.SpdxModelMapper
import org.ossreviewtoolkit.utils.spdx.model.SpdxChecksum
import org.ossreviewtoolkit.utils.spdx.model.SpdxDocument
import org.ossreviewtoolkit.utils.spdx.model.SpdxExternalDocumentReference
import org.ossreviewtoolkit.utils.spdx.model.SpdxPackage
import org.ossreviewtoolkit.utils.spdx.model.SpdxRelationship
import org.ossreviewtoolkit.utils.test.createTestTempDir
import org.ossreviewtoolkit.utils.test.shouldNotBeNull

class SpdxResolvedDocumentTest : WordSpec() {
    private lateinit var tempDir: File

    /** A mock server used for testing whether external references can reference documents via the Internet. */
    private lateinit var server: WireMockServer

    override fun beforeSpec(spec: Spec) {
        server = WireMockServer(
            WireMockConfiguration.options()
                .dynamicPort()
        )
        server.start()
    }

    override fun beforeTest(testCase: TestCase) {
        tempDir = createTestTempDir()

        server.resetAll()
    }

    override fun afterSpec(spec: Spec) {
        server.stop()
    }

    init {
        "load" should {
            "use the given loader to load a root document" {
                val loader = SpdxDocumentCache()
                val root = loader.load(BASE_DOCUMENT_FILE)

                val resolvedDoc = SpdxResolvedDocument.load(loader, BASE_DOCUMENT_FILE, MANAGER_NAME)

                resolvedDoc.rootDocument should beTheSameInstanceAs(root)
            }

            "load a root document without external references" {
                val rootFile = createSpdxDocument(1, listOf(createPackage(1)))

                val resolvedDoc = SpdxResolvedDocument.load(SpdxDocumentCache(), rootFile, MANAGER_NAME)

                resolvedDoc.referencedDocuments.keys should beEmpty()
            }

            "load the documents referenced from the root document" {
                val extPackage10 = createPackage(10)
                val extPackage11 = createPackage(11)
                val extPackage20 = createPackage(12)
                val referencedDoc1 = createSpdxDocument(2, listOf(extPackage10, extPackage11))
                val referencedDoc2 = createSpdxDocument(3, listOf(extPackage20))

                val ref1 = referencedDoc1.toExternalReference(1)
                val ref2 = referencedDoc2.toExternalReference(2)
                val rootFile =
                    createSpdxDocument(1, packages = listOf(createPackage(1)), references = listOf(ref1, ref2))

                val resolvedDoc = SpdxResolvedDocument.load(SpdxDocumentCache(), rootFile, MANAGER_NAME)

                resolvedDoc.referencedDocuments.keys should containExactlyInAnyOrder(ref1, ref2)

                resolvedDoc.referencedDocuments[ref1] shouldNotBeNull {
                    packages should containExactly(extPackage10, extPackage11)
                }
            }

            "load referenced documents transitively" {
                val extPackage = createPackage(30)
                val referencedDocTrans = createSpdxDocument(4, listOf(extPackage))
                val refTrans = referencedDocTrans.toExternalReference(3)

                val referencedDoc1 = createSpdxDocument(2, listOf(createPackage(10)))
                val referencedDoc2 = createSpdxDocument(3, listOf(createPackage(20)), references = listOf(refTrans))
                val ref1 = referencedDoc1.toExternalReference(1)
                val ref2 = referencedDoc2.toExternalReference(2)
                val rootFile =
                    createSpdxDocument(1, packages = listOf(createPackage(1)), references = listOf(ref1, ref2))

                val resolvedDoc = SpdxResolvedDocument.load(SpdxDocumentCache(), rootFile, MANAGER_NAME)

                resolvedDoc.referencedDocuments.keys should containExactlyInAnyOrder(ref1, ref2, refTrans)

                resolvedDoc.referencedDocuments[refTrans] shouldNotBeNull {
                    packages should containExactly(extPackage)
                }
            }

            "handle cyclic references" {
                val referencedDoc3 = createSpdxDocument(3, listOf(createPackage(30)))
                val ref3 = referencedDoc3.toExternalReference(3)
                val referencedDoc2 = createSpdxDocument(2, listOf(createPackage(20)), references = listOf(ref3))
                val ref2 = referencedDoc2.toExternalReference(2)
                val referencedDoc1 = createSpdxDocument(1, listOf(createPackage(10)), references = listOf(ref2))
                val ref1 = referencedDoc1.toExternalReference(1)

                // Overwrite 3rd document with a cyclic reference
                createSpdxDocument(3, listOf(createPackage(30)), references = listOf(ref1))
                val rootFile = createSpdxDocument(0, listOf(createPackage(1)), references = listOf(ref1))

                val resolvedDoc = SpdxResolvedDocument.load(SpdxDocumentCache(), rootFile, MANAGER_NAME)

                resolvedDoc.referencedDocuments.keys should containExactlyInAnyOrder(ref1, ref2, ref3)
            }
        }

        "getSpdxPackageForId" should {
            "return a package from the root document" {
                val resolvedDoc = SpdxResolvedDocument.load(SpdxDocumentCache(), BASE_DOCUMENT_FILE, MANAGER_NAME)

                val issues = mutableListOf<OrtIssue>()
                resolvedDoc.getSpdxPackageForId("SPDXRef-Package-xyz", issues) shouldNotBeNull {
                    name shouldBe "xyz"
                    versionInfo shouldBe "0.1.0"
                }

                issues should beEmpty()
            }

            "return a package declared in an external reference" {
                val externalPackage = createPackage(1)
                val refDocument = createSpdxDocument(2, listOf(externalPackage, createPackage(2)))
                val otherRefDocument = createSpdxDocument(3, listOf(createPackage(3)))
                val ref = refDocument.toExternalReference(1)
                val otherRef = otherRefDocument.toExternalReference(2)
                val rootFile = createSpdxDocument(1, listOf(createPackage(10)), references = listOf(otherRef, ref))
                val pkgId = "${ref.externalDocumentId}:${externalPackage.spdxId}"

                val resolvedDoc = SpdxResolvedDocument.load(SpdxDocumentCache(), rootFile, MANAGER_NAME)

                val issues = mutableListOf<OrtIssue>()
                resolvedDoc.getSpdxPackageForId(pkgId, issues) shouldBe externalPackage

                issues should beEmpty()
            }

            "create an issue for a package, which cannot be resolved" {
                val identifier = "unknownPackageId"
                val resolvedDoc = SpdxResolvedDocument.load(SpdxDocumentCache(), BASE_DOCUMENT_FILE, MANAGER_NAME)

                val issues = mutableListOf<OrtIssue>()
                resolvedDoc.getSpdxPackageForId(identifier, issues) should beNull()

                with(issues.single()) {
                    severity shouldBe Severity.ERROR
                    source shouldBe MANAGER_NAME
                    message should contain(identifier)
                }
            }

            "create an issue for an external reference with an invalid URI" {
                val reference = SpdxExternalDocumentReference("someDocument", "?Not a valid URI?!!", DUMMY_CHECKSUM)
                val identifier = "${reference.externalDocumentId}:somePackage"
                val doc = createSpdxDocument(1, listOf(createPackage(1)), references = listOf(reference))

                val resolvedDoc = SpdxResolvedDocument.load(SpdxDocumentCache(), doc, MANAGER_NAME)

                val issues = mutableListOf<OrtIssue>()
                resolvedDoc.getSpdxPackageForId(identifier, issues) should beNull()

                with(issues.single()) {
                    severity shouldBe Severity.ERROR
                    source shouldBe MANAGER_NAME
                    message should contain(reference.externalDocumentId)
                    message should contain(reference.spdxDocument)
                }
            }

            "create an issue for an external reference pointing to a non-existing file" {
                val reference = SpdxExternalDocumentReference("someDocument", "nonExisting.spdx.yml", DUMMY_CHECKSUM)
                val identifier = "${reference.externalDocumentId}:somePackage"
                val doc = createSpdxDocument(1, listOf(createPackage(1)), references = listOf(reference))

                val resolvedDoc = SpdxResolvedDocument.load(SpdxDocumentCache(), doc, MANAGER_NAME)

                val issues = mutableListOf<OrtIssue>()
                resolvedDoc.getSpdxPackageForId(identifier, issues) should beNull()

                with(issues.single()) {
                    severity shouldBe Severity.ERROR
                    source shouldBe MANAGER_NAME
                    message should contain(reference.externalDocumentId)
                    message should contain(reference.spdxDocument)
                    message should contain("does not exist")
                }
            }

            "create an issue for an external reference with a wrong checksum" {
                val externalPackage = createPackage(42)
                val refDocument = createSpdxDocument(2, listOf(externalPackage))
                val ref = refDocument.toExternalReference(1).copy(checksum = DUMMY_CHECKSUM)
                val identifier = "${ref.externalDocumentId}:${externalPackage.spdxId}"
                val rootDoc = createSpdxDocument(1, listOf(createPackage(1)), references = listOf(ref))

                val resolvedDoc = SpdxResolvedDocument.load(SpdxDocumentCache(), rootDoc, MANAGER_NAME)

                val issues = mutableListOf<OrtIssue>()
                resolvedDoc.getSpdxPackageForId(identifier, issues) shouldBe externalPackage

                with(issues.single()) {
                    severity shouldBe Severity.WARNING
                    source shouldBe MANAGER_NAME
                    message should contain(ref.externalDocumentId)
                    message should contain(ref.spdxDocument)
                    message should contain(DUMMY_CHECKSUM.checksumValue)
                }
            }

            "return a package from an external document downloaded from a server" {
                val externalPackage = createPackage(111)
                val refDocument = createSpdxDocument(2, listOf(externalPackage))
                val documentPath = "/documents/spdx/external_package.spdx.yml"
                val ref = refDocument.toExternalReference(1)
                    .copy(spdxDocument = "http://localhost:${server.port()}$documentPath")
                val identifier = "${ref.externalDocumentId}:${externalPackage.spdxId}"
                val rootDoc = createSpdxDocument(1, listOf(createPackage(1)), references = listOf(ref))

                server.stubFor(
                    get(urlPathEqualTo(documentPath))
                        .willReturn(
                            aResponse().withStatus(200)
                                .withBody(refDocument.readText())
                        )
                )

                val resolvedDoc = SpdxResolvedDocument.load(SpdxDocumentCache(), rootDoc, MANAGER_NAME)

                val issues = mutableListOf<OrtIssue>()
                resolvedDoc.getSpdxPackageForId(identifier, issues) shouldBe externalPackage

                issues should beEmpty()
            }

            "verify the checksum when downloading an external document" {
                val externalPackage = createPackage(222)
                val refDocument = createSpdxDocument(2, listOf(externalPackage))
                val documentPath = "/documents/spdx/package_with_wrong_checksum.spdx.yml"
                val ref = refDocument.toExternalReference(1)
                    .copy(spdxDocument = "http://localhost:${server.port()}$documentPath", checksum = DUMMY_CHECKSUM)
                val identifier = "${ref.externalDocumentId}:${externalPackage.spdxId}"
                val rootDoc = createSpdxDocument(1, listOf(createPackage(1)), references = listOf(ref))

                server.stubFor(
                    get(urlPathEqualTo(documentPath))
                        .willReturn(
                            aResponse().withStatus(200)
                                .withBody(refDocument.readText())
                        )
                )

                val resolvedDoc = SpdxResolvedDocument.load(SpdxDocumentCache(), rootDoc, MANAGER_NAME)

                val issues = mutableListOf<OrtIssue>()
                resolvedDoc.getSpdxPackageForId(identifier, issues) shouldBe externalPackage

                with(issues.single()) {
                    severity shouldBe Severity.WARNING
                    source shouldBe MANAGER_NAME
                    message should contain(ref.externalDocumentId)
                    message should contain(ref.spdxDocument)
                    message should contain(DUMMY_CHECKSUM.checksumValue)
                }
            }

            "handle a failure when downloading an external document" {
                val errorRef = SpdxExternalDocumentReference(
                    "error",
                    "http://localhost:${server.port()}/doc.spdx.json",
                    DUMMY_CHECKSUM
                )
                val identifier = "${errorRef.externalDocumentId}:somePackage"
                val rootDoc = createSpdxDocument(1, listOf(createPackage(1)), references = listOf(errorRef))

                server.stubFor(
                    get(anyUrl())
                        .willReturn(aResponse().withStatus(400))
                )

                val resolvedDoc = SpdxResolvedDocument.load(SpdxDocumentCache(), rootDoc, MANAGER_NAME)

                val issues = mutableListOf<OrtIssue>()
                resolvedDoc.getSpdxPackageForId(identifier, issues) should beNull()

                with(issues.single()) {
                    severity shouldBe Severity.ERROR
                    source shouldBe MANAGER_NAME
                    message should contain(errorRef.externalDocumentId)
                    message should contain(errorRef.spdxDocument)
                    message should contain("download")
                }
            }
        }
    }

    /**
     * Create an [SpdxDocument] in [tempDir] with identifiers derived from [index] that contains the given [packages],
     * [relations], and [references].
     */
    private fun createSpdxDocument(
        index: Int,
        packages: List<SpdxPackage>,
        relations: List<SpdxRelationship> = emptyList(),
        references: List<SpdxExternalDocumentReference> = emptyList()
    ): File {
        val document = spdxBaseDocument.copy(
            name = "testDocument$index",
            packages = packages,
            relationships = relations,
            externalDocumentRefs = references
        )

        val file = tempDir.resolve("external$index.spdx.yml")
        SpdxModelMapper.write(file, document)
        return file
    }
}

/** Prefix for SPDX identifiers generated by tests. */
private const val SPDX_REF = "SPDXRef-Package-Dummy"

/** References the SPDX file used to define the base document. */
private val BASE_DOCUMENT_FILE =
    File("src/funTest/assets/projects/synthetic/spdx/project-xyz-with-inline-packages.spdx.yml")

/** The name of the package manager used by tests. */
private const val MANAGER_NAME = "TestSpdxDocumentFile"

/** Constant for a checksum to be used in case no correct hash value is needed or available. */
private val DUMMY_CHECKSUM = SpdxChecksum(SpdxChecksum.Algorithm.SHA1, "0123456789012345678901234567890123456789")

/** Holds an SPDX document that is used as basis when creating documents for tests. */
private val spdxBaseDocument: SpdxDocument = SpdxModelMapper.read(BASE_DOCUMENT_FILE)

/**
 * Create a synthetic test package based in the given [index].
 */
private fun createPackage(index: Int): SpdxPackage =
    SpdxPackage(
        spdxId = packageId(index),
        name = "testPackage$index",
        copyrightText = "copyright$index",
        downloadLocation = "/downloads/file$index",
        licenseConcluded = "ASL-2",
        licenseDeclared = "ASL-2",
        packageFilename = "package$index"
    )

/**
 * Generate the ID of a test package based on the given [index].
 */
private fun packageId(index: Int): String = "$SPDX_REF-$index"

/**
 * Generate the ID of a test document reference based on the given [index].
 */
private fun referenceId(index: Int): String = "DocumentRef-ref$index"

/**
 * Create an [SpdxExternalDocumentReference] that points to this file with an ID derived from [index].
 */
private fun File.toExternalReference(index: Int): SpdxExternalDocumentReference {
    val hash = calculateHash(this)
    return SpdxExternalDocumentReference(
        externalDocumentId = referenceId(index),
        spdxDocument = name,
        checksum = SpdxChecksum(SpdxChecksum.Algorithm.SHA1, hash.toHexString())
    )
}
