/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.config.ScannerConfiguration

/**
 * The class to run license / copyright remote scanners.
 * Remote scanners are scanners not running directly on the system running ORT.
 */
abstract class RemoteScanner(name: String, config: ScannerConfiguration) : Scanner(name, config) {
    fun getDetails() = ScannerDetails(scannerName, getVersion(), getConfiguration())

    abstract fun getConfiguration(): String

    abstract fun getVersion(): String
}
