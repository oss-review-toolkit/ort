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

package org.ossreviewtoolkit.model.licenses

import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.PathExcludeReason
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.utils.ort.DeclaredLicenseProcessor
import org.ossreviewtoolkit.utils.spdx.toSpdx

val authors = setOf("The Author", "The Other Author")
val projectAuthors = setOf("The Project Author")

val concludedLicense = "LicenseRef-a AND LicenseRef-b".toSpdx()
val declaredLicenses = setOf("LicenseRef-a", "LicenseRef-b")
val declaredLicensesProcessed = DeclaredLicenseProcessor.process(declaredLicenses)

val licenseFindings = sortedSetOf(
    LicenseFinding("LicenseRef-a", TextLocation("LICENSE", 1)),
    LicenseFinding("LicenseRef-b", TextLocation("LICENSE", 2))
)

val packageWithAuthors = Package.EMPTY.copy(
    id = Identifier("Maven:org.ossreviewtoolkit:package-with-authors:1.0"),
    authors = authors
)

val packageWithoutLicense = Package.EMPTY.copy(
    id = Identifier("Maven:org.ossreviewtoolkit:package-without-license:1.0")
)

val packageWithConcludedLicense = Package.EMPTY.copy(
    id = Identifier("Maven:org.ossreviewtoolkit:package-with-concluded-license:1.0"),
    concludedLicense = concludedLicense
)

val packageWithDeclaredLicense = Package.EMPTY.copy(
    id = Identifier("Maven:org.ossreviewtoolkit:package-with-declared-license:1.0"),
    declaredLicenses = declaredLicenses,
    declaredLicensesProcessed = declaredLicensesProcessed
)

val packageWithDetectedLicense = Package.EMPTY.copy(
    id = Identifier("Maven:org.ossreviewtoolkit:package-with-detected-license:1.0")
)

val packageWithConcludedAndDeclaredLicense = Package.EMPTY.copy(
    id = Identifier("Maven:org.ossreviewtoolkit:package-with-concluded-and-declared-license:1.0"),
    concludedLicense = concludedLicense,
    declaredLicenses = declaredLicenses,
    declaredLicensesProcessed = declaredLicensesProcessed
)

val packageWithConcludedAndDetectedLicense = Package.EMPTY.copy(
    id = Identifier("Maven:org.ossreviewtoolkit:package-with-concluded-and-detected-license:1.0"),
    concludedLicense = concludedLicense
)

val packageWithDeclaredAndDetectedLicense = Package.EMPTY.copy(
    id = Identifier("Maven:org.ossreviewtoolkit:package-with-declared-and-detected-license:1.0"),
    declaredLicenses = declaredLicenses,
    declaredLicensesProcessed = declaredLicensesProcessed
)

val packageWithConcludedAndDeclaredAndDetectedLicense = Package.EMPTY.copy(
    id = Identifier("Maven:org.ossreviewtoolkit:package-with-concluded-and-declared-and-detected-license:1.0"),
    concludedLicense = concludedLicense,
    declaredLicenses = declaredLicenses,
    declaredLicensesProcessed = declaredLicensesProcessed
)

val allPackages = setOf(
    packageWithAuthors,
    packageWithoutLicense,
    packageWithConcludedLicense,
    packageWithDeclaredLicense,
    packageWithDetectedLicense,
    packageWithConcludedAndDeclaredLicense,
    packageWithConcludedAndDetectedLicense,
    packageWithDeclaredAndDetectedLicense,
    packageWithConcludedAndDeclaredAndDetectedLicense
)

val scope = Scope(
    name = "compile",
    dependencies = sortedSetOf(
        packageWithoutLicense.toReference(),
        packageWithConcludedLicense.toReference(),
        packageWithDeclaredLicense.toReference(),
        packageWithConcludedAndDeclaredLicense.toReference()
    )
)

val project = Project.EMPTY.copy(
    id = Identifier("Maven:org.ossreviewtoolkit:project-included:1.0"),
    definitionFilePath = "included/pom.xml",
    authors = projectAuthors,
    scopeDependencies = sortedSetOf(scope)
)

val provenance = UnknownProvenance

val scanResults = listOf(
    packageWithDetectedLicense,
    packageWithConcludedAndDetectedLicense,
    packageWithDeclaredAndDetectedLicense,
    packageWithConcludedAndDeclaredAndDetectedLicense
).associateTo(sortedMapOf()) {
    it.id to listOf(
        ScanResult(
            provenance = provenance,
            scanner = ScannerDetails.EMPTY,
            summary = ScanSummary.EMPTY.copy(
                licenseFindings = licenseFindings,
            )
        )
    )
}

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
            )
        )
    ),
    analyzer = AnalyzerRun.EMPTY.copy(
        result = AnalyzerResult(
            projects = setOf(project),
            packages = allPackages
        )
    ),
    scanner = ScannerRun.EMPTY.copy(
        scanResults = scanResults
    )
)
