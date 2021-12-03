/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.scanner.experimental

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.PackageType

/**
 * Additional context information that can be used by a [ScannerWrapper] to alter its behavior.
 */
data class ScanContext(
    /**
     * A map of key-value pairs, usually from [OrtResult.labels].
     */
    val labels: Map<String, String>,

    /**
     * The [type][PackageType] of the packages to scan.
     */
    val packageType: PackageType
)
