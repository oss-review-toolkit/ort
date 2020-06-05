/*
 * Copyright (C) 2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.reporter.utils

import java.time.Instant
import java.util.UUID

import org.ossreviewtoolkit.model.Environment
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.spdx.SpdxLicense
import org.ossreviewtoolkit.spdx.model.SpdxCreationInfo
import org.ossreviewtoolkit.spdx.model.SpdxDocument
import org.ossreviewtoolkit.utils.ORT_FULL_NAME

/**
 * A class for mapping [OrtResult]s to [SpdxDocument]s.
 */
object SpdxDocumentModelMapper {
    fun map(@Suppress("UNUSED_PARAMETER") ortResult: OrtResult, params: SpdxDocumentParams): SpdxDocument {
        val documentUuid = UUID.randomUUID()

        return SpdxDocument(
            spdxId = "SPDXRef-$documentUuid",
            creationInfo = SpdxCreationInfo(
                created = Instant.now(),
                comment = params.creationInfoComment,
                creators = listOf("Tool: $ORT_FULL_NAME - ${Environment().ortVersion}"),
                licenseListVersion = SpdxLicense.LICENSE_LIST_VERSION.substringBefore("-")
            ),
            documentNamespace = "spdx://$documentUuid",
            name = params.documentName,
            comment = params.documentComment
        )
    }

    data class SpdxDocumentParams(
        val documentName: String,
        val documentComment: String,
        val creationInfoComment: String
    )
}
