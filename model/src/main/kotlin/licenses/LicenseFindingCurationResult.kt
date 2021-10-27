/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.model.licenses

import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.config.LicenseFindingCuration
import org.ossreviewtoolkit.utils.spdx.SpdxConstants

/**
 * A [curated license finding][curatedFinding] created by applying [LicenseFindingCuration]s.
 */
data class LicenseFindingCurationResult(
    /**
     * The curated license finding, or null, if the [concluded license][LicenseFindingCuration.concludedLicense] is
     * [SpdxConstants.NONE].
     */
    val curatedFinding: LicenseFinding?,

    /**
     * All pairs of original license findings and applied curation that were resolved to the [curatedFinding].
     */
    val originalFindings: List<Pair<LicenseFinding, LicenseFindingCuration>>
)
