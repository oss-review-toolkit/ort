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

package org.ossreviewtoolkit.plugins.reporters.spdx

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.plugins.licensefactproviders.scancode.ScanCodeLicenseFactProviderFactory
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.spdxdocument.SpdxModelMapper.FileFormat
import org.ossreviewtoolkit.utils.spdxdocument.model.SpdxDocument
import org.ossreviewtoolkit.utils.spdxdocument.model.SpdxRelationship

class SpdxDocumentModelMapperFunTest : WordSpec({
    "Mapping to SPDX-2.3" should {
        "not create dependency relationships for a project without dependencies" {
            val document = mapToSpdxDocument(ORT_RESULT_WITHOUT_DEPENDENCIES)

            document.documentDescribes should containExactly(
                PROJECT_WITHOUT_DEPENDENCIES_ID.toSpdxId(SpdxPackageType.PROJECT)
            )
            document.relationships should beEmpty()
        }

        "not create dependency relationships from excluded scopes" {
            val document = mapToSpdxDocument(ORT_RESULT_WITH_DEPENDENCY_IN_INCLUDED_AND_EXCLUDED_SCOPES)

            document.relationships should containExactlyInAnyOrder(
                SpdxRelationship(
                    EXCLUDED_SCOPE_PROJECT_ID.toSpdxId(SpdxPackageType.PROJECT),
                    SpdxRelationship.Type.DEPENDS_ON,
                    EXCLUDED_SCOPE_ROOT_PACKAGE_ID.toSpdxId()
                ),
                SpdxRelationship(
                    EXCLUDED_SCOPE_PROJECT_ID.toSpdxId(SpdxPackageType.PROJECT),
                    SpdxRelationship.Type.DYNAMIC_LINK,
                    EXCLUDED_SCOPE_ROOT_PACKAGE_ID.toSpdxId()
                ),
                SpdxRelationship(
                    EXCLUDED_SCOPE_ROOT_PACKAGE_ID.toSpdxId(),
                    SpdxRelationship.Type.DEPENDS_ON,
                    EXCLUDED_SCOPE_TRANSITIVE_PACKAGE_ID.toSpdxId()
                ),
                SpdxRelationship(
                    EXCLUDED_SCOPE_ROOT_PACKAGE_ID.toSpdxId(),
                    SpdxRelationship.Type.DYNAMIC_LINK,
                    EXCLUDED_SCOPE_TRANSITIVE_PACKAGE_ID.toSpdxId()
                )
            )
        }

        "not hang on a cyclic dependency graph" {
            val document = mapToSpdxDocument(ORT_RESULT_WITH_CYCLIC_DEPENDENCY_GRAPH)

            document.relationships should containExactlyInAnyOrder(
                SpdxRelationship(
                    CYCLIC_GRAPH_PROJECT_ID.toSpdxId(SpdxPackageType.PROJECT),
                    SpdxRelationship.Type.DEPENDS_ON,
                    CYCLIC_GRAPH_PACKAGE_A_ID.toSpdxId()
                ),
                SpdxRelationship(
                    CYCLIC_GRAPH_PROJECT_ID.toSpdxId(SpdxPackageType.PROJECT),
                    SpdxRelationship.Type.DYNAMIC_LINK,
                    CYCLIC_GRAPH_PACKAGE_A_ID.toSpdxId()
                ),
                SpdxRelationship(
                    CYCLIC_GRAPH_PACKAGE_A_ID.toSpdxId(),
                    SpdxRelationship.Type.DEPENDS_ON,
                    CYCLIC_GRAPH_PACKAGE_B_ID.toSpdxId()
                ),
                SpdxRelationship(
                    CYCLIC_GRAPH_PACKAGE_A_ID.toSpdxId(),
                    SpdxRelationship.Type.DYNAMIC_LINK,
                    CYCLIC_GRAPH_PACKAGE_B_ID.toSpdxId()
                ),
                SpdxRelationship(
                    CYCLIC_GRAPH_PACKAGE_B_ID.toSpdxId(),
                    SpdxRelationship.Type.DEPENDS_ON,
                    CYCLIC_GRAPH_PACKAGE_A_ID.toSpdxId()
                ),
                SpdxRelationship(
                    CYCLIC_GRAPH_PACKAGE_B_ID.toSpdxId(),
                    SpdxRelationship.Type.DYNAMIC_LINK,
                    CYCLIC_GRAPH_PACKAGE_A_ID.toSpdxId()
                )
            )
        }
    }
})

private fun mapToSpdxDocument(ortResult: OrtResult): SpdxDocument {
    val input = ReporterInput(ortResult, licenseFactProvider = ScanCodeLicenseFactProviderFactory.create())

    val config = SpdxDocumentReporterConfig(
        spdxVersion = SpdxVersion.SPDX_2_3,
        creationInfoComment = "some creation info comment",
        creationInfoPerson = "some creation info person",
        creationInfoOrganization = "some creation info organization",
        documentComment = "some document comment",
        documentName = "some document name",
        fileInformationEnabled = false,
        outputFileFormats = listOf(FileFormat.JSON)
    )

    return SpdxDocumentModelMapper.map(
        input.ortResult,
        input.licenseInfoResolver,
        input.licenseFactProvider,
        config
    )
}
