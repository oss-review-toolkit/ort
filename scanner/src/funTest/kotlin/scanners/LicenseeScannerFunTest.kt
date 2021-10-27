/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.scanner.scanners

import org.ossreviewtoolkit.utils.spdx.toSpdx
import org.ossreviewtoolkit.utils.test.ExpensiveTag

class LicenseeScannerFunTest : AbstractScannerFunTest(setOf(ExpensiveTag)) {
    override val scanner = Licensee("Licensee", scannerConfig, downloaderConfig)
    override val expectedFileLicenses = setOf("Apache-2.0".toSpdx())
    override val expectedDirectoryLicenses = setOf("Apache-2.0".toSpdx())
}
