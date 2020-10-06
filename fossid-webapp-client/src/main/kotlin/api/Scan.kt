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

package org.ossreviewtoolkit.fossid.api

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
class Scan {
    var id: String? = null
    var created: String? = null
    var updated: String? = null

    @JsonProperty("user_id")
    var userId: Int? = null

    @JsonProperty("project_id")
    var projectId: Int? = null

    var name: String? = null
    var code: String? = null

    var description: String? = null
    var comment: String? = null

    @JsonProperty("is_archived")
    var isArchived: Boolean? = null

    @JsonProperty("target_path")
    var targetPath: Boolean? = null

    @JsonProperty("is_blind_audit")
    var isBlindAudit: Boolean? = null

    @JsonProperty("files_not_scanned")
    var filesNotScanned: String? = null

    @JsonProperty("pending_items")
    var pendingItems: String? = null

    @JsonProperty("is_from_report")
    var isFromReport: String? = null

    @JsonProperty("git_repo_url")
    var gitRepoUrl: String? = null

    @JsonProperty("git_branch")
    var gitBranch: String? = null

    @JsonProperty("imported_metadata")
    var importedMetadata: String? = null

    @JsonProperty("has_file_extension")
    var hasFileExtension: Int? = null

    @JsonProperty("jar_extraction")
    var jarExtraction: String? = null

    @JsonProperty("any_archives_expanded")
    var anyArchivesExpanded: String? = null

    @JsonProperty("uploaded_files")
    var uploadedFiles: String? = null
}
