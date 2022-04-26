/*
 * Copyright (C) 2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.utils.spdx

object SpdxConstants {
    /**
     * Represents a not present value, which has been determined to actually be not present. This representation must
     * not be used if [NOASSERTION] could be used instead.
     */
    const val NONE = "NONE"

    /**
     * Represents a not present value where any of the following cases applies:
     *
     * 1. no attempt was made to determine the information.
     * 2. intentionally no information is provided, whereas no meaning should be derived from the absence of the
     *    information.
     */
    const val NOASSERTION = "NOASSERTION"

    /**
     * The tag to use in a line of source code to declare an SPDX ID.
     *
     * Note: The tag does not include the (actually required) trailing space after the colon to work around
     * https://github.com/fsfe/reuse-tool/issues/463.
     */
    const val TAG = "SPDX-License-Identifier:"

    /**
     * A prefix used in fields like "originator", "supplier", or "annotator" to describe a person.
     */
    const val PERSON = "Person: "

    /**
     * A prefix used in fields like "originator", "supplier", or "annotator" to describe an organization.
     */
    const val ORGANIZATION = "Organization: "

    /**
     * A prefix used in fields like "annotator" to describe a tool.
     */
    const val TOOL = "Tool: "

    /**
     * The prefix to be used for SPDX document IDs or references.
     */
    const val REF_PREFIX = "SPDXRef-"

    /**
     * The prefix to be used for references to other SPDX documents.
     */
    const val DOCUMENT_REF_PREFIX = "DocumentRef-"

    /**
     * The prefix to be used for references to licenses that are not part of the SPDX license list.
     */
    const val LICENSE_REF_PREFIX = "LicenseRef-"

    /**
     * The URL that points to list of SPDX licenses.
     */
    const val LICENSE_LIST_URL = "https://spdx.org/licenses/"

    /**
     * The package verification code for no input.
     */
    const val EMPTY_PACKAGE_VERIFICATION_CODE = "da39a3ee5e6b4b0d3255bfef95601890afd80709"

    private val NOT_PRESENT_VALUES = setOf(null, NONE, NOASSERTION)

    /**
     * Return true if and only if the given value is null or equals [NONE] or [NOASSERTION].
     */
    fun isNotPresent(value: String?) = value in NOT_PRESENT_VALUES

    /**
     * Return true if and only if the given value is not null and does not equal [NONE] or [NOASSERTION].
     */
    fun isPresent(value: String?) = !isNotPresent(value)
}
