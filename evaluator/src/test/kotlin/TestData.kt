/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package com.here.ort.evaluator

import com.here.ort.model.AccessStatistics
import com.here.ort.model.AnalyzerResult
import com.here.ort.model.AnalyzerRun
import com.here.ort.model.CuratedPackage
import com.here.ort.model.Environment
import com.here.ort.model.Identifier
import com.here.ort.model.LicenseFindings
import com.here.ort.model.OrtResult
import com.here.ort.model.Package
import com.here.ort.model.PackageLinkage
import com.here.ort.model.Project
import com.here.ort.model.Repository
import com.here.ort.model.ScanRecord
import com.here.ort.model.ScannerRun
import com.here.ort.model.Scope
import com.here.ort.model.VcsInfo
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.Excludes
import com.here.ort.model.config.PathExclude
import com.here.ort.model.config.PathExcludeReason
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.model.config.ScannerConfiguration
import com.here.ort.spdx.SpdxExpression
import com.here.ort.utils.DeclaredLicenseProcessor

val concludedLicense = SpdxExpression.parse("LicenseRef-a AND LicenseRef-b")
val declaredLicenses = sortedSetOf("license-a", "license-b")
val declaredLicensesProcessed = DeclaredLicenseProcessor.process(declaredLicenses)
val detectedLicenses = listOf("LicenseRef-a", "LicenseRef-b")

val declaredSpdxLicenses = sortedSetOf("Apache-2.0")
val declaredSpdxLicensesProcessed = DeclaredLicenseProcessor.process(declaredSpdxLicenses)

val licenseFindings = listOf(
    LicenseFindings("LicenseRef-a", sortedSetOf(), sortedSetOf()),
    LicenseFindings("LicenseRef-b", sortedSetOf(), sortedSetOf())
)

val packageExcluded = Package.EMPTY.copy(
    id = Identifier("Maven:com.here:package-excluded:1.0")
)

val packageDynamicallyLinked = Package.EMPTY.copy(
    id = Identifier("Maven:com.here:package-dynamically-linked:1.0")
)

val packageStaticallyLinked = Package.EMPTY.copy(
    id = Identifier("Maven:com.here:package-statically-linked:1.0")
)

val packageWithoutLicense = Package.EMPTY.copy(
    id = Identifier("Maven:com.here:package-without-license:1.0")
)

val packageWithOnlyConcludedLicense = Package.EMPTY.copy(
    id = Identifier("Maven:com.here:package-with-only-concluded-license:1.0"),
    concludedLicense = concludedLicense
)

val packageWithOnlyDeclaredLicense = Package.EMPTY.copy(
    id = Identifier("Maven:com.here:package-with-only-declared-license:1.0"),
    declaredLicenses = declaredLicenses,
    declaredLicensesProcessed = declaredLicensesProcessed
)

val packageWithConcludedAndDeclaredLicense = Package.EMPTY.copy(
    id = Identifier("Maven:com.here:package-with-concluded-and-declared-license:1.0"),
    concludedLicense = concludedLicense,
    declaredLicenses = declaredLicenses,
    declaredLicensesProcessed = declaredLicensesProcessed
)

val packageWithSpdxLicense = Package.EMPTY.copy(
    id = Identifier("Maven:com.here:package-with-spdx-licenses:1.0"),
    declaredLicenses = declaredSpdxLicenses,
    declaredLicensesProcessed = declaredSpdxLicensesProcessed
)

val allPackages = listOf(
    packageExcluded,
    packageDynamicallyLinked,
    packageStaticallyLinked,
    packageWithoutLicense,
    packageWithOnlyConcludedLicense,
    packageWithOnlyDeclaredLicense,
    packageWithConcludedAndDeclaredLicense,
    packageWithSpdxLicense
)

val scopeExcluded = Scope(
    name = "compile",
    dependencies = sortedSetOf(
        packageExcluded.toReference()
    )
)

val projectExcluded = Project.EMPTY.copy(
    id = Identifier("Maven:com.here:project-excluded:1.0"),
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
        packageRefStaticallyLinked,
        packageWithSpdxLicense.toReference()
    )
)

val projectIncluded = Project.EMPTY.copy(
    id = Identifier("Maven:com.here:project-included:1.0"),
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
            packages = allPackages.mapTo(sortedSetOf()) { CuratedPackage(it, emptyList()) }
        )
    ),
    scanner = ScannerRun(
        environment = Environment(),
        config = ScannerConfiguration(),
        results = ScanRecord(
            scannedScopes = sortedSetOf(),
            scanResults = sortedSetOf(),
            storageStats = AccessStatistics()
        )
    )
)
