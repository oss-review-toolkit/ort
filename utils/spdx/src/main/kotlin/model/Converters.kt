/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.utils.spdx.model

import com.fasterxml.jackson.databind.util.StdConverter

import java.util.SortedSet

internal class SpdxRelationshipSortedSetConverter :
    StdConverter<List<SpdxRelationship>, SortedSet<SpdxRelationship>>() {
    override fun convert(value: List<SpdxRelationship>) =
        value.toSortedSet(
            compareBy<SpdxRelationship> { it.spdxElementId }
                .thenBy { it.relatedSpdxElement }
                .thenBy { it.relationshipType }
        )
}

// TODO: Add more converters for SpdxDocument to improve serialization, see
//       https://github.com/oss-review-toolkit/ort/issues/7023.
