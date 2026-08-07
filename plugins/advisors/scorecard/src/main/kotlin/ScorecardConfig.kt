/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.advisors.scorecard

import org.ossreviewtoolkit.clients.scorecard.SCORECARD_BASE_URL
import org.ossreviewtoolkit.plugins.api.OrtPluginOption

/**
 * The configuration for the SCORECARD project health provider.
 */

data class ScorecardConfig(
    /** The URL of the SCORECARD server. */
    @OrtPluginOption(defaultValue = SCORECARD_BASE_URL)
    val serverUrl: String
)
