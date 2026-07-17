/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.python.utils

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

import java.io.File

import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.utils.common.div

class PythonInspectorExtensionsTest : WordSpec({
    "toOrtPackages()" should {
        "use a PEP 639 license expression if legacy license metadata is empty" {
            val pkg = createPackage(
                licenseExpression = "Apache-2.0 OR BSD-2-Clause",
                declaredLicense = PythonInspector.DeclaredLicense()
            ).toOrtPackage()

            pkg.declaredLicenses should containExactly("Apache-2.0 OR BSD-2-Clause")
            pkg.declaredLicensesProcessed.spdxExpression.toString() shouldBe "Apache-2.0 OR BSD-2-Clause"
        }

        "prefer legacy license metadata if it is usable" {
            val pkg = createPackage(
                licenseExpression = "MIT",
                declaredLicense = PythonInspector.DeclaredLicense(license = "BSD-3-Clause")
            ).toOrtPackage()

            pkg.declaredLicenses should containExactly("BSD-3-Clause")
            pkg.declaredLicensesProcessed.spdxExpression.toString() shouldBe "BSD-3-Clause"
        }

        "use a PEP 639 license expression if legacy metadata is UNKNOWN" {
            val pkg = createPackage(
                licenseExpression = "MIT",
                declaredLicense = PythonInspector.DeclaredLicense(license = "UNKNOWN")
            ).toOrtPackage()

            pkg.declaredLicenses should containExactly("MIT")
            pkg.declaredLicensesProcessed.spdxExpression.toString() shouldBe "MIT"
        }

        "preserve unmappable legacy metadata instead of replacing it" {
            val pkg = createPackage(
                licenseExpression = "MIT",
                declaredLicense = PythonInspector.DeclaredLicense(license = "custom legacy terms")
            ).toOrtPackage()

            pkg.declaredLicenses should containExactly("custom legacy terms")
            pkg.declaredLicensesProcessed.spdxExpression should beNull()
            pkg.declaredLicensesProcessed.unmapped should containExactly("custom legacy terms")
        }

        "retain a malformed PEP 639 expression as unmapped metadata" {
            val pkg = createPackage(
                licenseExpression = "not a valid SPDX expression",
                declaredLicense = null
            ).toOrtPackage()

            pkg.declaredLicenses should containExactly("not a valid SPDX expression")
            pkg.declaredLicensesProcessed.spdxExpression should beNull()
            pkg.declaredLicensesProcessed.unmapped should containExactly("not a valid SPDX expression")
        }

        "ignore a blank PEP 639 license expression" {
            val pkg = createPackage(
                licenseExpression = "  ",
                declaredLicense = null
            ).toOrtPackage()

            pkg.declaredLicenses should beEmpty()
            pkg.declaredLicensesProcessed.spdxExpression should beNull()
        }
    }

    "ProjectResults.deduplicate()" should {
        "return the same result if there are no duplicates" {
            val resultMap = mapOf(
                File("foo") to createResult("foo"),
                File("bar") to createResult("bar")
            )

            resultMap.deduplicate() shouldBeSameInstanceAs resultMap
        }

        "return a deduplicated result" {
            val analysisRoot = tempdir()
            val definitionFile1 = analysisRoot / "libraries" / "requirements.txt"
            val definitionFile2 = analysisRoot / "tools" / "requirements.txt"
            val definitionFile3 = analysisRoot / "app" / "libraries" / "requirements.txt"
            val definitionFile4 = analysisRoot / "app" / "tools" / "requirements.txt"
            val definitionFile5 = analysisRoot / "test" / "requirements.txt"

            val resultMap = mapOf(
                definitionFile1 to createResult("libraries"),
                definitionFile2 to createResult("tools"),
                definitionFile3 to createResult("libraries"),
                definitionFile4 to createResult("tools"),
                definitionFile5 to createResult("test")
            )

            val deduplicatedResults = resultMap.deduplicate()

            deduplicatedResults[definitionFile1] shouldContainOnly createResult("libraries/requirements.txt")
            deduplicatedResults[definitionFile2] shouldContainOnly createResult("tools/requirements.txt")
            deduplicatedResults[definitionFile3] shouldContainOnly createResult("app/libraries/requirements.txt")
            deduplicatedResults[definitionFile4] shouldContainOnly createResult("app/tools/requirements.txt")
            deduplicatedResults[definitionFile5] shouldContainOnly createResult("test")

            deduplicatedResults.deduplicate() shouldBeSameInstanceAs deduplicatedResults
        }
    }
})

private fun createPackage(licenseExpression: String?, declaredLicense: PythonInspector.DeclaredLicense?) =
    PythonInspector.Package(
        type = "pypi",
        namespace = null,
        name = "example",
        version = "1.0.0",
        description = "Example package",
        parties = emptyList(),
        homepageUrl = null,
        downloadUrl = "https://example.org/example-1.0.0.tar.gz",
        size = 1,
        sha1 = null,
        md5 = null,
        sha256 = null,
        sha512 = null,
        codeViewUrl = null,
        vcsUrl = null,
        copyright = null,
        licenseExpression = licenseExpression,
        declaredLicense = declaredLicense,
        sourcePackages = emptyList(),
        repositoryHomepageUrl = null,
        repositoryDownloadUrl = null,
        apiDataUrl = "https://pypi.org/pypi/example/1.0.0/json",
        purl = "pkg:pypi/example@1.0.0"
    )

private fun PythonInspector.Package.toOrtPackage() = listOf(this).toOrtPackages().single()

/**
 * Return a list with a single [ProjectAnalyzerResult] with a project whose identifier has the given [name] component.
 */
private fun createResult(name: String): List<ProjectAnalyzerResult> =
    listOf(
        ProjectAnalyzerResult(
            project = Project.EMPTY.copy(id = Project.EMPTY.id.copy(name = name)),
            packages = emptySet()
        )
    )
