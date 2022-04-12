/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.model

/**
 * A class representing a single copyright finding.
 */
data class CopyrightFinding(
    /**
     * The copyright statement.
     */
    val statement: String,

    /**
     * The text location where the copyright statement was found.
     */
    val location: TextLocation
) : Comparable<CopyrightFinding> {
    companion object {
        private val COMPARATOR = compareBy<CopyrightFinding>({ it.statement }, { it.location })
    }

    override fun compareTo(other: CopyrightFinding) = COMPARATOR.compare(this, other)
}
