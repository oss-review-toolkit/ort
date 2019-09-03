/*
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

package com.here.ort.reporter.reporters

import com.here.ort.model.OrtResult
import com.here.ort.model.config.CopyrightGarbage
import com.here.ort.reporter.LicenseTextProvider
import com.here.ort.reporter.Reporter
import com.here.ort.reporter.ResolutionProvider
import com.here.ort.spdx.SpdxLicense

import java.io.OutputStream
import java.util.UUID

import org.cyclonedx.BomGeneratorFactory
import org.cyclonedx.CycloneDxSchema
import org.cyclonedx.model.Bom
import org.cyclonedx.model.Component
import org.cyclonedx.model.Hash
import org.cyclonedx.model.License
import org.cyclonedx.model.LicenseChoice
import org.cyclonedx.model.LicenseText

class CycloneDxReporter : Reporter() {
    override val reporterName = "CycloneDx"
    override val defaultFilename = "bom.xml"

    private fun mapHash(hash: com.here.ort.model.Hash): Hash? =
        enumValues<Hash.Algorithm>().find { it.spec == hash.algorithm.toString() }?.let { Hash(it, hash.value) }

    override fun generateReport(
        outputStream: OutputStream,
        ortResult: OrtResult,
        resolutionProvider: ResolutionProvider,
        licenseTextProvider: LicenseTextProvider,
        copyrightGarbage: CopyrightGarbage,
        postProcessingScript: String?
    ) {
        val bom = Bom().apply { serialNumber = "urn:uuid:${UUID.randomUUID()}" }

        ortResult.getPackages().forEach { (pkg, _) ->
            // TODO: We should actually use the concluded license expression here, but we first need a workflow to
            //       ensure it is being set.
            val licenseNames = ortResult.getDetectedLicensesForId(pkg.id) + pkg.declaredLicensesProcessed.allLicenses

            val licenseObjects = licenseNames.map { licenseName ->
                val spdxId = SpdxLicense.forId(licenseName)?.id

                // Prefer to set the id in case of an SPDX "core" license and only use the name as a fallback, also
                // see https://github.com/CycloneDX/cyclonedx-core-java/issues/8.
                License().apply {
                    id = spdxId
                    name = licenseName.takeIf { spdxId == null }
                    licenseText = LicenseText().apply {
                        contentType = "plain/text"
                        encoding = "UTF-8"
                        text = licenseTextProvider.getLicenseText(licenseName)
                    }
                }
            }

            val binaryHash = mapHash(pkg.binaryArtifact.hash)
            val sourceHash = mapHash(pkg.sourceArtifact.hash)

            val (hash, purlQualifier) = if (binaryHash == null && sourceHash != null) {
                Pair(sourceHash, "?classifier=sources")
            } else {
                Pair(binaryHash, "")
            }

            val component = Component().apply {
                group = pkg.id.namespace
                name = pkg.id.name
                version = pkg.id.version
                description = pkg.description

                // TODO: Map package-manager-specific OPTIONAL scopes.
                scope = if (ortResult.isPackageExcluded(pkg.id)) Component.Scope.EXCLUDED else Component.Scope.REQUIRED

                hashes = listOfNotNull(hash)

                // TODO: Support license expressions once we have fully converted to them.
                licenseChoice = LicenseChoice().apply { licenses = licenseObjects }

                purl = pkg.purl + purlQualifier

                // See https://github.com/CycloneDX/specification/issues/17 for how this differs from FRAMEWORK.
                type = Component.Type.LIBRARY
            }

            bom.addComponent(component)
        }

        val bomGenerator = BomGeneratorFactory.create(CycloneDxSchema.Version.VERSION_11, bom)
        bomGenerator.generate()

        outputStream.bufferedWriter().use {
            it.write(bomGenerator.toXmlString())
        }
    }
}
