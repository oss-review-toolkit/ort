/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.scanner

import org.ossreviewtoolkit.model.ScannerDetails

/**
 * A holder class for the [ScannerMatcher] criteria. Only non-null properties are taken into account for matching.
 */
interface ScannerMatcherCriteria {
    /**
     * Criterion to match the scanner name. This string is interpreted as a regular expression. In the most basic
     * form, it can be an exact scanner name, but by using features of regular expressions, a more advanced
     * matching can be achieved. So it is possible, for instance, to select multiple scanners using an alternative ('|')
     * expression or an arbitrary one using a wildcard ('.*').
     */
    val regScannerName: String?

    /**
     * Criterion to match the scanner version, including this minimum version. Results are accepted if they are produced
     * by scanners with a version greater than or equal to this version.
     */
    val minVersion: String?

    /**
     * Criterion to match the scanner version, excluding this maximum version. Results are accepted if they are produced
     * by scanners with a version less than this version.
     */
    val maxVersion: String?

    /**
     * Criterion to match the [configuration][ScannerDetails.configuration] of the scanner.
     */
    val configuration: String?
}
