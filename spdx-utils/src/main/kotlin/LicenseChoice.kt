/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.spdx

/**
 * This class represents a license choice for [SpdxExpression]s that contain the [OR operator][SpdxOperator.OR].
 *
 * The [choice] property contains the chosen license. The [alternatives] list contains a list of alternative licenses
 * that need to be concatenated with [choice] via [OR][SpdxOperator.OR] for the choice to apply. This is useful to make
 * sure that a license choice is not automatically applied if the alternative licenses of a package change, or when
 * making global license choices.
 *
 * Examples:
 *
 * | SPDX Expression                 | Choice                 | Alternatives   | Outcome                |
 * | ------------------------------- | ---------------------- | -------------- | ---------------------- |
 * | Apache-2.0                      | Apache-2.0             | ()             | Apache-2.0             |
 * | Apache-2.0                      | Apache-2.0             | (MIT)          | <ERROR>                |
 * | Apache-2.0                      | MIT                    | ()             | <ERROR>                |
 * | Apache-2.0 OR MIT               | Apache-2.0             | ()             | Apache-2.0             |
 * | Apache-2.0 OR MIT               | Apache-2.0             | (MIT)          | Apache-2.0             |
 * | Apache-2.0 OR MIT               | Apache-2.0             | (BSD)          | <ERROR>                |
 * | Apache-2.0 OR GPL-1.0 OR MIT    | Apache-2.0             | ()             | Apache-2.0             |
 * | Apache-2.0 OR GPL-1.0 OR MIT    | Apache-2.0             | (GPL-1.0)      | <ERROR>                |
 * | Apache-2.0 OR GPL-1.0 OR MIT    | Apache-2.0             | (GPL-1.0, MIT) | Apache-2.0             |
 * | (Apache-2.0 OR MIT) AND CC0-1.0 | Apache-2.0             | ()             | Apache-2.0 AND CC0-1.0 |
 * | (Apache-2.0 OR MIT) AND CC0-1.0 | Apache-2.0             | (CC0-1.0)      | <ERROR>                |
 * | (Apache-2.0 AND CC0-1.0) OR MIT | Apache-2.0 AND CC0-1.0 | ()             | Apache-2.0 AND CC0-1.0 |
 */
class LicenseChoice(
    /**
     * The chosen license.
     */
    val choice: SpdxExpression,

    /**
     * A list of alternative licenses. The choice is only applied if all alternatives are matched.
     */
    val alternatives: List<SpdxExpression>
)
