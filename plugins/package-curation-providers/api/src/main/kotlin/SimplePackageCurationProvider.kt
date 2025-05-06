/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagecurationproviders.api

import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.plugins.api.PluginDescriptor

/**
 * The default [PluginDescriptor] for a [SimplePackageCurationProvider]. Classes inheriting from this class
 * have to provide their own descriptor.
 */
private val pluginDescriptor = PluginDescriptor(
    id = "Simple",
    displayName = "Simple",
    description = "A simple package curation provider, which provides a fixed set of package curations."
)

/**
 * A [PackageCurationProvider] that provides the specified [packageCurations].
 */
open class SimplePackageCurationProvider(
    override val descriptor: PluginDescriptor,
    val packageCurations: List<PackageCuration>
) : PackageCurationProvider {
    constructor(packageCurations: List<PackageCuration>) : this(pluginDescriptor, packageCurations)

    override fun getCurationsFor(packages: Collection<Package>): Set<PackageCuration> =
        packageCurations.filterTo(mutableSetOf()) { curation ->
            packages.any { pkg -> curation.isApplicable(pkg.id) }
        }
}
