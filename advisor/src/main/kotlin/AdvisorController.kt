/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.advisor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AdvisorController(val providers: List<String>) {
    private val _status: MutableStateFlow<AdvisorStatus> = MutableStateFlow(AdvisorStatus(emptyMap()))
    val status: StateFlow<AdvisorStatus> = _status

    fun updateStatus(provider: String, totalPackages: Int, completedPackages: Int) {
        val providerProgress = ProviderProgress(totalPackages, completedPackages)
        val newStatus = _status.value.copy(providerProgress = _status.value.providerProgress + (provider to providerProgress))
        _status.value = newStatus
    }
}

data class AdvisorStatus(
    val providerProgress: Map<String, ProviderProgress>
)

data class ProviderProgress(
    val totalPackages: Int,
    val completedPackages: Int
)
