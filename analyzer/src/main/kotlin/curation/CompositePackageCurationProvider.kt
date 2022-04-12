/*
 * Copyright (C) 2022 Bosch.IO GmbH
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
 * A curation provider that does not provide any curations on its own, but composes the given list of [providers] to a
 * single curation provider. All matching curations of all providers are provided in order of declaration.
 */
class CompositePackageCurationProvider(private val providers: List<PackageCurationProvider>) : PackageCurationProvider {
    override fun getCurationsFor(pkgIds: Collection<Identifier>): Map<Identifier, List<PackageCuration>> {
        val allCurations = mutableMapOf<Identifier, MutableList<PackageCuration>>()

        providers.forEach { provider ->
            provider.getCurationsFor(pkgIds).forEach { (pkgId, curations) ->
                allCurations.getOrPut(pkgId) { mutableListOf() } += curations
            }
        }

        return allCurations
    }
}
