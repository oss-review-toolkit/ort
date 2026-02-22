/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.yoctospdx

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import org.ossreviewtoolkit.analyzer.resolveSingleProject
import org.ossreviewtoolkit.utils.test.getAssetFile

class YoctoSpdxFunTest : WordSpec({
    "resolveDependencies()" should {
        "parse Yocto SPDX 3 file and extract packages" {
            val definitionFile = getAssetFile(
                "projects/synthetic/minimal/core-image-minimal-qemux86-64.rootfs-20251202091030.spdx.json"
            )

            val result = YoctoSpdxFactory.create().resolveSingleProject(definitionFile)

            // Verify project is created with correct name
            result.project.id.name shouldContain "core-image-minimal"

            // Verify scopes are created
            result.project.scopes.shouldNotBeEmpty()

            // Verify packages are extracted
            result.packages.shouldNotBeEmpty()

            // Verify we have packages in different scopes
            val scopeNames = result.project.scopes.map { it.name }
            scopeNames shouldContain "install"
        }

        "extract package metadata correctly" {
            val definitionFile = getAssetFile(
                "projects/synthetic/minimal/core-image-minimal-qemux86-64.rootfs-20251202091030.spdx.json"
            )

            val result = YoctoSpdxFactory.create().resolveSingleProject(definitionFile)

            // Find base-files package which should have version 3.0.14 based on the SPDX file
            val baseFilesPackage = result.packages.find { it.id.name == "base-files" }
            baseFilesPackage.shouldNotBeNull()
            baseFilesPackage.id.version shouldBe "3.0.14"

            // Verify CPE is extracted
            baseFilesPackage.cpe.shouldNotBeNull()
            baseFilesPackage.cpe shouldContain "cpe:2.3"
        }

        "extract source packages with download locations" {
            val definitionFile = getAssetFile(
                "projects/synthetic/minimal/core-image-minimal-qemux86-64.rootfs-20251202091030.spdx.json"
            )

            val result = YoctoSpdxFactory.create().resolveSingleProject(definitionFile)

            // Find a source package with download location
            val sourcePackagesWithDownload = result.packages.filter {
                it.sourceArtifact.url.isNotBlank()
            }

            sourcePackagesWithDownload.shouldNotBeEmpty()

            // Verify at least one package has a hash
            val packagesWithHash = sourcePackagesWithDownload.filter {
                it.sourceArtifact.hash.value.isNotBlank()
            }
            packagesWithHash.shouldNotBeEmpty()
        }
    }
})
