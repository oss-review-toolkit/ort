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

package org.ossreviewtoolkit.plugins.packagecurationproviders.namespaced

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.should

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.PackageCurationData

class NamespacedPackageCurationProviderTest : StringSpec({
    val id = Identifier("Maven:org.third_party:testpackage:1.0.0")
    val pkg = Package.EMPTY.copy(id = id)

    val emptyNamespaceId = Identifier("Maven::testpackage:1.0.0")
    val emptyNamespacePkg = Package.EMPTY.copy(id = emptyNamespaceId)

    val prefixId = Identifier("Maven:org.third_party:testpackage-core:1.0.0")
    val prefixPkg = Package.EMPTY.copy(id = prefixId)

    val minorVersionId = Identifier("Maven:org.third_party:testpackage:1.1.0")
    val minorVersionPkg = Package.EMPTY.copy(id = minorVersionId)

    val npmId = Identifier("NPM:react:react:18.2.0")
    val npmPkg = Package.EMPTY.copy(id = npmId)

    val nugetId = Identifier("NuGet:Newtonsoft.Json:13.0.1")
    val nugetPkg = Package.EMPTY.copy(id = nugetId)

    val pkgs = setOf(pkg, prefixPkg, minorVersionPkg, npmPkg, nugetPkg)

    val expectedCuration = PackageCuration(
        id = id,
        data = PackageCurationData(
            sourceCodeOrigins = emptyList(),
            comment = "Third party proprietary closed-sourced package for which no sources are available."
        )
    )

    fun provider(pattern: String) =
        NamespacedPackageCurationProvider(
            config = NamespacedPackageCurationProviderConfig(
                curations = """
                - namespace: "$pattern"
                  curations:
                      source_code_origins: []
                      comment: |
                        Third party proprietary closed-sourced package for which no sources are available."""
                    .trimIndent()
            )
        )

    "Match the exact coordinate" {
        provider("Maven:org.third_party:testpackage:1.0.0")
            .getCurationsFor(pkgs) should containExactly(expectedCuration)
    }

    "Match packages with a specific name, ignoring namespace and version" {
        val expectedCurationForMinorVersion = expectedCuration.copy(id = minorVersionId)

        provider("Maven:*:testpackage:*")
            .getCurationsFor(pkgs) should containExactly(expectedCuration, expectedCurationForMinorVersion)
    }

    "Match a package with an empty namespace via a double colon" {
        val expectedCuration = PackageCuration(
            id = emptyNamespaceId,
            data = PackageCurationData(
                sourceCodeOrigins = emptyList(),
                comment = "Third party proprietary closed-sourced package for which no sources are available."
            )
        )

        provider("Maven::testpackage:1.0.0")
            .getCurationsFor(setOf(emptyNamespacePkg, pkg, prefixPkg, minorVersionPkg, npmPkg, nugetPkg)) should
            containExactly(expectedCuration)
    }

    "Match packages with a name prefix" {
        val expectedCurationForPrefix = expectedCuration.copy(id = prefixId)
        val expectedCurationForMinorVersion = expectedCuration.copy(id = minorVersionId)

        provider("Maven:org.third_party:testpackage*:*")
            .getCurationsFor(pkgs) should containExactly(
            expectedCuration,
            expectedCurationForPrefix,
            expectedCurationForMinorVersion
        )
    }

    "Match all releases of a specific major version" {
        val expectedCurationForMinorVersion = expectedCuration.copy(id = minorVersionId)

        provider("Maven:org.third_party:testpackage:1.*")
            .getCurationsFor(pkgs) should containExactly(expectedCuration, expectedCurationForMinorVersion)
    }

    "Apply only the first matching curation when multiple patterns match" {
        val provider = NamespacedPackageCurationProvider(
            config = NamespacedPackageCurationProviderConfig(
                curations = """
                    - namespace: "Maven:org.third_party:testpackage:1.0.0"
                      curations:
                        is_metadata_only: false
                        comment: "Second"
                    - namespace: "Maven:*:*:*"
                      curations:
                        is_metadata_only: true
                        comment: "First"
                    - namespace: "Maven:org.third_party:testpackage*:*"
                      curations:
                        is_metadata_only: true
                        comment: "First"
                    - namespace: "NPM:*:*:*"
                      curations:
                        is_metadata_only: true
                        comment: "First"
                    - namespace: "NuGet:*:*:*"
                      curations:
                        is_metadata_only: true
                        comment: "First"
                """.trimIndent()
            )
        )

        provider.getCurationsFor(pkgs).map { it.id } shouldContainExactly
            setOf(id, prefixId, minorVersionId, npmId, nugetId)
    }
})
