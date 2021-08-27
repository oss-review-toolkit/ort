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

import kotlinx.coroutines.runBlocking

import org.ossreviewtoolkit.clients.fossid.model.status.UnversionedScanDescription

abstract class FossIdServiceWithVersion(val version: String) : FossIdRestService {
    companion object {
        /**
         * Construct a new instance of [FossIdServiceWithVersion] for given [delegate]. The implementation matching
         * FossID version will be instantiated and returned.
         */
        fun instance(delegate: FossIdRestService): FossIdServiceWithVersion = runBlocking {
            val version = delegate.getFossIdVersion().orEmpty()

            when {
                version >= "2021.2" -> VersionedFossIdService2021dot2(delegate, version)
                else -> VersionedFossIdService(delegate, version)
            }
        }
    }

    /**
     * Get the scan status for the given [scanCode].
     *
     * The HTTP request is sent with [user] and [apiKey] as credentials.
     */
    abstract suspend fun checkScanStatus(
        user: String,
        apiKey: String,
        scanCode: String
    ): EntityResponseBody<out UnversionedScanDescription>
}
