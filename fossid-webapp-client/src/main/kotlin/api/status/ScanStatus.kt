/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

package org.ossreviewtoolkit.fossid.api.status

import com.fasterxml.jackson.annotation.JsonProperty

class ScanStatus {
    @JsonProperty("scan_id")
    var scanId: String? = null

    @JsonProperty("scan_name")
    var scanName: String? = null

    @JsonProperty("scan_code")
    var scanCode: String? = null

    var pid: String? = null
    var type: ScanStatusType? = null
    @JsonProperty("status")
    var state: ScanState? = null

    @JsonProperty("is_finished")
    var isFinished: Int? = null

    @JsonProperty("percentage_done")
    var percentageDone: String? = null

    var comment: String? = null

    @JsonProperty("comment_2")
    var comment2: String? = null

    @JsonProperty("comment_3")
    var comment3: String? = null

    var started: String? = null
    var finished: String? = null
}
