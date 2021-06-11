/*
 * Copyright (C) 2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.model.config

/**
 * Possible reasons for a license finding curation.
 */
enum class LicenseFindingCurationReason {
    /**
     * The findings occur in source code, for example the name of a variable.
     */
    CODE,

    /**
     * The findings occur in a data, for example a JSON object defining all SPDX licenses.
     */
    DATA_OF,

    /**
     * The findings occur in documentation, for example in code comments or in the README.md.
     */
    DOCUMENTATION_OF,

    /**
     * The detected licenses are not correct. Use only if none of the other reasons apply.
     */
    INCORRECT,

    /**
     * Add applicable license as the scanner did not detect it.
     */
    NOT_DETECTED,

    /**
     * The findings reference a file or URL, e.g. SEE LICENSE IN LICENSE or https://jquery.org/license/.
     */
    REFERENCE
}
