/*
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

package org.ossreviewtoolkit.analyzer.curation

import org.ossreviewtoolkit.analyzer.PackageCurationProvider
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.PackageCuration

/**
 * A curation provider that does not provide any curations on its own, but searches the given list of [providers] for
 * the first matching curation, and falls back to the next provider in the list if there is no match.
 */
class FallbackPackageCurationProvider(private val providers: List<PackageCurationProvider>) : PackageCurationProvider {
    override fun getCurationsFor(pkgIds: Collection<Identifier>): Map<Identifier, List<PackageCuration>> {
        val curations = mutableMapOf<Identifier, List<PackageCuration>>()
        val remainingPkgIds = pkgIds.toMutableSet()

        for (provider in providers) {
            if (remainingPkgIds.isEmpty()) break
            curations += provider.getCurationsFor(remainingPkgIds)
            remainingPkgIds -= curations.keys
        }

        return curations
    }
}
