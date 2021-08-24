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

package org.ossreviewtoolkit.model.utils

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.config.PackageConfiguration

/**
 * A provider for [PackageConfiguration]s.
 */
interface PackageConfigurationProvider {
    /**
     * Return all [PackageConfiguration]s of this provider.
     */
    fun getPackageConfigurations(): List<PackageConfiguration>

    /**
     * Return the first matching [PackageConfiguration] for the given [packageId] and [provenance] if any.
     */
    fun getPackageConfiguration(packageId: Identifier, provenance: Provenance): PackageConfiguration?
}
