/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

package org.ossreviewtoolkit.scanner.storages

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Result
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanResultContainer
import org.ossreviewtoolkit.model.Success
import org.ossreviewtoolkit.scanner.ScanResultsStorage
import org.ossreviewtoolkit.scanner.ScannerCriteria

/**
 * A dummy storage that does not store scan results at all. Can be used to disable storing of scan results to always
 * trigger new scans. All functions in this implementation always return a successful [Result] to not trigger any
 * errors.
 */
class NoStorage : ScanResultsStorage() {
    override fun readInternal(id: Identifier) = Success(ScanResultContainer(id, emptyList()))

    override fun readInternal(pkg: Package, scannerCriteria: ScannerCriteria) =
        Success(ScanResultContainer(pkg.id, emptyList()))

    override fun addInternal(id: Identifier, scanResult: ScanResult) = Success(Unit)
}
