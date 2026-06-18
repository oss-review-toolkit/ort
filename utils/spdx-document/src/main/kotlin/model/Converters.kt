/*
 * Copyright (C) 2023 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

@file:Suppress("Filename", "MatchingDeclarationName")

package org.ossreviewtoolkit.utils.spdxdocument.model

import com.fasterxml.jackson.databind.util.StdConverter

internal class SortedSpdxAnnotationConverter :
    StdConverter<Collection<SpdxAnnotation>, List<SpdxAnnotation>>() {
    override fun convert(value: Collection<SpdxAnnotation>) =
        value.sortedWith(
            compareBy<SpdxAnnotation> { it.annotationDate }
                .thenBy { it.annotator }
                .thenBy { it.annotationType }
        )
}

internal class SortedSpdxDocumentDescribesConverter :
    StdConverter<Collection<String>, List<String>>() {
    override fun convert(value: Collection<String>) = value.sorted()
}

internal class SortedSpdxExternalDocumentRefConverter :
    StdConverter<Collection<SpdxExternalDocumentReference>, List<SpdxExternalDocumentReference>>() {
    override fun convert(value: Collection<SpdxExternalDocumentReference>) = value.sortedBy { it.externalDocumentId }
}

internal class SortedSpdxExtractedLicenseInfoConverter :
    StdConverter<Collection<SpdxExtractedLicenseInfo>, List<SpdxExtractedLicenseInfo>>() {
    override fun convert(value: Collection<SpdxExtractedLicenseInfo>) = value.sortedBy { it.licenseId }
}

internal class SortedSpdxFileConverter :
    StdConverter<Collection<SpdxFile>, List<SpdxFile>>() {
    override fun convert(value: Collection<SpdxFile>) = value.sortedBy { it.spdxId }
}

internal class SortedSpdxPackageConverter :
    StdConverter<Collection<SpdxPackage>, List<SpdxPackage>>() {
    override fun convert(value: Collection<SpdxPackage>) = value.sortedBy { it.spdxId }
}

internal class SortedSpdxRelationshipConverter :
    StdConverter<Collection<SpdxRelationship>, List<SpdxRelationship>>() {
    override fun convert(value: Collection<SpdxRelationship>) =
        value.sortedWith(
            compareBy<SpdxRelationship> { it.spdxElementId }
                .thenBy { it.relatedSpdxElement }
                .thenBy { it.relationshipType }
        )
}

internal class SortedSpdxSnippetConverter :
    StdConverter<Collection<SpdxSnippet>, List<SpdxSnippet>>() {
    override fun convert(value: Collection<SpdxSnippet>) = value.sortedBy { it.spdxId }
}
