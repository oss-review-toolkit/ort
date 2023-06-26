/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.evaluator

import java.net.URI
import java.time.Instant

import org.ossreviewtoolkit.model.AdvisorCapability
import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorRecord
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorRun
import org.ossreviewtoolkit.model.AdvisorSummary
import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.Vulnerability
import org.ossreviewtoolkit.model.VulnerabilityReference
import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.LicenseChoices
import org.ossreviewtoolkit.model.config.PackageLicenseChoice
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.PathExcludeReason
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.utils.common.enumSetOf
import org.ossreviewtoolkit.utils.ort.DeclaredLicenseProcessor
import org.ossreviewtoolkit.utils.ort.Environment
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.model.SpdxLicenseChoice
import org.ossreviewtoolkit.utils.spdx.toSpdx
import org.ossreviewtoolkit.utils.test.scannerRunOf

val concludedLicense = "LicenseRef-a OR LicenseRef-b OR LicenseRef-c or LicenseRef-d".toSpdx()
val declaredLicenses = setOf("Apache-2.0", "MIT")
val declaredLicensesProcessed = DeclaredLicenseProcessor.process(declaredLicenses)

val packageExcluded = Package.EMPTY.copy(
    id = Identifier("Maven:org.ossreviewtoolkit:package-excluded:1.0")
)

val packageDynamicallyLinked = Package.EMPTY.copy(
    id = Identifier("Maven:org.ossreviewtoolkit:package-dynamically-linked:1.0")
)

val packageStaticallyLinked = Package.EMPTY.copy(
    id = Identifier("Maven:org.ossreviewtoolkit:package-statically-linked:1.0")
)

val packageWithoutLicense = Package.EMPTY.copy(
    id = Identifier("Maven:org.ossreviewtoolkit:package-without-license:1.0")
)

val packageWithNotPresentLicense = Package.EMPTY.copy(
    id = Identifier("Maven:org.ossreviewtoolkit:package-with-not-present-license:1.0"),
    concludedLicense = "${SpdxConstants.NONE} OR ${SpdxConstants.NOASSERTION}".toSpdx()
)

val packageWithOnlyConcludedLicense = Package.EMPTY.copy(
    id = Identifier("Maven:org.ossreviewtoolkit:package-with-only-concluded-license:1.0"),
    concludedLicense = concludedLicense
)

val packageWithOnlyDeclaredLicense = Package.EMPTY.copy(
    id = Identifier("Maven:org.ossreviewtoolkit:package-with-only-declared-license:1.0"),
    declaredLicenses = declaredLicenses,
    declaredLicensesProcessed = declaredLicensesProcessed
)

val packageWithOnlyDetectedLicense = Package.EMPTY.copy(
    id = Identifier("Maven:org.ossreviewtoolkit:package-with-only-detected-license:1.0")
    // Detected license for this package is added in the ortResult.
)

val packageWithConcludedAndDeclaredLicense = Package.EMPTY.copy(
    id = Identifier("Maven:org.ossreviewtoolkit:package-with-concluded-and-declared-license:1.0"),
    concludedLicense = concludedLicense,
    declaredLicenses = declaredLicenses,
    declaredLicensesProcessed = declaredLicensesProcessed
)

val packageWithVulnerabilities = Package.EMPTY.copy(
    id = Identifier("Maven:org.ossreviewtoolkit:package-with-vulnerabilities:1.0")
)

val packageMetadataOnly = Package.EMPTY.copy(
    id = Identifier("Maven:org.ossreviewtoolkit:package-metadata-only:1.0"),
    isMetadataOnly = true
)

val packageDependency = Package.EMPTY.copy(
    id = Identifier("Maven:org.ossreviewtoolkit:common-lib:1.0"),
    declaredLicenses = declaredLicenses
)

val allPackages = setOf(
    packageExcluded,
    packageDynamicallyLinked,
    packageStaticallyLinked,
    packageWithoutLicense,
    packageWithNotPresentLicense,
    packageWithOnlyConcludedLicense,
    packageWithOnlyDeclaredLicense,
    packageWithConcludedAndDeclaredLicense,
    packageMetadataOnly,
    packageDependency
)

val scopeExcluded = Scope(
    name = "compile",
    dependencies = setOf(
        packageExcluded.toReference()
    )
)

val projectExcluded = Project.EMPTY.copy(
    id = Identifier("Maven:org.ossreviewtoolkit:project-excluded:1.0"),
    definitionFilePath = "excluded/pom.xml",
    scopeDependencies = setOf(scopeExcluded)
)

val packageRefDynamicallyLinked = packageDynamicallyLinked.toReference(PackageLinkage.DYNAMIC)
val packageRefStaticallyLinked = packageStaticallyLinked.toReference(PackageLinkage.STATIC)

val scopeIncluded = Scope(
    name = "compile",
    dependencies = setOf(
        packageWithoutLicense.toReference(),
        packageWithNotPresentLicense.toReference(),
        packageWithOnlyConcludedLicense.toReference(),
        packageWithOnlyDeclaredLicense.toReference(dependencies = setOf(packageDependency.toReference())),
        packageWithConcludedAndDeclaredLicense.toReference(),
        packageRefDynamicallyLinked,
        packageRefStaticallyLinked,
        packageMetadataOnly.toReference()
    )
)

val projectIncluded = Project.EMPTY.copy(
    id = Identifier("Maven:org.ossreviewtoolkit:project-included:1.0"),
    definitionFilePath = "included/pom.xml",
    scopeDependencies = setOf(scopeIncluded)
)

val allProjects = setOf(
    projectExcluded,
    projectIncluded
)

val ortResult = OrtResult(
    repository = Repository(
        vcs = VcsInfo.EMPTY,
        config = RepositoryConfiguration(
            excludes = Excludes(
                paths = listOf(
                    PathExclude(
                        pattern = "excluded/**",
                        reason = PathExcludeReason.TEST_OF,
                        comment = "excluded"
                    )
                )
            ),
            licenseChoices = LicenseChoices(
                repositoryLicenseChoices = listOf(
                    // This license choice will not be applied to "only-concluded-license" since the package license
                    // choice takes precedence.
                    SpdxLicenseChoice("LicenseRef-a OR LicenseRef-b".toSpdx(), "LicenseRef-b".toSpdx()),
                    SpdxLicenseChoice("LicenseRef-c OR LicenseRef-d".toSpdx(), "LicenseRef-d".toSpdx())
                ),
                packageLicenseChoices = listOf(
                    PackageLicenseChoice(
                        packageId = Identifier("Maven:org.ossreviewtoolkit:package-with-only-concluded-license:1.0"),
                        licenseChoices = listOf(
                            SpdxLicenseChoice("LicenseRef-a OR LicenseRef-b".toSpdx(), "LicenseRef-a".toSpdx())
                        )
                    )
                )
            )
        )
    ),
    analyzer = AnalyzerRun.EMPTY.copy(
        config = AnalyzerConfiguration(allowDynamicVersions = true),
        result = AnalyzerResult(
            projects = allProjects,
            packages = allPackages
        )
    ),
    advisor = AdvisorRun(
        startTime = Instant.EPOCH,
        endTime = Instant.EPOCH,
        environment = Environment(),
        config = AdvisorConfiguration(),
        results = AdvisorRecord(
            advisorResults = mapOf(
                packageWithVulnerabilities.id to listOf(
                    AdvisorResult(
                        advisor = AdvisorDetails("Advisor", enumSetOf(AdvisorCapability.VULNERABILITIES)),
                        summary = AdvisorSummary(startTime = Instant.EPOCH, endTime = Instant.EPOCH),
                        vulnerabilities = listOf(
                            Vulnerability(
                                id = "CVE-2021-critical",
                                references = listOf(
                                    VulnerabilityReference(
                                        url = URI("https://oss-review-toolkit.org"),
                                        scoringSystem = "CVSS3",
                                        severity = "9.0"
                                    )
                                )
                            ),
                            Vulnerability(
                                id = "CVE-2021-trivial",
                                references = listOf(
                                    VulnerabilityReference(
                                        url = URI("https://oss-review-toolkit.org"),
                                        scoringSystem = "CVSS3",
                                        severity = "2.0"
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
    ),
    scanner = scannerRunOf(
        Identifier("Maven:org.ossreviewtoolkit:package-with-only-detected-license:1.0") to listOf(
            ScanResult(
                provenance = UnknownProvenance,
                scanner = ScannerDetails.EMPTY,
                summary = ScanSummary.EMPTY.copy(
                    licenseFindings = setOf(
                        LicenseFinding("LicenseRef-a", TextLocation("LICENSE", 1)),
                        LicenseFinding("LicenseRef-b", TextLocation("LICENSE", 2))
                    )
                )
            )
        )
    ),
    labels = mapOf(
        "label" to "value",
        "list" to "value1, value2, value3"
    )
)
