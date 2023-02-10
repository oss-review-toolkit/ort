/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.model.utils

import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageCuration

/**
 * A provider for [PackageCuration]s.
 */
fun interface PackageCurationProvider {
    /**
     * Return all available [PackageCuration]s which are applicable to any of the given [packages].
     */
    // TODO: Maybe make this a suspend function, then all implementing classes could deal with coroutines more easily.
    fun getCurationsFor(packages: Collection<Package>): Set<PackageCuration>
}
