/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

import io.kotest.assertions.show.show
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.neverNullMatcher
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.lang.IllegalArgumentException

import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.LicenseSource
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.model.config.LicenseFindingCuration
import org.ossreviewtoolkit.model.config.LicenseFindingCurationReason
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.PathExcludeReason
import org.ossreviewtoolkit.model.config.VcsMatcher
import org.ossreviewtoolkit.model.utils.SimplePackageConfigurationProvider
import org.ossreviewtoolkit.spdx.SpdxExpression
import org.ossreviewtoolkit.spdx.SpdxSingleLicenseExpression
import org.ossreviewtoolkit.spdx.toSpdx
import org.ossreviewtoolkit.utils.DeclaredLicenseProcessor

class LicenseInfoResolverTest : WordSpec() {
    init {
        val pkgId = Identifier("Gradle:org.ossreviewtoolkit:ort:1.0.0")
        val vcsInfo = VcsInfo(VcsType.GIT, "https://github.com/oss-review-toolkit/ort.git", "master", "master")
        val provenance = Provenance(vcsInfo = vcsInfo)

        "resolveLicenseInfo()" should {
            "resolve declared licenses" {
                val data = listOf(
                    createLicenseInfo(
                        id = pkgId,
                        declaredLicenses = setOf("Apache-2.0 WITH LLVM-exception", "MIT")
                    )
                )
                val resolver = createResolver(data)

                val result = resolver.resolveLicenseInfo(pkgId)

                result.id shouldBe pkgId
                result should containOnlyLicenseSources(LicenseSource.DECLARED)
                result should containLicensesExactly("Apache-2.0 WITH LLVM-exception", "MIT")
                result should containNoLicenseLocations()
                result should containNoCopyrights()
            }

            "resolve detected licenses" {
                val data = listOf(
                    createLicenseInfo(
                        id = pkgId,
                        detectedLicenses = listOf(
                            Findings(
                                provenance = provenance,
                                licenses = mapOf(
                                    "Apache-2.0 WITH LLVM-exception" to listOf(
                                        TextLocation("LICENSE", 1, 1),
                                        TextLocation("LICENSE", 21, 21)
                                    ),
                                    "MIT" to listOf(
                                        TextLocation("LICENSE", 31, 31),
                                        TextLocation("LICENSE", 41, 41)
                                    )
                                ).toFindingsSet(),
                                copyrights = setOf(
                                    CopyrightFinding("Copyright Apache-2.0", TextLocation("LICENSE", 1, 1)),
                                    CopyrightFinding("Copyright MIT", TextLocation("LICENSE", 31, 31))
                                )
                            )
                        )
                    )
                )

                val resolver = createResolver(data)

                val result = resolver.resolveLicenseInfo(pkgId)

                result.id shouldBe pkgId
                result should containOnlyLicenseSources(LicenseSource.DETECTED)
                result should containLicensesExactly("Apache-2.0 WITH LLVM-exception", "MIT")
                result should containNumberOfLocationsForLicense("Apache-2.0 WITH LLVM-exception", 2)
                result should containLocationForLicense(
                    license = "Apache-2.0 WITH LLVM-exception",
                    provenance = provenance,
                    location = TextLocation("LICENSE", 1, 1),
                    copyrights = setOf(
                        ResolvedCopyright(
                            statement = "Copyright Apache-2.0",
                            findings = setOf(
                                ResolvedCopyrightFinding(
                                    statement = "Copyright Apache-2.0",
                                    location = TextLocation("LICENSE", 1, 1),
                                    matchingPathExcludes = emptyList(),
                                    isGarbage = false
                                )
                            ),
                            isGarbage = false
                        )
                    )
                )
                result should containLocationForLicense(
                    license = "Apache-2.0 WITH LLVM-exception",
                    provenance = provenance,
                    location = TextLocation("LICENSE", 21, 21)
                )
                result should containNumberOfLocationsForLicense("MIT", 2)
                result should containLocationForLicense(
                    license = "MIT",
                    provenance = provenance,
                    location = TextLocation("LICENSE", 31, 31),
                    copyrights = setOf(
                        ResolvedCopyright(
                            statement = "Copyright MIT",
                            findings = setOf(
                                ResolvedCopyrightFinding(
                                    statement = "Copyright MIT",
                                    location = TextLocation("LICENSE", 31, 31),
                                    matchingPathExcludes = emptyList(),
                                    isGarbage = false
                                )
                            ),
                            isGarbage = false
                        )
                    )
                )
                result should containLocationForLicense(
                    license = "MIT",
                    provenance = provenance,
                    location = TextLocation("LICENSE", 41, 41)
                )
            }

            "resolve concluded licenses" {
                val data = listOf(
                    createLicenseInfo(
                        id = pkgId,
                        concludedLicense = SpdxExpression.parse("Apache-2.0 WITH LLVM-exception AND MIT")
                    )
                )
                val resolver = createResolver(data)

                val result = resolver.resolveLicenseInfo(pkgId)

                result.id shouldBe pkgId
                result should containOnlyLicenseSources(LicenseSource.CONCLUDED)
                result should containLicensesExactly("Apache-2.0 WITH LLVM-exception", "MIT")
                result should containNoLicenseLocations()
                result should containNoCopyrights()
            }

            "process copyright statements" {
                val data = listOf(
                    createLicenseInfo(
                        id = pkgId,
                        detectedLicenses = listOf(
                            Findings(
                                provenance = provenance,
                                licenses = mapOf(
                                    "Apache-2.0" to listOf(
                                        TextLocation("LICENSE", 1, 1)
                                    )
                                ).toFindingsSet(),
                                copyrights = setOf(
                                    CopyrightFinding("(c) 2009 Holder 1", TextLocation("LICENSE", 1, 1)),
                                    CopyrightFinding("(c) 2010 Holder 1", TextLocation("LICENSE", 2, 2)),
                                    CopyrightFinding("(c) 2010 Holder 2", TextLocation("LICENSE", 3, 3))
                                )
                            )
                        )
                    )
                )

                val resolver = createResolver(data)

                val result = resolver.resolveLicenseInfo(pkgId)

                result should containCopyrightsExactly("(c) 2009-2010 Holder 1", "(c) 2010 Holder 2")
                result should containFindingsForCopyrightExactly(
                    "(c) 2009-2010 Holder 1",
                    "(c) 2009 Holder 1" to TextLocation("LICENSE", 1, 1),
                    "(c) 2010 Holder 1" to TextLocation("LICENSE", 2, 2)
                )
                result should containFindingsForCopyrightExactly(
                    "(c) 2010 Holder 2",
                    "(c) 2010 Holder 2" to TextLocation("LICENSE", 3, 3)
                )
            }

            "mark copyright garbage as garbage" {
                val data = listOf(
                    createLicenseInfo(
                        id = pkgId,
                        detectedLicenses = listOf(
                            Findings(
                                provenance = provenance,
                                licenses = mapOf(
                                    "Apache-2.0" to listOf(
                                        TextLocation("LICENSE", 1, 1)
                                    )
                                ).toFindingsSet(),
                                copyrights = setOf(
                                    CopyrightFinding("(c) 2009 Holder 1", TextLocation("LICENSE", 1, 1)),
                                    CopyrightFinding("(c) 2010 Holder 1", TextLocation("LICENSE", 2, 2)),
                                    CopyrightFinding("(c) 2010 Holder 2", TextLocation("LICENSE", 3, 3))
                                )
                            )
                        )
                    )
                )

                val resolver =
                    createResolver(data, copyrightGarbage = setOf("(c) 2009-2010 Holder 1", "(c) 2009 Holder 1"))

                val result = resolver.resolveLicenseInfo(pkgId)

                result should containCopyrightsMarkedAsGarbage(
                    "(c) 2009-2010 Holder 1" to true,
                    "(c) 2010 Holder 2" to false
                )
                result should containCopyrightFindingsMarkedAsGarbage(
                    "(c) 2009 Holder 1" to true,
                    "(c) 2010 Holder 1" to false,
                    "(c) 2010 Holder 2" to false
                )
            }

            "apply path excludes" {
                val sourceArtifact = RemoteArtifact(
                    url = "http://example.com",
                    hash = Hash("", HashAlgorithm.NONE)
                )
                val sourceArtifactProvenance = Provenance(
                    sourceArtifact = sourceArtifact
                )

                val data = listOf(
                    createLicenseInfo(
                        id = pkgId,
                        detectedLicenses = listOf(
                            Findings(
                                provenance = provenance,
                                licenses = mapOf(
                                    "Apache-2.0" to listOf(
                                        TextLocation("LICENSE", 1, 1),
                                        TextLocation("a/b", 1, 1)
                                    )
                                ).toFindingsSet(),
                                copyrights = setOf(
                                    CopyrightFinding("(c) 2010 Holder", TextLocation("LICENSE", 1, 1)),
                                    CopyrightFinding("(c) 2010 Holder", TextLocation("a/b", 1, 1))
                                )
                            ),
                            Findings(
                                provenance = sourceArtifactProvenance,
                                licenses = mapOf(
                                    "Apache-2.0" to listOf(
                                        TextLocation("LICENSE", 1, 1),
                                        TextLocation("a/b", 1, 1)
                                    )
                                ).toFindingsSet(),
                                copyrights = setOf(
                                    CopyrightFinding("(c) 2010 Holder", TextLocation("LICENSE", 1, 1)),
                                    CopyrightFinding("(c) 2010 Holder", TextLocation("a/b", 1, 1))
                                )
                            )
                        )
                    )
                )

                val sourceArtifactPathExclude = PathExclude(
                    pattern = "LICENSE",
                    reason = PathExcludeReason.OTHER
                )
                val vcsPathExclude = PathExclude(
                    pattern = "a/b",
                    reason = PathExcludeReason.OTHER
                )

                val packageConfigs = listOf(
                    PackageConfiguration(
                        id = pkgId,
                        sourceArtifactUrl = sourceArtifact.url,
                        pathExcludes = listOf(sourceArtifactPathExclude)
                    ),
                    PackageConfiguration(
                        id = pkgId,
                        vcs = VcsMatcher(vcsInfo.type, vcsInfo.url, vcsInfo.revision),
                        pathExcludes = listOf(vcsPathExclude)
                    )
                )

                val resolver = createResolver(data, packageConfigs = packageConfigs)

                val result = resolver.resolveLicenseInfo(pkgId)

                result.pathExcludesForLicense(
                    "Apache-2.0", provenance, TextLocation("LICENSE", 1, 1)
                ) should beEmpty()
                result.pathExcludesForLicense(
                    "Apache-2.0", provenance, TextLocation("a/b", 1, 1)
                ) should containExactly(vcsPathExclude)
                result.pathExcludesForLicense(
                    "Apache-2.0", sourceArtifactProvenance, TextLocation("LICENSE", 1, 1)
                ) should containExactly(sourceArtifactPathExclude)
                result.pathExcludesForLicense(
                    "Apache-2.0", sourceArtifactProvenance, TextLocation("a/b", 1, 1)
                ) should beEmpty()

                result.pathExcludesForCopyright(
                    "(c) 2010 Holder", provenance, TextLocation("LICENSE", 1, 1)
                ) should beEmpty()
                result.pathExcludesForCopyright(
                    "(c) 2010 Holder", provenance, TextLocation("a/b", 1, 1)
                ) should containExactly(vcsPathExclude)
                result.pathExcludesForCopyright(
                    "(c) 2010 Holder", sourceArtifactProvenance, TextLocation("LICENSE", 1, 1)
                ) should containExactly(sourceArtifactPathExclude)
                result.pathExcludesForCopyright(
                    "(c) 2010 Holder", sourceArtifactProvenance, TextLocation("a/b", 1, 1)
                ) should beEmpty()
            }

            "apply license finding curations" {
                val data = listOf(
                    createLicenseInfo(
                        id = pkgId,
                        detectedLicenses = listOf(
                            Findings(
                                provenance = provenance,
                                licenses = mapOf(
                                    "Apache-2.0" to listOf(
                                        TextLocation("LICENSE", 1, 1)
                                    )
                                ).toFindingsSet(),
                                copyrights = setOf(
                                    CopyrightFinding("(c) 2010 Holder 1", TextLocation("LICENSE", 1, 1))
                                )
                            )
                        )
                    )
                )

                val curation = LicenseFindingCuration(
                    path = "LICENSE",
                    detectedLicense = "Apache-2.0".toSpdx(),
                    concludedLicense = "MIT".toSpdx(),
                    reason = LicenseFindingCurationReason.INCORRECT
                )

                val packageConfigs = listOf(
                    PackageConfiguration(
                        id = pkgId,
                        vcs = VcsMatcher(vcsInfo.type, vcsInfo.url, vcsInfo.revision),
                        licenseFindingCurations = listOf(curation)
                    )
                )

                val resolver = createResolver(data, packageConfigs = packageConfigs)

                val result = resolver.resolveLicenseInfo(pkgId)

                result should containLicensesExactly("MIT")
                result should containLocationForLicense(
                    license = "MIT",
                    provenance = provenance,
                    location = TextLocation("LICENSE", 1, 1),
                    appliedCuration = curation,
                    copyrights = setOf(
                        ResolvedCopyright(
                            statement = "(c) 2010 Holder 1",
                            findings = setOf(
                                ResolvedCopyrightFinding(
                                    statement = "(c) 2010 Holder 1",
                                    location = TextLocation("LICENSE", 1, 1),
                                    matchingPathExcludes = emptyList(),
                                    isGarbage = false
                                )
                            ),
                            isGarbage = false
                        )
                    )
                )
            }
        }
    }

    private fun createResolver(
        data: List<LicenseInfo>,
        packageConfigs: List<PackageConfiguration> = emptyList(),
        copyrightGarbage: Set<String> = emptySet()
    ) = LicenseInfoResolver(
        data.toProvider(),
        SimplePackageConfigurationProvider(packageConfigs),
        CopyrightGarbage(copyrightGarbage.toSortedSet())
    )

    private fun createLicenseInfo(
        id: Identifier,
        declaredLicenses: Set<String> = emptySet(),
        detectedLicenses: List<Findings> = emptyList(),
        concludedLicense: SpdxExpression? = null
    ) =
        LicenseInfo(
            id = id,
            declaredLicenseInfo = DeclaredLicenseInfo(
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

private class SimpleLicenseInfoProvider(val licenseInfo: Map<Identifier, LicenseInfo>) : LicenseInfoProvider {
    override fun get(id: Identifier) =
        licenseInfo[id] ?: throw IllegalArgumentException("No license info for '${id.toCoordinates()}' available.")
}

private fun List<LicenseInfo>.toProvider() = SimpleLicenseInfoProvider(associateBy { it.id })

private fun Map<String, List<TextLocation>>.toFindingsSet(): Set<LicenseFinding> =
    flatMap { (license, locations) ->
        locations.map { LicenseFinding(license, it) }
    }.toSet()

fun containNoLicenseLocations(): Matcher<ResolvedLicenseInfo?> =
    neverNullMatcher { value ->
        val locations = value.flatMap { it.locations }

        MatcherResult(
            locations.isEmpty(),
            "ResolvedLicenseInfo should not contain license locations, but has ${locations.show().value}",
            "ResolvedLicenseInfo should contain license locations, but has none"
        )
    }

fun containNoCopyrights(): Matcher<ResolvedLicenseInfo?> =
    neverNullMatcher { value ->
        val copyrights = value.flatMap { license -> license.locations.flatMap { it.copyrights } }

        MatcherResult(
            copyrights.isEmpty(),
            "ResolvedLicenseInfo should not contain copyrights, but has ${copyrights.show().value}",
            "ResolvedLicenseInfo should contain copyrights, but has none"
        )
    }

fun containCopyrightsExactly(vararg copyrights: String): Matcher<ResolvedLicenseInfo?> =
    neverNullMatcher { value ->
        val expected = copyrights.toSet()
        val actual = value.flatMapTo(mutableSetOf()) { license ->
            license.locations.flatMap { it.copyrights.map { it.statement } }
        }

        MatcherResult(
            expected == actual,
            "Resolved license info should contain exactly copyrights ${expected.show().value}, but has " +
                    actual.show().value,
            "Resolved license info should not contain exactly copyrights ${copyrights.show().value}"
        )
    }

fun containFindingsForCopyrightExactly(
    copyright: String,
    vararg findings: Pair<String, TextLocation>
): Matcher<ResolvedLicenseInfo?> =
    neverNullMatcher { value ->
        val expected = findings.toSet()
        val actual = value.flatMap { license ->
            license.locations.flatMap { it.copyrights }
        }.find { it.statement == copyright }?.findings.orEmpty().map { Pair(it.statement, it.location) }.toSet()

        MatcherResult(
            expected == actual,
            "Resolved license info should contain exactly findings ${expected.show().value} for copyright " +
                    "$copyright, but has ${actual.show().value}",
            "Resolved license info should not contain exactly findings ${expected.show().value} for copyright " +
                    copyright
        )
    }

fun containCopyrightsMarkedAsGarbage(vararg copyrights: Pair<String, Boolean>): Matcher<ResolvedLicenseInfo?> =
    neverNullMatcher { value ->
        val expected = copyrights.toSet()
        val actual = value.flatMap { license ->
            license.locations.flatMap { location -> location.copyrights.map { Pair(it.statement, it.isGarbage) } }
        }.toSet()

        MatcherResult(
            expected == actual,
            "Resolved license info should contain copyrights marked as garbage ${expected.show().value}, but has " +
                    actual.show().value,
            "Resolve license info should not contain copyrights marked as garbage ${expected.show().value}"
        )
    }

fun containCopyrightFindingsMarkedAsGarbage(vararg copyrights: Pair<String, Boolean>): Matcher<ResolvedLicenseInfo?> =
    neverNullMatcher { value ->
        val expected = copyrights.toSet()
        val actual = value.flatMap { license ->
            license.locations.flatMap { location ->
                location.copyrights.flatMap { copyright ->
                    copyright.findings.map { Pair(it.statement, it.isGarbage) }
                }
            }
        }.toSet()

        MatcherResult(
            expected == actual,
            "Resolved license info should contain copyright findings marked as garbage ${expected.show().value}, but " +
                    "has ${actual.show().value}",
            "Resolve license info should not contain copyright findings marked as garbage ${expected.show().value}"
        )
    }

fun containOnlyLicenseSources(vararg licenseSources: LicenseSource): Matcher<ResolvedLicenseInfo?> =
    neverNullMatcher { value ->
        val expected = licenseSources.toSet()
        val actual = value.flatMap { it.sources }.toSet()

        MatcherResult(
            expected == actual,
            "ResolvedLicenseInfo should contain only license sources ${expected.show().value}, but has " +
                    actual.show().value,
            "ResolvedLicenseInfo should not only contain license source ${expected.show().value}"
        )
    }

fun containLicensesExactly(vararg licenses: String): Matcher<ResolvedLicenseInfo?> =
    neverNullMatcher { value ->
        val expected = licenses.map { SpdxExpression.parse(it) as SpdxSingleLicenseExpression }.toSet()
        val actual = value.map { it.license }.toSet()

        MatcherResult(
            expected == actual,
            "ResolvedLicenseInfo should contain exactly licenses ${expected.show().value}, but has " +
                    actual.show().value,
            "ResolvedLicenseInfo should not contain exactly ${expected.show().value}"
        )
    }

fun containNumberOfLocationsForLicense(license: String, count: Int): Matcher<ResolvedLicenseInfo?> =
    neverNullMatcher { value ->
        val actualCount = value[SpdxSingleLicenseExpression.parse(license)]?.locations?.size ?: 0

        MatcherResult(
            count == actualCount,
            "ResolvedLicenseInfo should contain $count locations for $license, but has $actualCount",
            "ResolvedLicenseInfo should not contain $count locations for $license"
        )
    }

fun containLocationForLicense(
    license: String,
    provenance: Provenance,
    location: TextLocation,
    appliedCuration: LicenseFindingCuration? = null,
    matchingPathExcludes: List<PathExclude> = emptyList(),
    copyrights: Set<ResolvedCopyright> = emptySet()
): Matcher<ResolvedLicenseInfo?> =
    neverNullMatcher { value ->
        val expectedLocation =
            ResolvedLicenseLocation(
                provenance,
                location,
                appliedCuration,
                matchingPathExcludes,
                copyrights
            )

        val locations = value[SpdxSingleLicenseExpression.parse(license)]?.locations.orEmpty()

        val contained = expectedLocation in locations

        MatcherResult(
            contained,
            "ResolvedLicenseInfo should contain location ${expectedLocation.show().value} for $license",
            "ResolvedLicenseInfo should not contain location ${expectedLocation.show().value} for $license"
        )
    }

fun ResolvedLicenseInfo.pathExcludesForLicense(license: String, provenance: Provenance, location: TextLocation) =
    find { it.license == SpdxSingleLicenseExpression.parse(license) }
        ?.locations
        ?.find { it.provenance == provenance && it.location == location }
        ?.matchingPathExcludes
        ?.toSet().orEmpty()

fun ResolvedLicenseInfo.pathExcludesForCopyright(copyright: String, provenance: Provenance, location: TextLocation) =
    flatMap { license -> license.locations.filter { it.provenance == provenance } }
        .flatMap { it.copyrights }
        .flatMap { it.findings }
        .find { it.statement == copyright && it.location == location }
        ?.matchingPathExcludes
        ?.toSet().orEmpty()
