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

package org.ossreviewtoolkit.clients.fossid

import org.ossreviewtoolkit.clients.fossid.model.status.UnversionedScanDescription

class VersionedFossIdService2021dot2(
    val delegate: FossIdRestService, version: String
) : FossIdRestService by delegate, FossIdServiceWithVersion(version) {
    override suspend fun checkScanStatus(
        user: String,
        apiKey: String,
        scanCode: String
    ): EntityResponseBody<out UnversionedScanDescription> =
        delegate.checkScanStatus2021dot2(
            PostRequestBody("check_status", SCAN_GROUP, user, apiKey, "scan_code" to scanCode)
        )
}
