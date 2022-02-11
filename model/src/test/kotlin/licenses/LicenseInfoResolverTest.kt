/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.model.licenses

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.Matcher
import io.kotest.matchers.be
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.contain
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.neverNullMatcher
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

import java.io.File
import java.lang.IllegalArgumentException
import java.util.SortedSet

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.LicenseSource
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.model.config.LicenseFilenamePatterns
import org.ossreviewtoolkit.model.config.LicenseFindingCuration
import org.ossreviewtoolkit.model.config.LicenseFindingCurationReason
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.PathExcludeReason
import org.ossreviewtoolkit.model.licenses.TestUtils.containLicensesExactly
import org.ossreviewtoolkit.model.utils.FileArchiver
import org.ossreviewtoolkit.model.utils.FileArchiverFileStorage
import org.ossreviewtoolkit.model.utils.getArchivePath
import org.ossreviewtoolkit.utils.core.DeclaredLicenseProcessor
import org.ossreviewtoolkit.utils.core.storage.LocalFileStorage
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.SpdxSingleLicenseExpression
import org.ossreviewtoolkit.utils.spdx.getLicenseText
import org.ossreviewtoolkit.utils.spdx.toSpdx
import org.ossreviewtoolkit.utils.test.createDefault
import org.ossreviewtoolkit.utils.test.shouldNotBeNull
import org.ossreviewtoolkit.utils.test.transformingCollectionEmptyMatcher
import org.ossreviewtoolkit.utils.test.transformingCollectionMatcher

class LicenseInfoResolverTest : WordSpec() {
    init {
        val pkgId = Identifier("Gradle:org.ossreviewtoolkit:ort:1.0.0")
        val vcsInfo = VcsInfo(
            type = VcsType.GIT,
            url = "https://github.com/oss-review-toolkit/ort.git",
            revision = "master"
        )
        val provenance = RepositoryProvenance(
            vcsInfo = vcsInfo,
            resolvedRevision = "0000000000000000000000000000000000000000"
        )

        "resolveLicenseInfo()" should {
            "resolve declared licenses" {
                val licenseInfos = listOf(
                    createLicenseInfo(
                        id = pkgId,
                        declaredLicenses = setOf("Apache-2.0 WITH LLVM-exception", "MIT")
                    )
                )
                val resolver = createResolver(licenseInfos)

                val result = resolver.resolveLicenseInfo(pkgId)

                result.id shouldBe pkgId
                result should containOnlyLicenseSources(LicenseSource.DECLARED)
                result should containLicensesExactly("Apache-2.0 WITH LLVM-exception", "MIT")
                result should containNoLicenseLocations()
                result should containNoCopyrights()
            }

            "resolve detected licenses" {
                val licenseInfos = listOf(
                    createLicenseInfo(
                        id = pkgId,
                        detectedLicenses = listOf(
                            Findings(
                                provenance = provenance,
                                licenses = mapOf(
                                    "Apache-2.0 WITH LLVM-exception" to listOf(
                                        TextLocation("LICENSE", 1),
                                        TextLocation("LICENSE", 21)
                                    ),
                                    "MIT" to listOf(
                                        TextLocation("LICENSE", 31),
                                        TextLocation("LICENSE", 41)
                                    )
                                ).toFindingsSet(),
                                copyrights = setOf(
                                    CopyrightFinding("Copyright Apache-2.0", TextLocation("LICENSE", 1)),
                                    CopyrightFinding("Copyright MIT", TextLocation("LICENSE", 31))
                                ),
                                licenseFindingCurations = emptyList(),
                                pathExcludes = emptyList(),
                                relativeFindingsPath = ""
                            )
                        )
                    )
                )

                val resolver = createResolver(licenseInfos)

                val result = resolver.resolveLicenseInfo(pkgId)

                result.id shouldBe pkgId
                result should containOnlyLicenseSources(LicenseSource.DETECTED)
                result should containLicensesExactly("Apache-2.0 WITH LLVM-exception", "MIT")
                result should containNumberOfLocationsForLicense("Apache-2.0 WITH LLVM-exception", 2)
                result should containLocationForLicense(
                    license = "Apache-2.0 WITH LLVM-exception",
                    provenance = provenance,
                    location = TextLocation("LICENSE", 1),
                    copyrights = setOf(
                        ResolvedCopyrightFinding(
                            statement = "Copyright Apache-2.0",
                            location = TextLocation("LICENSE", 1),
                            matchingPathExcludes = emptyList()
                        )
                    )
                )
                result should containLocationForLicense(
                    license = "Apache-2.0 WITH LLVM-exception",
                    provenance = provenance,
                    location = TextLocation("LICENSE", 21)
                )
                result should containNumberOfLocationsForLicense("MIT", 2)
                result should containLocationForLicense(
                    license = "MIT",
                    provenance = provenance,
                    location = TextLocation("LICENSE", 31),
                    copyrights = setOf(
                        ResolvedCopyrightFinding(
                            statement = "Copyright MIT",
                            location = TextLocation("LICENSE", 31),
                            matchingPathExcludes = emptyList()
                        )
                    )
                )
                result should containLocationForLicense(
                    license = "MIT",
                    provenance = provenance,
                    location = TextLocation("LICENSE", 41)
                )

                result.licenses.find { it.license == "Apache-2.0 WITH LLVM-exception".toSpdx() } shouldNotBeNull {
                    originalExpressions.filter {
                        it.source == LicenseSource.DETECTED
                    }.map { it.expression } shouldContainExactlyInAnyOrder listOf(
                        "Apache-2.0 WITH LLVM-exception".toSpdx()
                    )
                }
            }

            "resolve concluded licenses" {
                val licenseInfos = listOf(
                    createLicenseInfo(
                        id = pkgId,
                        concludedLicense = SpdxExpression.parse("Apache-2.0 WITH LLVM-exception AND MIT")
                    )
                )
                val resolver = createResolver(licenseInfos)

                val result = resolver.resolveLicenseInfo(pkgId)

                result.id shouldBe pkgId
                result should containOnlyLicenseSources(LicenseSource.CONCLUDED)
                result should containLicensesExactly("Apache-2.0 WITH LLVM-exception", "MIT")
                result should containNoLicenseLocations()
                result should containNoCopyrights()
            }

            "resolve copyright statements" {
                val licenseInfos = listOf(
                    createLicenseInfo(
                        id = pkgId,
                        detectedLicenses = listOf(
                            Findings(
                                provenance = provenance,
                                licenses = mapOf(
                                    "Apache-2.0" to listOf(
                                        TextLocation("LICENSE", 1)
                                    )
                                ).toFindingsSet(),
                                copyrights = setOf(
                                    CopyrightFinding("(c) 2009 Holder 1", TextLocation("LICENSE", 1)),
                                    CopyrightFinding("(c) 2010 Holder 1", TextLocation("LICENSE", 2)),
                                    CopyrightFinding("(c) 2010 Holder 2", TextLocation("LICENSE", 3))
                                ),
                                licenseFindingCurations = emptyList(),
                                pathExcludes = emptyList(),
                                relativeFindingsPath = ""
                            )
                        )
                    )
                )

                val resolver = createResolver(licenseInfos)

                val result = resolver.resolveLicenseInfo(pkgId)

                result should containCopyrightsExactly("(c) 2009 Holder 1", "(c) 2010 Holder 1", "(c) 2010 Holder 2")
                result should containFindingsForCopyrightExactly("(c) 2009 Holder 1", TextLocation("LICENSE", 1))
                result should containFindingsForCopyrightExactly("(c) 2010 Holder 1", TextLocation("LICENSE", 2))
                result should containFindingsForCopyrightExactly("(c) 2010 Holder 2", TextLocation("LICENSE", 3))
            }

            "process copyrights by license" {
                val licenseInfos = listOf(
                    createLicenseInfo(
                        id = pkgId,
                        detectedLicenses = listOf(
                            Findings(
                                provenance = provenance,
                                licenses = mapOf(
                                    "Apache-2.0" to listOf(
                                        TextLocation("LICENSE", 1)
                                    ),
                                    "MIT" to listOf(
                                        TextLocation("LICENSE", 50)
                                    )
                                ).toFindingsSet(),
                                copyrights = setOf(
                                    CopyrightFinding("(c) 2009 Holder", TextLocation("LICENSE", 1)),
                                    CopyrightFinding("(c) 2010 Holder", TextLocation("LICENSE", 2)),
                                    CopyrightFinding("(c) 2011 Holder", TextLocation("LICENSE", 50)),
                                    CopyrightFinding("(c) 2012 Holder", TextLocation("LICENSE", 51))
                                ),
                                licenseFindingCurations = emptyList(),
                                pathExcludes = emptyList(),
                                relativeFindingsPath = ""
                            )
                        )
                    )
                )

                val resolver = createResolver(licenseInfos)

                val result = resolver.resolveLicenseInfo(pkgId)

                result should containCopyrightStatementsForLicenseExactly(
                    "Apache-2.0",
                    "(c) 2009 Holder",
                    "(c) 2010 Holder"
                )
                result should containCopyrightStatementsForLicenseExactly(
                    "MIT",
                    "(c) 2011 Holder",
                    "(c) 2012 Holder"
                )
            }

            "mark copyright garbage as garbage" {
                val licenseInfos = listOf(
                    createLicenseInfo(
                        id = pkgId,
                        detectedLicenses = listOf(
                            Findings(
                                provenance = provenance,
                                licenses = mapOf(
                                    "Apache-2.0" to listOf(
                                        TextLocation("LICENSE", 1)
                                    )
                                ).toFindingsSet(),
                                copyrights = setOf(
                                    CopyrightFinding("(c) 2009 Holder 1", TextLocation("LICENSE", 1)),
                                    CopyrightFinding("(c) 2010 Holder 1", TextLocation("LICENSE", 2)),
                                    CopyrightFinding("(c) 2009 Holder 2", TextLocation("LICENSE", 3)),
                                    CopyrightFinding("(c) 2010 Holder 2", TextLocation("LICENSE", 4)),
                                    CopyrightFinding("(c) 2010 Holder 3", TextLocation("LICENSE", 5))
                                ),
                                licenseFindingCurations = emptyList(),
                                pathExcludes = emptyList(),
                                relativeFindingsPath = ""
                            )
                        )
                    )
                )

                val resolver = createResolver(
                    licenseInfos,
                    copyrightGarbage = setOf("(c) 2009 Holder 1", "(c) 2010 Holder 1", "(c) 2009 Holder 2")
                )

                val result = resolver.resolveLicenseInfo(pkgId)

                result should containCopyrightsExactly("(c) 2010 Holder 2", "(c) 2010 Holder 3")
                result should containFindingsForCopyrightExactly("(c) 2010 Holder 2", TextLocation("LICENSE", 4))
                result should containCopyrightGarbageForProvenanceExactly(
                    provenance,
                    "(c) 2009 Holder 1" to TextLocation("LICENSE", 1),
                    "(c) 2010 Holder 1" to TextLocation("LICENSE", 2),
                    "(c) 2009 Holder 2" to TextLocation("LICENSE", 3)
                )
            }

            "apply path excludes" {
                val sourceArtifact = RemoteArtifact(
                    url = "http://example.com",
                    hash = Hash("", HashAlgorithm.NONE)
                )
                val sourceArtifactProvenance = ArtifactProvenance(
                    sourceArtifact = sourceArtifact
                )
                val sourceArtifactPathExclude = PathExclude(
                    pattern = "LICENSE",
                    reason = PathExcludeReason.OTHER
                )
                val vcsPathExclude = PathExclude(
                    pattern = "a/b",
                    reason = PathExcludeReason.OTHER
                )

                val licenseInfos = listOf(
                    createLicenseInfo(
                        id = pkgId,
                        detectedLicenses = listOf(
                            Findings(
                                provenance = provenance,
                                licenses = mapOf(
                                    "Apache-2.0" to listOf(
                                        TextLocation("LICENSE", 1),
                                        TextLocation("a/b", 1)
                                    ),
                                    "MIT" to listOf(
                                        TextLocation("a/b", 4)
                                    )
                                ).toFindingsSet(),
                                copyrights = setOf(
                                    CopyrightFinding("(c) 2010 Holder", TextLocation("LICENSE", 1)),
                                    CopyrightFinding("(c) 2010 Holder", TextLocation("a/b", 1))
                                ),
                                licenseFindingCurations = emptyList(),
                                pathExcludes = listOf(vcsPathExclude),
                                relativeFindingsPath = ""
                            ),
                            Findings(
                                provenance = sourceArtifactProvenance,
                                licenses = mapOf(
                                    "Apache-2.0" to listOf(
                                        TextLocation("LICENSE", 1),
                                        TextLocation("a/b", 1)
                                    )
                                ).toFindingsSet(),
                                copyrights = setOf(
                                    CopyrightFinding("(c) 2010 Holder", TextLocation("LICENSE", 1)),
                                    CopyrightFinding("(c) 2010 Holder", TextLocation("a/b", 1))
                                ),
                                licenseFindingCurations = emptyList(),
                                pathExcludes = listOf(sourceArtifactPathExclude),
                                relativeFindingsPath = ""
                            )
                        )
                    )
                )

                val resolver = createResolver(licenseInfos)

                val result = resolver.resolveLicenseInfo(pkgId)

                result.pathExcludesForLicense(
                    "Apache-2.0", provenance, TextLocation("LICENSE", 1)
                ) should beEmpty()
                result.pathExcludesForLicense(
                    "Apache-2.0", provenance, TextLocation("a/b", 1)
                ) should containExactly(vcsPathExclude)
                result.pathExcludesForLicense(
                    "Apache-2.0", sourceArtifactProvenance, TextLocation("LICENSE", 1)
                ) should containExactly(sourceArtifactPathExclude)
                result.pathExcludesForLicense(
                    "Apache-2.0", sourceArtifactProvenance, TextLocation("a/b", 1)
                ) should beEmpty()

                result.pathExcludesForCopyright(
                    "(c) 2010 Holder", provenance, TextLocation("LICENSE", 1)
                ) should beEmpty()
                result.pathExcludesForCopyright(
                    "(c) 2010 Holder", provenance, TextLocation("a/b", 1)
                ) should containExactly(vcsPathExclude)
                result.pathExcludesForCopyright(
                    "(c) 2010 Holder", sourceArtifactProvenance, TextLocation("LICENSE", 1)
                ) should containExactly(sourceArtifactPathExclude)
                result.pathExcludesForCopyright(
                    "(c) 2010 Holder", sourceArtifactProvenance, TextLocation("a/b", 1)
                ) should beEmpty()

                result.licenses.flatMap { resolvedLicense ->
                    resolvedLicense.originalExpressions.filter { it.source == LicenseSource.DETECTED }
                } shouldContainExactlyInAnyOrder listOf(
                    ResolvedOriginalExpression("Apache-2.0".toSpdx(), LicenseSource.DETECTED, false),
                    ResolvedOriginalExpression("MIT".toSpdx(), LicenseSource.DETECTED, true)
                )
            }

            "apply license finding curations" {
                val curation = LicenseFindingCuration(
                    path = "LICENSE",
                    detectedLicense = "Apache-2.0".toSpdx(),
                    concludedLicense = "MIT".toSpdx(),
                    reason = LicenseFindingCurationReason.INCORRECT
                )

                val licenseInfos = listOf(
                    createLicenseInfo(
                        id = pkgId,
                        detectedLicenses = listOf(
                            Findings(
                                provenance = provenance,
                                licenses = mapOf(
                                    "Apache-2.0" to listOf(
                                        TextLocation("LICENSE", 1)
                                    )
                                ).toFindingsSet(),
                                copyrights = setOf(
                                    CopyrightFinding("(c) 2010 Holder 1", TextLocation("LICENSE", 1))
                                ),
                                licenseFindingCurations = listOf(curation),
                                pathExcludes = emptyList(),
                                relativeFindingsPath = ""
                            )
                        )
                    )
                )

                val resolver = createResolver(licenseInfos)

                val result = resolver.resolveLicenseInfo(pkgId)

                result should containLicensesExactly("MIT")
                result should containLocationForLicense(
                    license = "MIT",
                    provenance = provenance,
                    location = TextLocation("LICENSE", 1),
                    appliedCuration = curation,
                    copyrights = setOf(
                        ResolvedCopyrightFinding(
                            statement = "(c) 2010 Holder 1",
                            location = TextLocation("LICENSE", 1),
                            matchingPathExcludes = emptyList()
                        )
                    )
                )
                result.licenses.flatMap { resolvedLicense ->
                    resolvedLicense.originalExpressions.map { it.expression }
                } shouldContainExactlyInAnyOrder listOf("MIT".toSpdx())
            }

            "contain a list of the original license expressions" {
                val mitLicense = "MIT"
                val apacheLicense = "Apache-2.0 WITH LLVM-exception"
                val gplLicense = "GPL-2.0-only"
                val bsdLicense = "0BSD"

                val licenseInfos = listOf(
                    createLicenseInfo(
                        id = pkgId,
                        declaredLicenses = setOf("$apacheLicense or $gplLicense", mitLicense),
                        detectedLicenses = listOf(
                            Findings(
                                provenance = provenance,
                                licenses = mapOf(
                                    "$gplLicense OR $bsdLicense" to listOf(
                                        TextLocation("LICENSE", 1),
                                        TextLocation("LICENSE", 21)
                                    ),
                                    bsdLicense to listOf(
                                        TextLocation("LICENSE", 31),
                                        TextLocation("LICENSE", 41)
                                    )
                                ).toFindingsSet(),
                                copyrights = setOf(
                                    CopyrightFinding(
                                        "Copyright GPL 2.0 OR BSD Zero Clause",
                                        TextLocation("LICENSE", 1)
                                    ),
                                    CopyrightFinding("Copyright BSD Zero Clause", TextLocation("LICENSE", 31))
                                ),
                                licenseFindingCurations = emptyList(),
                                pathExcludes = emptyList(),
                                relativeFindingsPath = ""
                            )
                        ),
                        concludedLicense = "$apacheLicense OR $gplLicense".toSpdx()
                    )
                )
                val resolver = createResolver(licenseInfos)

                val expectedDeclaredSpdxExpression =
                    DeclaredLicenseProcessor.process(setOf("$apacheLicense or $gplLicense", mitLicense)).spdxExpression
                val expectedDetectedSpdxExpressions =
                    arrayOf("$gplLicense OR $bsdLicense".toSpdx(), bsdLicense.toSpdx())
                val expectedConcludedSpdxExpression = "$apacheLicense OR $gplLicense".toSpdx()

                val result: ResolvedLicenseInfo = resolver.resolveLicenseInfo(pkgId)
                result should containOnlyLicenseSources(
                    LicenseSource.DECLARED,
                    LicenseSource.DETECTED,
                    LicenseSource.CONCLUDED
                )
                result should containLicenseExpressionsExactlyBySource(
                    LicenseSource.DECLARED,
                    expectedDeclaredSpdxExpression
                )
                result should containLicenseExpressionsExactlyBySource(
                    LicenseSource.DETECTED,
                    *expectedDetectedSpdxExpressions
                )
                result should containLicenseExpressionsExactlyBySource(
                    LicenseSource.CONCLUDED,
                    expectedConcludedSpdxExpression
                )
                result should containLicensesExactly(apacheLicense, gplLicense, mitLicense, bsdLicense)
                result should containNumberOfLocationsForLicense(gplLicense, 2)
                result should containNumberOfLocationsForLicense(bsdLicense, 4)
            }

            "resolve copyrights from authors if enabled" {
                val licenseInfos = listOf(
                    createLicenseInfo(
                        id = pkgId,
                        authors = authors,
                        declaredLicenses = declaredLicenses
                    )
                )
                val resolver = createResolver(licenseInfos, addAuthorsToCopyrights = true)

                val result = resolver.resolveLicenseInfo(pkgId)
                result should containCopyrightStatementsForLicenseExactly(
                    "LicenseRef-a",
                    "Copyright (C) The Author", "Copyright (C) The Other Author"
                )
                result should containCopyrightStatementsForLicenseExactly(
                    "LicenseRef-b",
                    "Copyright (C) The Author", "Copyright (C) The Other Author"
                )
            }

            "not resolve copyrights from authors if disabled" {
                val licenseInfos = listOf(
                    createLicenseInfo(
                        id = pkgId,
                        authors = authors,
                        declaredLicenses = declaredLicenses
                    )
                )
                val resolver = createResolver(licenseInfos, addAuthorsToCopyrights = false)

                val result = resolver.resolveLicenseInfo(pkgId)
                result should containCopyrightStatementsForLicenseExactly("LicenseRef-a")
                result should containCopyrightStatementsForLicenseExactly("LicenseRef-b")
            }
        }

        "resolveLicenseFiles()" should {
            "create the expected result" {
                val licenseInfos = listOf(
                    createLicenseInfo(
                        id = pkgId,
                        detectedLicenses = listOf(
                            Findings(
                                provenance = provenance,
                                licenses = mapOf(
                                    "Apache-2.0" to listOf(
                                        TextLocation("other", 1)
                                    ),
                                    "MIT" to listOf(
                                        TextLocation("LICENSE", 3, 20)
                                    )
                                ).toFindingsSet(),
                                copyrights = setOf(
                                    CopyrightFinding("Copyright 2020 Holder", TextLocation("LICENSE", 1))
                                ),
                                licenseFindingCurations = emptyList(),
                                pathExcludes = emptyList(),
                                relativeFindingsPath = ""
                            )
                        )
                    )
                )

                val archiveDir = File("src/test/assets/archive")
                val archiver = FileArchiver(
                    patterns = LicenseFilenamePatterns.DEFAULT.licenseFilenames,
                    storage = LocalFileStorage(archiveDir)
                )
                val resolver = createResolver(licenseInfos, archiver = archiver)

                val result = resolver.resolveLicenseFiles(pkgId)

                archiver.storage.shouldBeTypeOf<FileArchiverFileStorage>().apply {
                    withClue(archiveDir.resolve(getArchivePath(provenance))) {
                        hasArchive(provenance) shouldBe true
                    }
                }
                result.id shouldBe pkgId
                result.files should haveSize(1)

                val file = result.files.first()
                file.provenance shouldBe provenance
                file.path shouldBe "LICENSE"
                file.file.readText() shouldBe "Copyright 2020 Holder\n\n${getLicenseText("MIT")}"
                file.licenses should containLicensesExactly("MIT")
                file.licenses should containLocationForLicense(
                    license = "MIT",
                    provenance = provenance,
                    location = TextLocation("LICENSE", 3, 20),
                    copyrights = setOf(
                        ResolvedCopyrightFinding(
                            statement = "Copyright 2020 Holder",
                            location = TextLocation("LICENSE", 1),
                            matchingPathExcludes = emptyList()
                        )
                    )
                )
            }
        }
    }

    private fun createResolver(
        data: List<LicenseInfo>,
        copyrightGarbage: Set<String> = emptySet(),
        addAuthorsToCopyrights: Boolean = false,
        archiver: FileArchiver = FileArchiver.createDefault()
    ) = LicenseInfoResolver(
        provider = SimpleLicenseInfoProvider(data),
        copyrightGarbage = CopyrightGarbage(copyrightGarbage.toSortedSet()),
        addAuthorsToCopyrights = addAuthorsToCopyrights,
        archiver = archiver
    )

    private fun createLicenseInfo(
        id: Identifier,
        authors: SortedSet<String> = sortedSetOf(),
        declaredLicenses: Set<String> = emptySet(),
        detectedLicenses: List<Findings> = emptyList(),
        concludedLicense: SpdxExpression? = null
    ) =
        LicenseInfo(
            id = id,
            declaredLicenseInfo = DeclaredLicenseInfo(
                authors = authors,
                licenses = declaredLicenses,
                processed = DeclaredLicenseProcessor.process(declaredLicenses),
                appliedCurations = emptyList()
            ),
            detectedLicenseInfo = DetectedLicenseInfo(
                findings = detectedLicenses
            ),
            concludedLicenseInfo = ConcludedLicenseInfo(
                concludedLicense = concludedLicense,
                appliedCurations = emptyList()
            )
        )
}

private class SimpleLicenseInfoProvider(licenseInfo: List<LicenseInfo>) : LicenseInfoProvider {
    private val licenseInfoById = licenseInfo.associateBy { it.id }

    override fun get(id: Identifier) =
        licenseInfoById[id] ?: throw IllegalArgumentException("No license info for '${id.toCoordinates()}' available.")
}

private fun Map<String, List<TextLocation>>.toFindingsSet(): Set<LicenseFinding> =
    flatMap { (license, locations) ->
        locations.map { LicenseFinding(license, it) }
    }.toSet()

private fun containNoLicenseLocations(): Matcher<ResolvedLicenseInfo?> =
    transformingCollectionEmptyMatcher { resolvedLicenseInfo ->
        resolvedLicenseInfo.flatMap { it.locations }
    }

private fun containNoCopyrights(): Matcher<ResolvedLicenseInfo?> =
    transformingCollectionEmptyMatcher { resolvedLicenseInfo ->
        resolvedLicenseInfo.flatMap { resolvedLicense -> resolvedLicense.locations.flatMap { it.copyrights } }
    }

private fun containCopyrightsExactly(vararg copyrights: String): Matcher<Iterable<ResolvedLicense>?> =
    transformingCollectionMatcher(
        expected = copyrights.toSet(),
        matcher = ::containExactlyInAnyOrder
    ) { resolvedLicenses ->
        resolvedLicenses.flatMapTo(mutableSetOf()) { license ->
            license.locations.flatMap { location ->
                location.copyrights.map { it.statement }
            }
        }
    }

private fun containFindingsForCopyrightExactly(
    copyright: String,
    vararg findings: TextLocation
): Matcher<Iterable<ResolvedLicense>?> =
    transformingCollectionMatcher(
        expected = findings.toSet(),
        matcher = ::containExactlyInAnyOrder
    ) { resolvedLicenses ->
        resolvedLicenses.flatMapTo(mutableSetOf()) { license ->
            license.locations.flatMap { licenseLocations ->
                licenseLocations.copyrights.filter { it.statement == copyright }.map { it.location }
            }
        }
    }

private fun containCopyrightGarbageForProvenanceExactly(
    provenance: Provenance,
    vararg findings: Pair<String, TextLocation>
): Matcher<ResolvedLicenseInfo?> =
    transformingCollectionMatcher(
        expected = findings.toList(),
        matcher = ::containExactlyInAnyOrder
    ) { resolvedLicenseInfo ->
        resolvedLicenseInfo.copyrightGarbage[provenance].orEmpty().map { Pair(it.statement, it.location) }
    }

private fun containCopyrightStatementsForLicenseExactly(
    license: String,
    vararg copyrights: String
): Matcher<ResolvedLicenseInfo?> =
    transformingCollectionMatcher(
        expected = copyrights.toList(),
        matcher = ::containExactlyInAnyOrder
    ) { resolvedLicenseInfo ->
        resolvedLicenseInfo[SpdxSingleLicenseExpression.parse(license)]?.getCopyrights(process = false).orEmpty()
    }

private fun containOnlyLicenseSources(vararg licenseSources: LicenseSource): Matcher<ResolvedLicenseInfo?> =
    transformingCollectionMatcher(
        expected = licenseSources.toSet(),
        matcher = ::containExactlyInAnyOrder
    ) { resolvedLicenseInfo -> resolvedLicenseInfo.flatMap { it.sources }.toSet() }

private fun containLicenseExpressionsExactlyBySource(
    source: LicenseSource,
    vararg expressions: SpdxExpression?
): Matcher<ResolvedLicenseInfo?> =
    transformingCollectionMatcher(
        expected = expressions.toSet(),
        matcher = ::containExactlyInAnyOrder
    ) { resolvedLicenseInfo ->
        resolvedLicenseInfo.licenses.flatMapTo(mutableSetOf()) { resolvedLicense ->
            resolvedLicense.originalExpressions.filter { it.source == source }.map { it.expression }
        }
    }

private fun containNumberOfLocationsForLicense(license: String, count: Int): Matcher<ResolvedLicenseInfo?> =
    neverNullMatcher { value ->
        val actualCount = value[SpdxSingleLicenseExpression.parse(license)]?.locations?.size ?: 0
        be(count).test(actualCount)
    }

private fun containLocationForLicense(
    license: String,
    provenance: Provenance,
    location: TextLocation,
    appliedCuration: LicenseFindingCuration? = null,
    matchingPathExcludes: List<PathExclude> = emptyList(),
    copyrights: Set<ResolvedCopyrightFinding> = emptySet()
): Matcher<Iterable<ResolvedLicense>?> =
    neverNullMatcher { value ->
        val expectedLocation =
            ResolvedLicenseLocation(
                provenance,
                location,
                appliedCuration,
                matchingPathExcludes,
                copyrights
            )

        val locations = value.find { it.license == SpdxSingleLicenseExpression.parse(license) }?.locations.orEmpty()

        contain(expectedLocation).test(locations)
    }

private fun ResolvedLicenseInfo.pathExcludesForLicense(
    license: String,
    provenance: Provenance,
    location: TextLocation
) =
    find { it.license == SpdxSingleLicenseExpression.parse(license) }
        ?.locations
        ?.find { it.provenance == provenance && it.location == location }
        ?.matchingPathExcludes
        ?.toSet().orEmpty()

private fun ResolvedLicenseInfo.pathExcludesForCopyright(
    copyright: String,
    provenance: Provenance,
    location: TextLocation
) =
    flatMap { license -> license.locations.filter { it.provenance == provenance } }
        .flatMap { it.copyrights }
        .find { it.statement == copyright && it.location == location }
        ?.matchingPathExcludes
        ?.toSet().orEmpty()
