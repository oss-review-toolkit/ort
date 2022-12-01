/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.config.LicenseFindingCuration
import org.ossreviewtoolkit.model.config.PathExclude

/**
 * A resolved text location.
 */
data class ResolvedLicenseLocation(
    /**
     * The provenance of the file.
     */
    val provenance: Provenance,

    /**
     * The text location relative to the root of the VCS or source artifact defined by [provenance].
     */
    val location: TextLocation,

    /**
     * The applied [LicenseFindingCuration], or null if none were applied.
     */
    val appliedCuration: LicenseFindingCuration?,

    /**
     * All [PathExclude]s matching this [location].
     */
    val matchingPathExcludes: List<PathExclude>,

    /**
     * All copyright findings associated to this license location, excluding copyright garbage.
     */
    val copyrights: Set<ResolvedCopyrightFinding>
)
