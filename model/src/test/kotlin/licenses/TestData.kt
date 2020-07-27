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

import org.ossreviewtoolkit.model.AccessStatistics
import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.CuratedPackage
import org.ossreviewtoolkit.model.Environment
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseFindings
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.ScanRecord
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.PathExcludeReason
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.spdx.SpdxLicenseReferenceExpression
import org.ossreviewtoolkit.spdx.toSpdx
import org.ossreviewtoolkit.utils.DeclaredLicenseProcessor

val concludedLicense = "LicenseRef-a AND LicenseRef-b".toSpdx()
val declaredLicenses = sortedSetOf("Apache-2.0", "MIT")
val declaredLicensesProcessed = DeclaredLicenseProcessor.process(declaredLicenses)
val detectedLicenses = listOf(
    SpdxLicenseReferenceExpression("LicenseRef-a"),
    SpdxLicenseReferenceExpression("LicenseRef-b")
)

val licenseFindings = listOf(
    LicenseFindings(SpdxLicenseReferenceExpression("LicenseRef-a"), sortedSetOf(), sortedSetOf()),
    LicenseFindings(SpdxLicenseReferenceExpression("LicenseRef-b"), sortedSetOf(), sortedSetOf())
)

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

val packageWithOnlyConcludedLicense = Package.EMPTY.copy(
    id = Identifier("Maven:org.ossreviewtoolkit:package-with-only-concluded-license:1.0"),
    concludedLicense = concludedLicense
)

val packageWithOnlyDeclaredLicense = Package.EMPTY.copy(
    id = Identifier("Maven:org.ossreviewtoolkit:package-with-only-declared-license:1.0"),
    declaredLicenses = declaredLicenses,
    declaredLicensesProcessed = declaredLicensesProcessed
)

val packageWithConcludedAndDeclaredLicense = Package.EMPTY.copy(
    id = Identifier("Maven:org.ossreviewtoolkit:package-with-concluded-and-declared-license:1.0"),
    concludedLicense = concludedLicense,
    declaredLicenses = declaredLicenses,
    declaredLicensesProcessed = declaredLicensesProcessed
)

val allPackages = listOf(
    packageExcluded,
    packageDynamicallyLinked,
    packageStaticallyLinked,
    packageWithoutLicense,
    packageWithOnlyConcludedLicense,
    packageWithOnlyDeclaredLicense,
    packageWithConcludedAndDeclaredLicense
)

val scopeExcluded = Scope(
    name = "compile",
    dependencies = sortedSetOf(
        packageExcluded.toReference()
    )
)

val projectExcluded = Project.EMPTY.copy(
    id = Identifier("Maven:org.ossreviewtoolkit:project-excluded:1.0"),
    definitionFilePath = "excluded/pom.xml",
    scopes = sortedSetOf(scopeExcluded)
)

val packageRefDynamicallyLinked = packageDynamicallyLinked.toReference(PackageLinkage.DYNAMIC)
val packageRefStaticallyLinked = packageStaticallyLinked.toReference(PackageLinkage.STATIC)

val scopeIncluded = Scope(
    name = "compile",
    dependencies = sortedSetOf(
        packageWithoutLicense.toReference(),
        packageWithOnlyConcludedLicense.toReference(),
        packageWithOnlyDeclaredLicense.toReference(),
        packageWithConcludedAndDeclaredLicense.toReference(),
        packageRefDynamicallyLinked,
        packageRefStaticallyLinked
    )
)

val projectIncluded = Project.EMPTY.copy(
    id = Identifier("Maven:org.ossreviewtoolkit:project-included:1.0"),
    definitionFilePath = "included/pom.xml",
    scopes = sortedSetOf(scopeIncluded)
)

val allProjects = listOf(
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
                        reason = PathExcludeReason.TEST_TOOL_OF,
                        comment = "excluded"
                    )
                )
            )
        )
    ),
    analyzer = AnalyzerRun(
        environment = Environment(),
        config = AnalyzerConfiguration(ignoreToolVersions = true, allowDynamicVersions = true),
        result = AnalyzerResult(
            projects = sortedSetOf(
                projectExcluded,
                projectIncluded
            ),
            packages = allPackages.mapTo(sortedSetOf()) { CuratedPackage(it) }
        )
    ),
    scanner = ScannerRun(
        environment = Environment(),
        config = ScannerConfiguration(),
        results = ScanRecord(
            scanResults = sortedSetOf(),
            storageStats = AccessStatistics()
        )
    )
)
