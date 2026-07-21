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
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

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
