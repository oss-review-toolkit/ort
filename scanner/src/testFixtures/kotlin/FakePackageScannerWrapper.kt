/*
 * Copyright (C) 2021 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.scanner

import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.scanner.provenance.NestedProvenance

/**
 * An implementation of [PackageScannerWrapper] that creates empty scan results.
 */
@Suppress("RedundantNullableReturnType")
class FakePackageScannerWrapper(id: String = "fake") : PackageScannerWrapper {
    override val descriptor = PluginDescriptor(id = id, displayName = id, description = "")
    override val version = "1.0.0"
    override val configuration = "config"

    // Explicit nullability is required here for a mock response.
    override val matcher: ScannerMatcher? = ScannerMatcher.create(details)
    override val readFromStorage = true
    override val writeToStorage = true

    override fun scanPackage(nestedProvenance: NestedProvenance?, context: ScanContext): ScanResult =
        createScanResult(nestedProvenance?.root ?: UnknownProvenance, details)
}
