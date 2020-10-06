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

package org.ossreviewtoolkit.fossid.api.result

import com.fasterxml.jackson.annotation.JsonProperty

class FossIdScanResult {
    var id: Int? = null

    @JsonProperty("local_path")
    var localPath: String? = null
    var created: String? = null

    @JsonProperty("scan_id")
    var scanId: Int? = null

    @JsonProperty("scan_file_id")
    var scanFileId: Int? = null

    @JsonProperty("file_id")
    var fileId: Int? = null

    @JsonProperty("match_type")
    var matchType: MatchType? = null
    var author: String? = null
    var artifact: String? = null
    var version: String? = null

    @JsonProperty("artifact_license")
    var artifactLicense: String? = null
    var mirror: String? = null
    var file: String? = null

    @JsonProperty("file_license")
    var fileLicense: String? = null

    @JsonProperty("underlying_licenses")
    var underlyingLicenses: String? = null
    var url: String? = null
    var hits: String? = null
    var size: Int? = null
    var updated: String? = null
}
