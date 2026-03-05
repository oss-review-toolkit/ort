/*
 * Copyright (C) 2020 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.spdx.utils

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.utils.common.collapseWhitespace
import org.ossreviewtoolkit.utils.common.withoutPrefix
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdxdocument.model.SpdxExternalDocumentReference

/**
 * Return whether the string has the format of an [SpdxExternalDocumentReference], with or without an additional
 * package id.
 */
internal fun String.isExternalDocumentReferenceId(): Boolean = startsWith(SpdxConstants.DOCUMENT_REF_PREFIX)

/**
 * Map a "not preset" SPDX value, i.e. NONE or NOASSERTION, to an empty string.
 */
internal fun String.mapNotPresentToEmpty(): String = takeUnless { SpdxConstants.isNotPresent(it) }.orEmpty()

/**
 * Sanitize a string for use as an [Identifier] property where colons are not supported by replacing them with spaces,
 * trimming, and finally collapsing multiple consecutive spaces.
 */
internal fun String.sanitize(): String = replace(':', ' ').collapseWhitespace()

/**
 * Wrap any "present" SPDX value in a sorted set, or return an empty sorted set otherwise.
 */
internal fun String?.wrapPresentInSet(): Set<String> {
    if (SpdxConstants.isPresent(this)) {
        withoutPrefix(SpdxConstants.PERSON)?.let { persons ->
            // In case of a person, allow a comma-separated list of persons.
            return persons.split(',').mapTo(mutableSetOf()) { it.trim() }
        }

        // Do not split an organization like "Acme, Inc." by comma.
        withoutPrefix(SpdxConstants.ORGANIZATION)?.let {
            return setOf(it.trim())
        }
    }

    return emptySet()
}
