/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.model.utils

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.config.PackageConfiguration

/**
 * A provider for [PackageConfiguration]s.
 */
fun interface PackageConfigurationProvider {
    companion object {
        /**
         * A provider that does not provide any curations.
         */
        @JvmField
        val EMPTY = PackageConfigurationProvider { _, _ -> emptyList() }
    }

    /**
     * Return a list of [PackageConfiguration]s for the given [packageId] and [provenance].
     */
    fun getPackageConfigurations(packageId: Identifier, provenance: Provenance): List<PackageConfiguration>
}
